package java.lang;

public class ClassCastException extends RuntimeException {
    public ClassCastException() {
        super();
    }

    public ClassCastException(String message) {
        super(message);
    }

    public ClassCastException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClassCastException(Throwable cause) {
        super(cause);
    }
}
