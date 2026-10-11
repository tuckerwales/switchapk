package java.security.cert;

/** A certificate revocation list. No CRL implementation is built in; CertificateFactory.generateCRL throws. */
public abstract class CRL {
    private final String type;

    protected CRL(String type) {
        this.type = type;
    }

    public final String getType() {
        return type;
    }

    public abstract String toString();

    public abstract boolean isRevoked(Certificate cert);
}
