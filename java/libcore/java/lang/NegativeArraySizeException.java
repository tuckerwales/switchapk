package java.lang;

public class NegativeArraySizeException extends RuntimeException {
    private static final long serialVersionUID = -8960118058596991861L;

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
