package java.lang;

public class NullPointerException extends RuntimeException {
    public NullPointerException() {
        super();
    }

    public NullPointerException(String message) {
        super(message);
    }

    public NullPointerException(String message, Throwable cause) {
        super(message, cause);
    }

    public NullPointerException(Throwable cause) {
        super(cause);
    }
}
