package java.lang;

public class UnsatisfiedLinkError extends LinkageError {
    private static final long serialVersionUID = -4019343241616879428L;

    public UnsatisfiedLinkError() {
        super();
    }

    public UnsatisfiedLinkError(String message) {
        super(message);
    }

    public UnsatisfiedLinkError(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsatisfiedLinkError(Throwable cause) {
        super(cause);
    }
}
