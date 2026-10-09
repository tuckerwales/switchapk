package java.security;

public class KeyManagementException extends KeyException {
    public KeyManagementException() {
    }

    public KeyManagementException(String msg) {
        super(msg);
    }

    public KeyManagementException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeyManagementException(Throwable cause) {
        super(cause);
    }
}
