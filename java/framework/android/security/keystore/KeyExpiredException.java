package android.security.keystore;

public class KeyExpiredException extends java.security.InvalidKeyException {
    public KeyExpiredException() {
        super();
    }

    public KeyExpiredException(String message) {
        super(message);
    }

    public KeyExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
