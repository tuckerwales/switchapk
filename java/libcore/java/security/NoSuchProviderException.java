package java.security;

public class NoSuchProviderException extends GeneralSecurityException {
    public NoSuchProviderException() {
    }

    public NoSuchProviderException(String msg) {
        super(msg);
    }

    public NoSuchProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchProviderException(Throwable cause) {
        super(cause);
    }
}
