package android.security.keystore;

import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * One AndroidKeyStore entry and its authorizations, kept as a properties file
 * in the app's data directory (data/keystore/<hex alias>.key). The Switch has
 * no keystore an app can reach, so the key bytes are protected only by the
 * file system, like the app's other private files.
 */
final class KeyStoreEntry {
    private static final Object LOCK = new Object();
    private static File sDir;

    String alias;
    String algorithm;
    int keySize;
    int origin;
    long created;
    int purposes;
    String[] blockModes = new String[0];
    String[] encryptionPaddings = new String[0];
    String[] signaturePaddings = new String[0];
    String[] digests = new String[0];
    boolean randomizedEncryptionRequired = true;
    boolean userAuthenticationRequired;
    int userAuthenticationValidityDurationSeconds;
    int userAuthenticationType;
    int maxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;
    long validityStart = -1;
    long originationEnd = -1;
    long consumptionEnd = -1;
    byte[] key;

    static void setDirectory(File dir) {
        synchronized (LOCK) {
            sDir = dir;
        }
    }

    private static File dir() {
        synchronized (LOCK) {
            if (sDir == null) sDir = new File(System.getProperty("java.io.tmpdir", "/tmp"), "keystore");
            if (!sDir.isDirectory()) sDir.mkdirs();
            return sDir;
        }
    }

    private static File file(String alias) {
        StringBuilder sb = new StringBuilder();
        for (byte b : alias.getBytes(StandardCharsets.UTF_8)) sb.append(String.format("%02x", b & 0xff));
        return new File(dir(), sb.append(".key").toString());
    }

    private static String aliasOf(String fileName) {
        String hex = fileName.substring(0, fileName.length() - 4);
        if ((hex.length() & 1) != 0) return null;
        byte[] b = new byte[hex.length() / 2];
        try {
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    static List<String> aliases() {
        synchronized (LOCK) {
            ArrayList<String> out = new ArrayList<String>();
            String[] names = dir().list();
            if (names == null) return out;
            Arrays.sort(names);
            for (String n : names) {
                if (!n.endsWith(".key")) continue;
                String a = aliasOf(n);
                if (a != null) out.add(a);
            }
            return out;
        }
    }

    static boolean exists(String alias) {
        synchronized (LOCK) {
            return file(alias).isFile();
        }
    }

    static boolean delete(String alias) {
        synchronized (LOCK) {
            File f = file(alias);
            return !f.exists() || f.delete();
        }
    }

    static KeyStoreEntry load(String alias) {
        synchronized (LOCK) {
            File f = file(alias);
            if (!f.isFile()) return null;
            Properties p = new Properties();
            try {
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                return null;
            }
            KeyStoreEntry e = new KeyStoreEntry();
            e.alias = alias;
            e.algorithm = p.getProperty("algorithm");
            e.keySize = Integer.parseInt(p.getProperty("keySize", "0"));
            e.origin = Integer.parseInt(p.getProperty("origin", String.valueOf(KeyProperties.ORIGIN_GENERATED)));
            e.created = Long.parseLong(p.getProperty("created", "0"));
            e.purposes = Integer.parseInt(p.getProperty("purposes", "0"));
            e.blockModes = list(p.getProperty("blockModes"));
            e.encryptionPaddings = list(p.getProperty("encryptionPaddings"));
            e.signaturePaddings = list(p.getProperty("signaturePaddings"));
            e.digests = list(p.getProperty("digests"));
            e.randomizedEncryptionRequired = Boolean.parseBoolean(p.getProperty("randomizedEncryptionRequired", "true"));
            e.userAuthenticationRequired = Boolean.parseBoolean(p.getProperty("userAuthenticationRequired", "false"));
            e.userAuthenticationValidityDurationSeconds = Integer.parseInt(p.getProperty("userAuthenticationValidityDurationSeconds", "0"));
            e.userAuthenticationType = Integer.parseInt(p.getProperty("userAuthenticationType", "0"));
            e.maxUsageCount = Integer.parseInt(p.getProperty("maxUsageCount", "-1"));
            e.validityStart = Long.parseLong(p.getProperty("validityStart", "-1"));
            e.originationEnd = Long.parseLong(p.getProperty("originationEnd", "-1"));
            e.consumptionEnd = Long.parseLong(p.getProperty("consumptionEnd", "-1"));
            String k = p.getProperty("key");
            if (e.algorithm == null || k == null) return null;
            e.key = Base64.decode(k, Base64.NO_WRAP);
            return e;
        }
    }

    void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("algorithm", algorithm);
        p.setProperty("keySize", String.valueOf(keySize));
        p.setProperty("origin", String.valueOf(origin));
        p.setProperty("created", String.valueOf(created));
        p.setProperty("purposes", String.valueOf(purposes));
        p.setProperty("blockModes", join(blockModes));
        p.setProperty("encryptionPaddings", join(encryptionPaddings));
        p.setProperty("signaturePaddings", join(signaturePaddings));
        p.setProperty("digests", join(digests));
        p.setProperty("randomizedEncryptionRequired", String.valueOf(randomizedEncryptionRequired));
        p.setProperty("userAuthenticationRequired", String.valueOf(userAuthenticationRequired));
        p.setProperty("userAuthenticationValidityDurationSeconds", String.valueOf(userAuthenticationValidityDurationSeconds));
        p.setProperty("userAuthenticationType", String.valueOf(userAuthenticationType));
        p.setProperty("maxUsageCount", String.valueOf(maxUsageCount));
        p.setProperty("validityStart", String.valueOf(validityStart));
        p.setProperty("originationEnd", String.valueOf(originationEnd));
        p.setProperty("consumptionEnd", String.valueOf(consumptionEnd));
        p.setProperty("key", Base64.encodeToString(key, Base64.NO_WRAP));
        synchronized (LOCK) {
            File f = file(alias);
            File tmp = new File(f.getPath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                p.store(out, null);
                out.getFD().sync();
            } finally {
                out.close();
            }
            if (!tmp.renameTo(f)) {
                tmp.delete();
                throw new IOException("could not write " + f);
            }
        }
    }

    private static String[] list(String s) {
        if (s == null || s.isEmpty()) return new String[0];
        return s.split(",");
    }

    private static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (String s : a) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }

    static long time(Date d) {
        return d == null ? -1 : d.getTime();
    }

    Date date(long t) {
        return t < 0 ? null : new Date(t);
    }

    private static boolean contains(String[] list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /**
     * Applies the key's authorizations the way Keystore does: purposes, block
     * modes, paddings and validity dates. User authentication cannot be asked
     * for on the Switch and is treated as given.
     */
    void checkUse(String operation, String mode, String padding, String digest) throws InvalidKeyException {
        int purpose;
        boolean originating;
        if ("encrypt".equals(operation)) {
            purpose = KeyProperties.PURPOSE_ENCRYPT;
            originating = true;
        } else if ("decrypt".equals(operation)) {
            purpose = KeyProperties.PURPOSE_DECRYPT;
            originating = false;
        } else if ("sign".equals(operation)) {
            purpose = KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY;
            originating = true;
        } else {
            return;
        }
        if ((purposes & purpose) == 0) {
            throw new KeyPermanentlyInvalidatedException("Incompatible purpose: key " + alias + " does not allow " + operation);
        }
        if (mode != null && algorithm.equals("AES") && !contains(blockModes, mode)) {
            throw new InvalidKeyException("Incompatible block mode: " + mode + " not in " + Arrays.toString(blockModes));
        }
        if (padding != null && algorithm.equals("AES") && !contains(encryptionPaddings, padding)) {
            throw new InvalidKeyException("Incompatible padding scheme: " + padding + " not in " + Arrays.toString(encryptionPaddings));
        }
        long now = System.currentTimeMillis();
        if (validityStart >= 0 && now < validityStart) throw new KeyNotYetValidException("Key not yet valid");
        long end = originating ? originationEnd : consumptionEnd;
        if (end >= 0 && now > end) throw new KeyExpiredException("Key expired");
    }

    static String upper(String s) {
        return s.toUpperCase(Locale.ENGLISH);
    }
}
