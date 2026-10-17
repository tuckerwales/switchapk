package java.lang;

public class ArithmeticException extends RuntimeException {
    private static final long serialVersionUID = 2256477558314496007L;

    public ArithmeticException() {
        super();
    }

    public ArithmeticException(String message) {
        super(message);
    }

    public ArithmeticException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArithmeticException(Throwable cause) {
        super(cause);
    }
}
