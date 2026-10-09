package java.lang;

public class ClassNotFoundException extends ReflectiveOperationException {
    public ClassNotFoundException() {
        super();
    }

    public ClassNotFoundException(String message) {
        super(message);
    }

    public ClassNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClassNotFoundException(Throwable cause) {
        super(cause);
    }

    public Throwable getException() {
        return getCause();
    }
}
