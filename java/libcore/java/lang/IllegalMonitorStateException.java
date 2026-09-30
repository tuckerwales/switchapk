package java.lang;

public class IllegalMonitorStateException extends RuntimeException {
    public IllegalMonitorStateException() {
        super();
    }

    public IllegalMonitorStateException(String message) {
        super(message);
    }

    public IllegalMonitorStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalMonitorStateException(Throwable cause) {
        super(cause);
    }
}
