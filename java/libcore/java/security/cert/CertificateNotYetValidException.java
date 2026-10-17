package java.security.cert;

public class CertificateNotYetValidException extends CertificateException {
    public CertificateNotYetValidException() {
    }

    public CertificateNotYetValidException(String message) {
        super(message);
    }
}
