package java.lang;

public class IllegalMonitorStateException extends RuntimeException {
    private static final long serialVersionUID = 3713306369498869069L;

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
