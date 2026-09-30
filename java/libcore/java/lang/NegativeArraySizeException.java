package java.lang;

public class NegativeArraySizeException extends RuntimeException {
    public NegativeArraySizeException() {
        super();
    }

    public NegativeArraySizeException(String message) {
        super(message);
    }

    public NegativeArraySizeException(String message, Throwable cause) {
        super(message, cause);
    }

    public NegativeArraySizeException(Throwable cause) {
        super(cause);
    }
}
