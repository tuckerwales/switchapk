package java.security;

public class KeyException extends GeneralSecurityException {
    public KeyException() {
    }

    public KeyException(String msg) {
        super(msg);
    }

    public KeyException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeyException(Throwable cause) {
        super(cause);
    }
}
