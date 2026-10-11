package java.security.cert;

import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import libcore.crypto.Services;

public class CertificateFactory {
    private final String type;
    private final Provider provider;
    private final CertificateFactorySpi certFacSpi;

    protected CertificateFactory(CertificateFactorySpi certFacSpi, Provider provider, String type) {
        this.certFacSpi = certFacSpi;
        this.provider = provider;
        this.type = type;
    }

    public static final CertificateFactory getInstance(String type) throws CertificateException {
        try {
            Provider.Service s = Services.first("CertificateFactory", type);
            return new CertificateFactory(Services.newSpi(s, CertificateFactorySpi.class, null), s.getProvider(), type);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(type + " not found", e);
        }
    }

    public static final CertificateFactory getInstance(String type, String provider)
            throws CertificateException, NoSuchProviderException {
        try {
            Provider.Service s = Services.in("CertificateFactory", type, provider);
            return new CertificateFactory(Services.newSpi(s, CertificateFactorySpi.class, null), s.getProvider(), type);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(type + " not found", e);
        }
    }

    public static final CertificateFactory getInstance(String type, Provider provider) throws CertificateException {
        try {
            Provider.Service s = Services.in("CertificateFactory", type, provider);
            return new CertificateFactory(Services.newSpi(s, CertificateFactorySpi.class, null), s.getProvider(), type);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(type + " not found", e);
        }
    }

    public final Provider getProvider() {
        return provider;
    }

    public final String getType() {
        return type;
    }

    public final Certificate generateCertificate(InputStream inStream) throws CertificateException {
        return certFacSpi.engineGenerateCertificate(inStream);
    }

    public final Iterator<String> getCertPathEncodings() {
        return certFacSpi.engineGetCertPathEncodings();
    }

    public final CertPath generateCertPath(InputStream inStream) throws CertificateException {
        return certFacSpi.engineGenerateCertPath(inStream);
    }

    public final CertPath generateCertPath(InputStream inStream, String encoding) throws CertificateException {
        return certFacSpi.engineGenerateCertPath(inStream, encoding);
    }

    public final CertPath generateCertPath(List<? extends Certificate> certificates) throws CertificateException {
        return certFacSpi.engineGenerateCertPath(certificates);
    }

    public final Collection<? extends Certificate> generateCertificates(InputStream inStream) throws CertificateException {
        return certFacSpi.engineGenerateCertificates(inStream);
    }

    public final CRL generateCRL(InputStream inStream) throws CRLException {
        return certFacSpi.engineGenerateCRL(inStream);
    }

    public final Collection<? extends CRL> generateCRLs(InputStream inStream) throws CRLException {
        return certFacSpi.engineGenerateCRLs(inStream);
    }
}
