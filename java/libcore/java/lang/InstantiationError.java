package java.lang;

public class InstantiationError extends IncompatibleClassChangeError {
    public InstantiationError() {
        super();
    }

    public InstantiationError(String message) {
        super(message);
    }

    public InstantiationError(String message, Throwable cause) {
        super(message, cause);
    }

    public InstantiationError(Throwable cause) {
        super(cause);
    }
}
