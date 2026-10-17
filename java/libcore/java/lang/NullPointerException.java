package java.lang;

public class NullPointerException extends RuntimeException {
    private static final long serialVersionUID = 5162710183389028792L;

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
