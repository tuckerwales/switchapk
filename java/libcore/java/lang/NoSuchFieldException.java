package java.lang;

public class NoSuchFieldException extends ReflectiveOperationException {
    public NoSuchFieldException() {
        super();
    }

    public NoSuchFieldException(String message) {
        super(message);
    }

    public NoSuchFieldException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchFieldException(Throwable cause) {
        super(cause);
    }
}
