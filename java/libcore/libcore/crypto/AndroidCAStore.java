package libcore.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreSpi;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;

/**
 * KeyStore "AndroidCAStore": the system CA certificates, read-only, as Android's
 * TrustedCertificateKeyStoreSpi. Each file in /system/etc/security/cacerts (named
 * &lt;subject hash&gt;.&lt;n&gt;, a PEM certificate followed by text) is the alias
 * "system:&lt;file name&gt;". load() only lists the directory; certificates are
 * parsed on first use and cached for the process, shared by all instances.
 */
final class AndroidCAStore extends KeyStoreSpi {
    private static final String PREFIX = "system:";

    /** File name to parsed certificate, shared by every AndroidCAStore. */
    private static final HashMap<String, X509Certificate> CACHE = new HashMap<String, X509Certificate>();

    private File dir;
    private String[] names = new String[0];

    static File systemDir() {
        String root = System.getenv("ANDROID_ROOT");
        return new File((root != null ? root : "/system") + "/etc/security/cacerts");
    }

    public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        if (stream != null) throw new UnsupportedOperationException();
        dir = systemDir();
        String[] list = dir.list();
        if (list == null) list = new String[0];
        ArrayList<String> files = new ArrayList<String>();
        for (String n : list) {
            if (isCertFileName(n)) files.add(n);
        }
        Collections.sort(files);
        names = files.toArray(new String[0]);
    }

    private static boolean isCertFileName(String n) {
        int dot = n.indexOf('.');
        if (dot != 8 || n.length() == 9) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (i == 8) continue;
            if (i < 8 ? Character.digit(c, 16) < 0 : (c < '0' || c > '9')) return false;
        }
        return true;
    }

    private String fileOf(String alias) {
        if (alias == null || !alias.startsWith(PREFIX)) return null;
        String f = alias.substring(PREFIX.length());
        return Arrays.binarySearch(names, f) >= 0 ? f : null;
    }

    private X509Certificate load(String file) {
        synchronized (CACHE) {
            X509Certificate c = CACHE.get(file);
            if (c != null) return c;
        }
        X509Certificate c = null;
        try {
            InputStream in = new FileInputStream(new File(dir, file));
            try {
                byte[] der = X509Factory.readBlock(in);
                if (der != null) c = new X509CertImpl(der);
            } finally {
                in.close();
            }
        } catch (IOException | CertificateException e) {
            System.err.println("AndroidCAStore: cannot read " + file + ": " + e);
            return null;
        }
        synchronized (CACHE) {
            CACHE.put(file, c);
        }
        return c;
    }

    public Certificate engineGetCertificate(String alias) {
        String f = fileOf(alias);
        return f == null ? null : load(f);
    }

    public Certificate[] engineGetCertificateChain(String alias) {
        return null;
    }

    public Key engineGetKey(String alias, char[] password) {
        return null;
    }

    public Date engineGetCreationDate(String alias) {
        String f = fileOf(alias);
        if (f == null) return null;
        long t = new File(dir, f).lastModified();
        return t == 0 ? null : new Date(t);
    }

    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
        throw new UnsupportedOperationException();
    }

    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
        throw new UnsupportedOperationException();
    }

    public void engineSetCertificateEntry(String alias, Certificate cert) {
        throw new UnsupportedOperationException("AndroidCAStore is read-only");
    }

    public void engineDeleteEntry(String alias) {
        throw new UnsupportedOperationException("AndroidCAStore is read-only");
    }

    public Enumeration<String> engineAliases() {
        ArrayList<String> out = new ArrayList<String>(names.length);
        for (String n : names) out.add(PREFIX + n);
        return Collections.enumeration(out);
    }

    public boolean engineContainsAlias(String alias) {
        return fileOf(alias) != null;
    }

    public int engineSize() {
        return names.length;
    }

    public boolean engineIsKeyEntry(String alias) {
        return false;
    }

    public boolean engineIsCertificateEntry(String alias) {
        return engineContainsAlias(alias);
    }

    /** Looks only at the files named for the certificate's subject hash, as Android does. */
    public String engineGetCertificateAlias(Certificate cert) {
        if (!(cert instanceof X509Certificate)) return null;
        String hash;
        try {
            hash = subjectHashOld((X509Certificate) cert);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        for (int i = 0; ; i++) {
            String f = hash + "." + i;
            if (Arrays.binarySearch(names, f) < 0) return null;
            X509Certificate c = load(f);
            if (cert.equals(c)) return PREFIX + f;
        }
    }

    /** OpenSSL's X509_NAME_hash_old: the first four bytes of MD5(subject DER), little-endian, as 8 hex digits. */
    static String subjectHashOld(X509Certificate cert) throws NoSuchAlgorithmException {
        byte[] d = MessageDigest.getInstance("MD5").digest(cert.getSubjectX500Principal().getEncoded());
        long h = (d[0] & 0xffL) | (d[1] & 0xffL) << 8 | (d[2] & 0xffL) << 16 | (d[3] & 0xffL) << 24;
        String s = Long.toHexString(h);
        return "00000000".substring(s.length()) + s.toLowerCase(Locale.US);
    }

    public void engineStore(OutputStream stream, char[] password) {
        throw new UnsupportedOperationException();
    }
}
