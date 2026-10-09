package android.security.keystore;

public class StrongBoxUnavailableException extends java.security.ProviderException {
    public StrongBoxUnavailableException() {
        super();
    }

    public StrongBoxUnavailableException(String message) {
        super(message);
    }

    public StrongBoxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public StrongBoxUnavailableException(Throwable cause) {
        super(cause);
    }
}
