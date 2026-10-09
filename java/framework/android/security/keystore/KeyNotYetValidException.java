package android.security.keystore;

public class KeyNotYetValidException extends java.security.InvalidKeyException {
    public KeyNotYetValidException() {
        super();
    }

    public KeyNotYetValidException(String message) {
        super(message);
    }

    public KeyNotYetValidException(String message, Throwable cause) {
        super(message, cause);
    }
}
