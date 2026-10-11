package com.example.keystore;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Collections;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AndroidKeyStore across two runs. Run 1 generates an AES-GCM key (the way
 * Seren's SecretBox does) and seals a secret into SharedPreferences; run 2
 * finds the same key and opens it. Both runs check TOTP over HMAC-SHA1
 * (RFC 6238), opaque keys, KeyInfo, purpose enforcement, import and delete.
 */
public class MainActivity extends Activity {
    private static final String TAG = "KEYSTORE";
    private static final String ALIAS = "box";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View swatch = new View(this);
        try {
            boolean second = run();
            swatch.setBackgroundColor(second ? 0xFF1E88E5 : 0xFF43A047);
        } catch (Exception e) {
            Log.e(TAG, "failed", e);
            swatch.setBackgroundColor(0xFFE53935);
        }
        setContentView(swatch);
    }

    private boolean run() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SharedPreferences prefs = getSharedPreferences("box", MODE_PRIVATE);
        boolean second = ks.containsAlias(ALIAS);
        Log.i(TAG, "has key " + second);
        SecretKey key;
        if (!second) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            key = gen.generateKey();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = c.getIV();
            byte[] sealed = c.doFinal("the secret".getBytes(StandardCharsets.UTF_8));
            byte[] blob = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            prefs.edit().putString("sealed", Base64.encodeToString(blob, Base64.NO_WRAP)).commit();
            Log.i(TAG, "sealed iv=" + iv.length + " ct=" + sealed.length);
        } else {
            key = ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
            byte[] blob = Base64.decode(prefs.getString("sealed", ""), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, blob, 0, 12));
            String opened = new String(c.doFinal(blob, 12, blob.length - 12), StandardCharsets.UTF_8);
            Log.i(TAG, "opened " + opened);
        }
        Log.i(TAG, "opaque alg=" + key.getAlgorithm() + " format=" + key.getFormat() + " encoded=" + key.getEncoded());
        KeyInfo info = (KeyInfo) SecretKeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore").getKeySpec(key, KeyInfo.class);
        Log.i(TAG, "info " + info.getKeystoreAlias() + " size=" + info.getKeySize() + " purposes=" + info.getPurposes()
                + " modes=" + String.join(",", info.getBlockModes()) + " hw=" + info.isInsideSecureHardware());

        // Wrong block mode for this key.
        try {
            Cipher cbc = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cbc.init(Cipher.ENCRYPT_MODE, key);
            Log.i(TAG, "mode not enforced");
        } catch (java.security.InvalidKeyException e) {
            Log.i(TAG, "mode enforced");
        }

        // Import a decrypt-only key, check its purpose is enforced, then delete it.
        ks.setEntry("imported", new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[16], "AES")),
                new KeyProtection.Builder(KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false)
                        .build());
        SecretKey imported = (SecretKey) ks.getKey("imported", null);
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.DECRYPT_MODE, imported);
        Log.i(TAG, "imported decrypt " + hex(ecb.doFinal(new byte[16])));
        try {
            ecb.init(Cipher.ENCRYPT_MODE, imported);
            Log.i(TAG, "purpose not enforced");
        } catch (java.security.InvalidKeyException e) {
            Log.i(TAG, "purpose enforced");
        }
        Log.i(TAG, "aliases " + Collections.list(ks.aliases()));
        ks.deleteEntry("imported");
        Log.i(TAG, "after delete " + Collections.list(ks.aliases()) + " size=" + ks.size());

        // TOTP, RFC 6238 appendix B: SHA-1, T = 59 s, 8 digits -> 94287082.
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("12345678901234567890".getBytes(StandardCharsets.US_ASCII), "RAW"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(59 / 30).array());
        int off = h[h.length - 1] & 0xf;
        int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
        Log.i(TAG, "totp " + String.format("%08d", bin % 100000000));

        // A keystore HMAC key used through Mac.
        KeyGenerator hg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
        hg.init(new KeyGenParameterSpec.Builder("mac", KeyProperties.PURPOSE_SIGN).build());
        Mac km = Mac.getInstance("HmacSHA256");
        km.init(hg.generateKey());
        Log.i(TAG, "keystore mac " + km.doFinal(new byte[4]).length);
        ks.deleteEntry("mac");
        return second;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }
}
