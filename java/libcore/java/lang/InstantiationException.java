package java.lang;

public class InstantiationException extends ReflectiveOperationException {
    public InstantiationException() {
        super();
    }

    public InstantiationException(String message) {
        super(message);
    }

    public InstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InstantiationException(Throwable cause) {
        super(cause);
    }
}
