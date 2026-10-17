package java.util;

public class NoSuchElementException extends RuntimeException {
    private static final long serialVersionUID = 6769829250639411880L;

    public NoSuchElementException() {
        super();
    }

    public NoSuchElementException(String message) {
        super(message);
    }

    public NoSuchElementException(Throwable cause) {
        super(cause);
    }

    public NoSuchElementException(String message, Throwable cause) {
        super(message, cause);
    }
}
