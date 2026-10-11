package java.security.cert;

public class CertificateExpiredException extends CertificateException {
    public CertificateExpiredException() {
    }

    public CertificateExpiredException(String message) {
        super(message);
    }
}
