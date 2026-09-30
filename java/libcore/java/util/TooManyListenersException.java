package java.util;

public class TooManyListenersException extends Exception {
    public TooManyListenersException() {
        super();
    }

    public TooManyListenersException(String message) {
        super(message);
    }
}
