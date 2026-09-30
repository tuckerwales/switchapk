package java.lang;

public class NoSuchMethodException extends ReflectiveOperationException {
    public NoSuchMethodException() {
        super();
    }

    public NoSuchMethodException(String message) {
        super(message);
    }

    public NoSuchMethodException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchMethodException(Throwable cause) {
        super(cause);
    }
}
