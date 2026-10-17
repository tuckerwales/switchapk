package libcore.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * KeyStore "BKS" (Android's default type) and "PKCS12": an in-memory store of
 * trusted certificates and keys with their chains. load(null, ...) starts empty;
 * a BKS stream (BouncyCastle v1/v2 format) loads its certificate and unprotected
 * key entries, checking the integrity MAC when a password is given (password-sealed
 * key entries are kept but their keys cannot be recovered). Loading PKCS#12 files
 * and store() are not supported. PKCS12 aliases are case-insensitive, as in
 * BouncyCastle.
 */
final class MemoryKeyStore extends KeyStoreSpi {
    private static final int BKS_CERTIFICATE = 1, BKS_KEY = 2, BKS_SECRET = 3, BKS_SEALED = 4;

    private static final class Entry {
        final String alias;
        final Date date;
        final Certificate cert;
        final Key key;
        final Certificate[] chain;
        /** Set for BKS secret or sealed entries whose key we cannot decode. */
        final boolean opaqueKey;

        Entry(String alias, Date date, Certificate cert, Key key, Certificate[] chain, boolean opaqueKey) {
            this.alias = alias;
            this.date = date;
            this.cert = cert;
            this.key = key;
            this.chain = chain;
            this.opaqueKey = opaqueKey;
        }

        boolean isKey() {
            return key != null || opaqueKey;
        }
    }

    private final boolean pkcs12;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>();

    MemoryKeyStore(boolean pkcs12) {
        this.pkcs12 = pkcs12;
    }

    private String k(String alias) {
        return pkcs12 ? alias.toLowerCase(Locale.ENGLISH) : alias;
    }

    private Entry get(String alias) {
        return alias == null ? null : entries.get(k(alias));
    }

    public synchronized Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        Entry e = get(alias);
        if (e == null || !e.isKey()) return null;
        if (e.key == null) throw new UnrecoverableKeyException("password-protected BKS key entries are not supported");
        return e.key;
    }

    public synchronized Certificate[] engineGetCertificateChain(String alias) {
        Entry e = get(alias);
        return e == null || e.chain == null ? null : e.chain.clone();
    }

    public synchronized Certificate engineGetCertificate(String alias) {
        Entry e = get(alias);
        if (e == null) return null;
        if (e.cert != null) return e.cert;
        return e.chain != null && e.chain.length > 0 ? e.chain[0] : null;
    }

    public synchronized Date engineGetCreationDate(String alias) {
        Entry e = get(alias);
        return e == null ? null : new Date(e.date.getTime());
    }

    public synchronized void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        if (alias == null || key == null) throw new KeyStoreException("null alias or key");
        if (key instanceof java.security.PrivateKey && (chain == null || chain.length == 0)) {
            throw new KeyStoreException("Private key must be accompanied by certificate chain");
        }
        Certificate[] c = chain == null || chain.length == 0 ? null : chain.clone();
        entries.put(k(alias), new Entry(alias, new Date(), null, key, c, false));
    }

    public synchronized void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new KeyStoreException("Setting protected keys in encoded form is not supported");
    }

    public synchronized void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        if (alias == null) throw new KeyStoreException("null alias");
        Entry e = get(alias);
        if (e != null && e.isKey()) throw new KeyStoreException("Cannot overwrite own key entry with a trusted certificate: " + alias);
        entries.put(k(alias), new Entry(alias, new Date(), cert, null, null, false));
    }

    public synchronized void engineDeleteEntry(String alias) throws KeyStoreException {
        if (alias != null) entries.remove(k(alias));
    }

    public synchronized Enumeration<String> engineAliases() {
        ArrayList<String> out = new ArrayList<String>();
        for (Entry e : entries.values()) out.add(e.alias);
        return Collections.enumeration(out);
    }

    public synchronized boolean engineContainsAlias(String alias) {
        return get(alias) != null;
    }

    public synchronized int engineSize() {
        return entries.size();
    }

    public synchronized boolean engineIsKeyEntry(String alias) {
        Entry e = get(alias);
        return e != null && e.isKey();
    }

    public synchronized boolean engineIsCertificateEntry(String alias) {
        Entry e = get(alias);
        return e != null && e.cert != null;
    }

    public synchronized String engineGetCertificateAlias(Certificate cert) {
        if (cert == null) return null;
        for (Entry e : entries.values()) {
            Certificate c = e.cert != null ? e.cert : e.chain != null && e.chain.length > 0 ? e.chain[0] : null;
            if (cert.equals(c)) return e.alias;
        }
        return null;
    }

    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new IOException("storing a " + (pkcs12 ? "PKCS12" : "BKS") + " keystore is not supported");
    }

    public synchronized void engineLoad(InputStream stream, char[] password)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        entries.clear();
        if (stream == null) return;
        if (pkcs12) throw new IOException("loading PKCS12 keystores is not supported yet");
        loadBks(readAll(stream), password);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ BKS (BouncyCastle BcKeyStoreSpi)

    private void loadBks(byte[] data, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int version = in.readInt();
        if (version != 0 && version != 1 && version != 2) throw new IOException("Wrong version of key store.");
        int saltLength = in.readInt();
        if (saltLength <= 0 || saltLength > data.length) throw new IOException("Invalid salt detected");
        byte[] salt = new byte[saltLength];
        in.readFully(salt);
        int iterations = in.readInt();
        int start = data.length - in.available();
        CertificateFactory cf = null;
        int type;
        while ((type = in.read()) > 0) {
            String alias = in.readUTF();
            Date date = new Date(in.readLong());
            int chainLength = in.readInt();
            Certificate[] chain = null;
            if (chainLength < 0 || chainLength > 1000) throw new IOException("bad certificate chain length");
            if (chainLength != 0) {
                chain = new Certificate[chainLength];
                for (int i = 0; i < chainLength; i++) chain[i] = readCertificate(in);
            }
            switch (type) {
                case BKS_CERTIFICATE:
                    entries.put(alias, new Entry(alias, date, readCertificate(in), null, null, false));
                    break;
                case BKS_KEY:
                    entries.put(alias, new Entry(alias, date, null, readKey(in), chain, false));
                    break;
                case BKS_SECRET:
                case BKS_SEALED: {
                    int len = in.readInt();
                    if (len < 0 || len > in.available()) throw new IOException("bad entry length");
                    in.readFully(new byte[len]);
                    entries.put(alias, new Entry(alias, date, null, null, chain, true));
                    break;
                }
                default:
                    throw new IOException("Unknown object type in store.");
            }
        }
        int end = data.length - in.available();
        byte[] mac = new byte[20];
        in.readFully(mac);
        if (password != null && password.length != 0) {
            // HMAC-SHA1 keyed by the PKCS#12 KDF (ID 3); version 1 stores asked for 20 bits, so 2 bytes, of key.
            byte[] key = pkcs12MacKey(password, salt, iterations, version == 2 ? 20 : 2);
            try {
                Mac m = Mac.getInstance("HmacSHA1");
                m.init(new SecretKeySpec(key, "HmacSHA1"));
                m.update(data, start, end - start);
                if (!MessageDigest.isEqual(m.doFinal(), mac)) {
                    entries.clear();
                    throw new IOException("KeyStore integrity check failed.");
                }
            } catch (java.security.InvalidKeyException e) {
                throw new IOException(e);
            }
        }
    }

    private static Certificate readCertificate(DataInputStream in) throws IOException, CertificateException {
        String type = in.readUTF();
        int len = in.readInt();
        if (len < 0 || len > in.available()) throw new IOException("bad certificate length");
        byte[] enc = new byte[len];
        in.readFully(enc);
        return CertificateFactory.getInstance(type).generateCertificate(new ByteArrayInputStream(enc));
    }

    private static Key readKey(DataInputStream in) throws IOException {
        int keyType = in.read();
        String format = in.readUTF();
        String algorithm = in.readUTF();
        int len = in.readInt();
        if (len < 0 || len > in.available()) throw new IOException("bad key length");
        byte[] enc = new byte[len];
        in.readFully(enc);
        try {
            if (format.equals("PKCS#8") || format.equals("PKCS8")) {
                return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(enc));
            }
            if (format.equals("X.509") || format.equals("X509")) {
                return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(enc));
            }
            if (format.equals("RAW")) return new SecretKeySpec(enc, algorithm);
        } catch (Exception e) {
            throw new IOException("Exception creating key: " + e, e);
        }
        throw new IOException("Key format " + format + " not recognised (key type " + keyType + ")");
    }

    /** RFC 7292 appendix B key derivation with SHA-1, ID 3 (MAC key); n is at most 20 bytes. */
    static byte[] pkcs12MacKey(char[] password, byte[] salt, int iterations, int n) throws NoSuchAlgorithmException {
        final int v = 64;
        byte[] pw = new byte[password.length == 0 ? 0 : (password.length + 1) * 2];
        for (int i = 0; i < password.length; i++) {
            pw[2 * i] = (byte) (password[i] >>> 8);
            pw[2 * i + 1] = (byte) password[i];
        }
        int sLen = v * ((salt.length + v - 1) / v), pLen = v * ((pw.length + v - 1) / v);
        byte[] buf = new byte[v + sLen + pLen];
        for (int i = 0; i < v; i++) buf[i] = 3;
        for (int i = 0; i < sLen; i++) buf[v + i] = salt[i % salt.length];
        for (int i = 0; i < pLen; i++) buf[v + sLen + i] = pw[i % pw.length];
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] a = md.digest(buf);
        for (int i = 1; i < iterations; i++) a = md.digest(a);
        byte[] out = new byte[n];
        System.arraycopy(a, 0, out, 0, n);
        return out;
    }
}
