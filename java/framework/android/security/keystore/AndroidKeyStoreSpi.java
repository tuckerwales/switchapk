package android.security.keystore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import javax.crypto.SecretKey;

/**
 * KeyStore.getInstance("AndroidKeyStore"): the app's own keys, persisted
 * across runs. Secret keys (AES, HMAC) are supported; key pairs and trusted
 * certificates are not yet.
 */
public class AndroidKeyStoreSpi extends KeyStoreSpi {
    public AndroidKeyStoreSpi() {
    }

    public Key engineGetKey(String alias, char[] password) {
        if (alias == null) throw new NullPointerException("alias == null");
        KeyStoreEntry e = KeyStoreEntry.load(alias);
        return e == null ? null : new AndroidKeyStoreSecretKey(e);
    }

    public Certificate[] engineGetCertificateChain(String alias) {
        if (alias == null) throw new NullPointerException("alias == null");
        return null;
    }

    public Certificate engineGetCertificate(String alias) {
        if (alias == null) throw new NullPointerException("alias == null");
        return null;
    }

    public Date engineGetCreationDate(String alias) {
        if (alias == null) throw new NullPointerException("alias == null");
        KeyStoreEntry e = KeyStoreEntry.load(alias);
        return e == null ? null : new Date(e.created);
    }

    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        if (password != null && password.length > 0) throw new KeyStoreException("entries cannot be protected with passwords");
        if (key instanceof SecretKey) {
            throw new KeyStoreException("Use KeyStore.setEntry with KeyProtection to import secret keys");
        }
        throw new KeyStoreException("Unsupported key type: " + (key == null ? null : key.getClass().getName()));
    }

    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new KeyStoreException("Operation not supported because key encoding is unknown");
    }

    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        throw new KeyStoreException("Trusted certificate entries are not supported yet");
    }

    public void engineDeleteEntry(String alias) throws KeyStoreException {
        if (!KeyStoreEntry.delete(alias)) throw new KeyStoreException("Failed to delete entry: " + alias);
    }

    public Enumeration<String> engineAliases() {
        return Collections.enumeration(KeyStoreEntry.aliases());
    }

    public boolean engineContainsAlias(String alias) {
        if (alias == null) throw new NullPointerException("alias == null");
        return KeyStoreEntry.exists(alias);
    }

    public int engineSize() {
        return KeyStoreEntry.aliases().size();
    }

    public boolean engineIsKeyEntry(String alias) {
        return engineContainsAlias(alias);
    }

    public boolean engineIsCertificateEntry(String alias) {
        if (alias == null) throw new NullPointerException("alias == null");
        return false;
    }

    public String engineGetCertificateAlias(Certificate cert) {
        return null;
    }

    public void engineStore(OutputStream stream, char[] password) {
        throw new UnsupportedOperationException("Can not serialize AndroidKeyStore to OutputStream");
    }

    public void engineLoad(InputStream stream, char[] password) throws IOException {
        if (stream != null) throw new IllegalArgumentException("InputStream not supported");
        if (password != null) throw new IllegalArgumentException("password not supported");
    }

    public void engineLoad(KeyStore.LoadStoreParameter param) throws IOException {
        if (param != null && param.getProtectionParameter() != null) {
            throw new IllegalArgumentException("Unsupported param type: " + param.getClass());
        }
    }

    public void engineSetEntry(String alias, KeyStore.Entry entry, KeyStore.ProtectionParameter param)
            throws KeyStoreException {
        if (entry == null) throw new KeyStoreException("entry == null");
        if (!(entry instanceof KeyStore.SecretKeyEntry)) {
            throw new KeyStoreException("Entry must be a SecretKeyEntry; key pairs are not supported yet");
        }
        if (param != null && !(param instanceof KeyProtection)) {
            throw new KeyStoreException("Unsupported protection parameter class: " + param.getClass().getName()
                    + ". Supported: " + KeyProtection.class.getName());
        }
        SecretKey key = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        if (key instanceof AndroidKeyStoreSecretKey) {
            if (param != null) throw new KeyStoreException("Modifying KeyStore-backed key using protection parameters not supported");
            KeyStoreEntry src = ((AndroidKeyStoreSecretKey) key).entry();
            if (src.alias.equals(alias)) return;
            KeyStoreEntry copy = KeyStoreEntry.load(src.alias);
            if (copy == null) throw new KeyStoreException("Source key no longer exists: " + src.alias);
            copy.alias = alias;
            save(copy);
            return;
        }
        if (param == null) throw new KeyStoreException("Protection parameters must be specified when importing a symmetric key");
        if (!"RAW".equalsIgnoreCase(key.getFormat())) {
            throw new KeyStoreException("Unsupported secret key material export format: " + key.getFormat());
        }
        byte[] raw = key.getEncoded();
        if (raw == null) throw new KeyStoreException("Key did not export its key material despite supporting RAW format export");
        String algorithm = canonicalAlgorithm(key.getAlgorithm());
        if (algorithm == null) throw new KeyStoreException("Unsupported secret key algorithm: " + key.getAlgorithm());
        KeyProtection p = (KeyProtection) param;
        KeyStoreEntry e = new KeyStoreEntry();
        e.alias = alias;
        e.algorithm = algorithm;
        e.keySize = raw.length * 8;
        e.origin = KeyProperties.ORIGIN_IMPORTED;
        e.created = System.currentTimeMillis();
        e.purposes = p.getPurposes();
        e.blockModes = p.getBlockModes();
        e.encryptionPaddings = p.getEncryptionPaddings();
        e.signaturePaddings = p.getSignaturePaddings();
        e.digests = p.isDigestsSpecified() ? p.getDigests() : new String[0];
        e.randomizedEncryptionRequired = p.isRandomizedEncryptionRequired();
        e.userAuthenticationRequired = p.isUserAuthenticationRequired();
        e.userAuthenticationValidityDurationSeconds = p.getUserAuthenticationValidityDurationSeconds();
        e.userAuthenticationType = p.getUserAuthenticationType();
        e.maxUsageCount = p.getMaxUsageCount();
        e.validityStart = KeyStoreEntry.time(p.getKeyValidityStart());
        e.originationEnd = KeyStoreEntry.time(p.getKeyValidityForOriginationEnd());
        e.consumptionEnd = KeyStoreEntry.time(p.getKeyValidityForConsumptionEnd());
        e.key = raw;
        if (algorithm.equals("AES") && raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new KeyStoreException("Unsupported AES key size: " + e.keySize);
        }
        save(e);
    }

    private static void save(KeyStoreEntry e) throws KeyStoreException {
        try {
            e.save();
        } catch (IOException ex) {
            throw new KeyStoreException("Failed to store " + e.alias, ex);
        }
    }

    static String canonicalAlgorithm(String algorithm) {
        String[] known = {"AES", "HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512"};
        for (String k : known) {
            if (k.equalsIgnoreCase(algorithm)) return k;
        }
        return null;
    }
}
