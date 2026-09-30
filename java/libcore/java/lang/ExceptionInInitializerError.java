package java.lang;

public class ExceptionInInitializerError extends LinkageError {
    public ExceptionInInitializerError() {
        super();
    }

    public ExceptionInInitializerError(String message) {
        super(message);
    }

    public ExceptionInInitializerError(String message, Throwable cause) {
        super(message, cause);
    }

    public ExceptionInInitializerError(Throwable cause) {
        super(null, cause);
    }

    public Throwable getException() {
        return getCause();
    }
}
