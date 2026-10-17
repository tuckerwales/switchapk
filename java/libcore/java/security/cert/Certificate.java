package java.security.cert;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;

/** Base class of certificates; X509Certificate is the one implemented (CertificateFactory "X.509"). */
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
        try {
            return new CertificateRep(type, getEncoded());
        } catch (CertificateException e) {
            throw new java.io.NotSerializableException("java.security.cert.Certificate: " + type + ": " + e.getMessage());
        }
    }

    protected static class CertificateRep implements java.io.Serializable {
        private static final long serialVersionUID = -8563758940495660020L;

        private final String type;
        private final byte[] data;

        protected CertificateRep(String type, byte[] data) {
            this.type = type;
            this.data = data;
        }

        protected Object readResolve() throws java.io.ObjectStreamException {
            try {
                return CertificateFactory.getInstance(type).generateCertificate(new java.io.ByteArrayInputStream(data));
            } catch (CertificateException e) {
                throw new java.io.NotSerializableException("java.security.cert.Certificate: " + type + ": " + e.getMessage());
            }
        }
    }
}
