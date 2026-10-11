package java.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Set;
import javax.crypto.SecretKey;

public abstract class KeyStoreSpi {
    public KeyStoreSpi() {
    }

    public abstract Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException;

    public abstract Certificate[] engineGetCertificateChain(String alias);

    public abstract Certificate engineGetCertificate(String alias);

    public abstract Date engineGetCreationDate(String alias);

    public abstract void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException;

    public abstract void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException;

    public abstract void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException;

    public abstract void engineDeleteEntry(String alias) throws KeyStoreException;

    public abstract Enumeration<String> engineAliases();

    public abstract boolean engineContainsAlias(String alias);

    public abstract int engineSize();

    public abstract boolean engineIsKeyEntry(String alias);

    public abstract boolean engineIsCertificateEntry(String alias);

    public abstract String engineGetCertificateAlias(Certificate cert);

    public abstract void engineStore(OutputStream stream, char[] password)
            throws IOException, NoSuchAlgorithmException, CertificateException;

    public void engineStore(KeyStore.LoadStoreParameter param)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        throw new UnsupportedOperationException();
    }

    public abstract void engineLoad(InputStream stream, char[] password)
            throws IOException, NoSuchAlgorithmException, CertificateException;

    public void engineLoad(KeyStore.LoadStoreParameter param)
            throws IOException, NoSuchAlgorithmException, CertificateException {
        if (param == null) {
            engineLoad((InputStream) null, (char[]) null);
            return;
        }
        KeyStore.ProtectionParameter protection = param.getProtectionParameter();
        if (protection instanceof KeyStore.PasswordProtection) {
            engineLoad(null, ((KeyStore.PasswordProtection) protection).getPassword());
        } else if (protection == null) {
            engineLoad(null, null);
        } else {
            throw new NoSuchAlgorithmException("ProtectionParameter must be PasswordProtection or CallbackHandlerProtection");
        }
    }

    public Set<KeyStore.Entry.Attribute> engineGetAttributes(String alias) {
        return Collections.emptySet();
    }

    public KeyStore.Entry engineGetEntry(String alias, KeyStore.ProtectionParameter protParam)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException {
        if (!engineContainsAlias(alias)) return null;
        if (protParam == null && engineIsCertificateEntry(alias)) {
            return new KeyStore.TrustedCertificateEntry(engineGetCertificate(alias));
        }
        char[] password = null;
        if (protParam instanceof KeyStore.PasswordProtection) {
            password = ((KeyStore.PasswordProtection) protParam).getPassword();
        } else if (protParam != null) {
            throw new UnrecoverableKeyException("ProtectionParameter must be PasswordProtection");
        }
        if (engineIsCertificateEntry(alias)) {
            throw new UnsupportedOperationException("trusted certificate entries are not password-protected");
        }
        if (engineIsKeyEntry(alias)) {
            Key key = engineGetKey(alias, password);
            if (key instanceof PrivateKey) {
                return new KeyStore.PrivateKeyEntry((PrivateKey) key, engineGetCertificateChain(alias));
            }
            if (key instanceof SecretKey) {
                return new KeyStore.SecretKeyEntry((SecretKey) key);
            }
        }
        throw new UnsupportedOperationException();
    }

    public void engineSetEntry(String alias, KeyStore.Entry entry, KeyStore.ProtectionParameter protParam)
            throws KeyStoreException {
        if (protParam != null && !(protParam instanceof KeyStore.PasswordProtection)) {
            throw new KeyStoreException("unsupported protection parameter");
        }
        char[] password = protParam != null ? ((KeyStore.PasswordProtection) protParam).getPassword() : null;
        if (entry instanceof KeyStore.TrustedCertificateEntry) {
            engineSetCertificateEntry(alias, ((KeyStore.TrustedCertificateEntry) entry).getTrustedCertificate());
        } else if (entry instanceof KeyStore.PrivateKeyEntry) {
            KeyStore.PrivateKeyEntry e = (KeyStore.PrivateKeyEntry) entry;
            engineSetKeyEntry(alias, e.getPrivateKey(), password, e.getCertificateChain());
        } else if (entry instanceof KeyStore.SecretKeyEntry) {
            engineSetKeyEntry(alias, ((KeyStore.SecretKeyEntry) entry).getSecretKey(), password, null);
        } else {
            throw new KeyStoreException("unsupported entry type: " + entry.getClass().getName());
        }
    }

    public boolean engineEntryInstanceOf(String alias, Class<? extends KeyStore.Entry> entryClass) {
        if (entryClass == KeyStore.TrustedCertificateEntry.class) return engineIsCertificateEntry(alias);
        if (entryClass == KeyStore.PrivateKeyEntry.class) {
            return engineIsKeyEntry(alias) && engineGetCertificate(alias) != null;
        }
        if (entryClass == KeyStore.SecretKeyEntry.class) {
            return engineIsKeyEntry(alias) && engineGetCertificate(alias) == null;
        }
        return false;
    }

    public boolean engineProbe(InputStream stream) throws IOException {
        if (stream == null) throw new NullPointerException("input stream must not be null");
        return false;
    }
}
