package java.lang;

public class ExceptionInInitializerError extends LinkageError {
    private static final long serialVersionUID = 1521711792217232256L;

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
