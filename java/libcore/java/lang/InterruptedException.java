package java.lang;

public class InterruptedException extends Exception {
    private static final long serialVersionUID = 6700697376100628473L;

    public InterruptedException() {
        super();
    }

    public InterruptedException(String message) {
        super(message);
    }

    public InterruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    public InterruptedException(Throwable cause) {
        super(cause);
    }
}
