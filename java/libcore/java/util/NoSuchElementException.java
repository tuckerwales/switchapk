package java.util;

public class NoSuchElementException extends RuntimeException {
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
