package java.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import libcore.crypto.Services;

public class KeyStore {
    private final KeyStoreSpi keyStoreSpi;
    private final Provider provider;
    private final String type;
    private boolean initialized;

    protected KeyStore(KeyStoreSpi keyStoreSpi, Provider provider, String type) {
        this.keyStoreSpi = keyStoreSpi;
        this.provider = provider;
        this.type = type;
    }

    public static KeyStore getInstance(String type) throws KeyStoreException {
        try {
            Provider.Service s = Services.first("KeyStore", type);
            return new KeyStore(Services.newSpi(s, KeyStoreSpi.class, null), s.getProvider(), type);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyStoreException(type + " not found", e);
        }
    }

    public static KeyStore getInstance(String type, String provider) throws KeyStoreException, NoSuchProviderException {
        try {
            Provider.Service s = Services.in("KeyStore", type, provider);
            return new KeyStore(Services.newSpi(s, KeyStoreSpi.class, null), s.getProvider(), type);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyStoreException(type + " not found", e);
        }
    }

    public static KeyStore getInstance(String type, Provider provider) throws KeyStoreException {
        try {
            Provider.Service s = Services.in("KeyStore", type, provider);
            return new KeyStore(Services.newSpi(s, KeyStoreSpi.class, null), s.getProvider(), type);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyStoreException(type + " not found", e);
        }
    }

    public static final KeyStore getInstance(File file, char[] password)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore ks = probe(file);
        FileInputStream in = new FileInputStream(file);
        try {
            ks.load(in, password);
        } finally {
            in.close();
        }
        return ks;
    }

    public static final KeyStore getInstance(File file, LoadStoreParameter param)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore ks = probe(file);
        ks.load(param);
        return ks;
    }

    private static KeyStore probe(File file) throws KeyStoreException, IOException {
        if (file == null) throw new NullPointerException();
        if (!file.isFile()) throw new IllegalArgumentException("File does not exist or it does not refer to a normal file: " + file);
        for (Provider p : Security.getProviders()) {
            for (Provider.Service s : p.getServices()) {
                if (!"KeyStore".equals(s.getType())) continue;
                try {
                    KeyStoreSpi spi = Services.newSpi(s, KeyStoreSpi.class, null);
                    FileInputStream in = new FileInputStream(file);
                    try {
                        if (spi.engineProbe(in)) return new KeyStore(spi, p, s.getAlgorithm());
                    } finally {
                        in.close();
                    }
                } catch (NoSuchAlgorithmException ignored) {
                }
            }
        }
        throw new KeyStoreException("Unrecognized keystore format. Please load it with a specified type");
    }

    public static final String getDefaultType() {
        String t = Security.getProperty("keystore.type");
        return t != null ? t : "PKCS12";
    }

    public final Provider getProvider() {
        return provider;
    }

    public final String getType() {
        return type;
    }

    private void check() throws KeyStoreException {
        if (!initialized) throw new KeyStoreException("Uninitialized keystore");
    }

    public final Set<Entry.Attribute> getAttributes(String alias) throws KeyStoreException {
        check();
        if (alias == null) throw new NullPointerException("invalid null input");
        return keyStoreSpi.engineGetAttributes(alias);
    }

    public final Key getKey(String alias, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        check();
        return keyStoreSpi.engineGetKey(alias, password);
    }

    public final Certificate[] getCertificateChain(String alias) throws KeyStoreException {
        check();
        return keyStoreSpi.engineGetCertificateChain(alias);
    }

    public final Certificate getCertificate(String alias) throws KeyStoreException {
        check();
        return keyStoreSpi.engineGetCertificate(alias);
    }

    public final Date getCreationDate(String alias) throws KeyStoreException {
        check();
        return keyStoreSpi.engineGetCreationDate(alias);
    }

    public final void setKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        check();
        if (key instanceof PrivateKey && (chain == null || chain.length == 0)) {
            throw new IllegalArgumentException("Private key must be accompanied by certificate chain");
        }
        keyStoreSpi.engineSetKeyEntry(alias, key, password, chain);
    }

    public final void setKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        check();
        keyStoreSpi.engineSetKeyEntry(alias, key, chain);
    }

    public final void setCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        check();
        keyStoreSpi.engineSetCertificateEntry(alias, cert);
    }

    public final void deleteEntry(String alias) throws KeyStoreException {
        check();
        keyStoreSpi.engineDeleteEntry(alias);
    }

    public final Enumeration<String> aliases() throws KeyStoreException {
        check();
        return keyStoreSpi.engineAliases();
    }

    public final boolean containsAlias(String alias) throws KeyStoreException {
        check();
        return keyStoreSpi.engineContainsAlias(alias);
    }

    public final int size() throws KeyStoreException {
        check();
        return keyStoreSpi.engineSize();
    }

    public final boolean isKeyEntry(String alias) throws KeyStoreException {
        check();
        return keyStoreSpi.engineIsKeyEntry(alias);
    }

    public final boolean isCertificateEntry(String alias) throws KeyStoreException {
        check();
        return keyStoreSpi.engineIsCertificateEntry(alias);
    }

    public final String getCertificateAlias(Certificate cert) throws KeyStoreException {
        check();
        return keyStoreSpi.engineGetCertificateAlias(cert);
    }

    public final void store(OutputStream stream, char[] password)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        check();
        keyStoreSpi.engineStore(stream, password);
    }

    public final void store(LoadStoreParameter param)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        check();
        keyStoreSpi.engineStore(param);
    }

    public final void load(InputStream stream, char[] password)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        keyStoreSpi.engineLoad(stream, password);
        initialized = true;
    }

    public final void load(LoadStoreParameter param) throws IOException, NoSuchAlgorithmException, CertificateException {
        keyStoreSpi.engineLoad(param);
        initialized = true;
    }

    public final Entry getEntry(String alias, ProtectionParameter protParam)
            throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException {
        if (alias == null) throw new NullPointerException("invalid null input");
        check();
        return keyStoreSpi.engineGetEntry(alias, protParam);
    }

    public final void setEntry(String alias, Entry entry, ProtectionParameter protParam) throws KeyStoreException {
        if (alias == null || entry == null) throw new NullPointerException("invalid null input");
        check();
        keyStoreSpi.engineSetEntry(alias, entry, protParam);
    }

    public final boolean entryInstanceOf(String alias, Class<? extends Entry> entryClass) throws KeyStoreException {
        if (alias == null || entryClass == null) throw new NullPointerException("invalid null input");
        check();
        return keyStoreSpi.engineEntryInstanceOf(alias, entryClass);
    }

    public interface LoadStoreParameter {
        ProtectionParameter getProtectionParameter();
    }

    public interface ProtectionParameter {
    }

    public static class PasswordProtection implements ProtectionParameter, Destroyable {
        private final char[] password;
        private final String protectionAlgorithm;
        private final AlgorithmParameterSpec protectionParameters;
        private volatile boolean destroyed;

        public PasswordProtection(char[] password) {
            this(password, null, null, false);
        }

        public PasswordProtection(char[] password, String protectionAlgorithm, AlgorithmParameterSpec protectionParameters) {
            this(password, protectionAlgorithm, protectionParameters, true);
        }

        private PasswordProtection(char[] password, String alg, AlgorithmParameterSpec params, boolean checkAlg) {
            if (checkAlg && alg == null) throw new NullPointerException("invalid null input");
            this.password = password == null ? null : password.clone();
            this.protectionAlgorithm = alg;
            this.protectionParameters = params;
        }

        public String getProtectionAlgorithm() {
            return protectionAlgorithm;
        }

        public AlgorithmParameterSpec getProtectionParameters() {
            return protectionParameters;
        }

        public synchronized char[] getPassword() {
            if (destroyed) throw new IllegalStateException("password has been cleared");
            return password;
        }

        public synchronized void destroy() throws DestroyFailedException {
            destroyed = true;
            if (password != null) Arrays.fill(password, ' ');
        }

        public synchronized boolean isDestroyed() {
            return destroyed;
        }
    }

    public interface Entry {
        default Set<Attribute> getAttributes() {
            return Collections.emptySet();
        }

        interface Attribute {
            String getName();

            String getValue();
        }
    }

    public static final class PrivateKeyEntry implements Entry {
        private final PrivateKey privKey;
        private final Certificate[] chain;
        private final Set<Attribute> attributes;

        public PrivateKeyEntry(PrivateKey privateKey, Certificate[] chain) {
            this(privateKey, chain, Collections.<Attribute>emptySet());
        }

        public PrivateKeyEntry(PrivateKey privateKey, Certificate[] chain, Set<Attribute> attributes) {
            if (privateKey == null || chain == null || attributes == null) throw new NullPointerException("invalid null input");
            if (chain.length == 0) throw new IllegalArgumentException("invalid zero-length input chain");
            this.privKey = privateKey;
            this.chain = chain.clone();
            this.attributes = Collections.unmodifiableSet(new HashSet<Attribute>(attributes));
        }

        public PrivateKey getPrivateKey() {
            return privKey;
        }

        public Certificate[] getCertificateChain() {
            return chain.clone();
        }

        public Certificate getCertificate() {
            return chain[0];
        }

        public Set<Attribute> getAttributes() {
            return attributes;
        }

        public String toString() {
            return "Private key entry and certificate chain with " + chain.length + " elements";
        }
    }

    public static final class SecretKeyEntry implements Entry {
        private final SecretKey sKey;
        private final Set<Attribute> attributes;

        public SecretKeyEntry(SecretKey secretKey) {
            this(secretKey, Collections.<Attribute>emptySet());
        }

        public SecretKeyEntry(SecretKey secretKey, Set<Attribute> attributes) {
            if (secretKey == null || attributes == null) throw new NullPointerException("invalid null input");
            this.sKey = secretKey;
            this.attributes = Collections.unmodifiableSet(new HashSet<Attribute>(attributes));
        }

        public SecretKey getSecretKey() {
            return sKey;
        }

        public Set<Attribute> getAttributes() {
            return attributes;
        }

        public String toString() {
            return "Secret key entry with algorithm " + sKey.getAlgorithm();
        }
    }

    public static final class TrustedCertificateEntry implements Entry {
        private final Certificate cert;
        private final Set<Attribute> attributes;

        public TrustedCertificateEntry(Certificate trustedCert) {
            this(trustedCert, Collections.<Attribute>emptySet());
        }

        public TrustedCertificateEntry(Certificate trustedCert, Set<Attribute> attributes) {
            if (trustedCert == null || attributes == null) throw new NullPointerException("invalid null input");
            this.cert = trustedCert;
            this.attributes = Collections.unmodifiableSet(new HashSet<Attribute>(attributes));
        }

        public Certificate getTrustedCertificate() {
            return cert;
        }

        public Set<Attribute> getAttributes() {
            return attributes;
        }

        public String toString() {
            return "Trusted certificate entry:\r\n" + cert.toString();
        }
    }
}
