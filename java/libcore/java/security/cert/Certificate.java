package java.security.cert;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;

/** Base class of certificates. Parsing (X509Certificate, CertificateFactory) comes with TLS (WS11). */
public abstract class Certificate implements java.io.Serializable {
    private final String type;

    protected Certificate(String type) {
        this.type = type;
    }

    public final String getType() {
        return type;
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Certificate)) return false;
        try {
            return Arrays.equals(getEncoded(), ((Certificate) other).getEncoded());
        } catch (CertificateException e) {
            return false;
        }
    }

    public int hashCode() {
        try {
            return Arrays.hashCode(getEncoded());
        } catch (CertificateException e) {
            return 0;
        }
    }

    public abstract byte[] getEncoded() throws CertificateEncodingException;

    public abstract void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchProviderException, SignatureException;

    public abstract void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException;

    public void verify(PublicKey key, Provider sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        throw new UnsupportedOperationException();
    }

    public abstract String toString();

    public abstract PublicKey getPublicKey();

    protected Object writeReplace() throws java.io.ObjectStreamException {
        return this;
    }
}
