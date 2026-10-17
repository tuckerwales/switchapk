package java.lang;

public class InstantiationError extends IncompatibleClassChangeError {
    private static final long serialVersionUID = -4885810657349421204L;

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
