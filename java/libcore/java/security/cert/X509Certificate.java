package java.security.cert;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import libcore.crypto.X509CertImpl;

/**
 * An X.509 certificate. The built-in implementation is libcore.crypto.X509CertImpl;
 * the non-abstract methods here work for other subclasses by parsing getTBSCertificate()
 * and getExtensionValue(), as OpenJDK's do.
 */
public abstract class X509Certificate extends Certificate implements X509Extension {
    private static final long serialVersionUID = -2491127588187038216L;

    private transient X500Principal subjectX500Principal, issuerX500Principal;

    protected X509Certificate() {
        super("X.509");
    }

    public abstract void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException;

    public abstract void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException;

    public abstract int getVersion();

    public abstract BigInteger getSerialNumber();

    public abstract Principal getIssuerDN();

    public X500Principal getIssuerX500Principal() {
        if (issuerX500Principal == null) issuerX500Principal = X509CertImpl.issuerOf(this);
        return issuerX500Principal;
    }

    public abstract Principal getSubjectDN();

    public X500Principal getSubjectX500Principal() {
        if (subjectX500Principal == null) subjectX500Principal = X509CertImpl.subjectOf(this);
        return subjectX500Principal;
    }

    public abstract Date getNotBefore();

    public abstract Date getNotAfter();

    public abstract byte[] getTBSCertificate() throws CertificateEncodingException;

    public abstract byte[] getSignature();

    public abstract String getSigAlgName();

    public abstract String getSigAlgOID();

    public abstract byte[] getSigAlgParams();

    public abstract boolean[] getIssuerUniqueID();

    public abstract boolean[] getSubjectUniqueID();

    public abstract boolean[] getKeyUsage();

    public List<String> getExtendedKeyUsage() throws CertificateParsingException {
        return X509CertImpl.extendedKeyUsageOf(this);
    }

    public abstract int getBasicConstraints();

    public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
        return X509CertImpl.alternativeNamesOf(this, X509CertImpl.SUBJECT_ALT_NAME_OID);
    }

    public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
        return X509CertImpl.alternativeNamesOf(this, X509CertImpl.ISSUER_ALT_NAME_OID);
    }

    public void verify(PublicKey key, Provider sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        X509CertImpl.verify(this, key, sigProvider);
    }
}
