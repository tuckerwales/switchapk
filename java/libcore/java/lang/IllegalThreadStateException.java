package java.lang;

public class IllegalThreadStateException extends IllegalArgumentException {
    private static final long serialVersionUID = -7626246362397460174L;

    public IllegalThreadStateException() {
        super();
    }

    public IllegalThreadStateException(String message) {
        super(message);
    }

    public IllegalThreadStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalThreadStateException(Throwable cause) {
        super(cause);
    }
}
