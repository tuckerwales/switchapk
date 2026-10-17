package java.lang;

public class IllegalStateException extends RuntimeException {
    private static final long serialVersionUID = -1848914673093119416L;

    public IllegalStateException() {
        super();
    }

    public IllegalStateException(String message) {
        super(message);
    }

    public IllegalStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalStateException(Throwable cause) {
        super(cause);
    }
}
