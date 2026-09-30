package java.security;

public class DigestException extends GeneralSecurityException {
    public DigestException() {
    }

    public DigestException(String msg) {
        super(msg);
    }

    public DigestException(String message, Throwable cause) {
        super(message, cause);
    }

    public DigestException(Throwable cause) {
        super(cause);
    }
}
