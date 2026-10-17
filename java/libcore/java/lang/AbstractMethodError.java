package java.lang;

public class AbstractMethodError extends IncompatibleClassChangeError {
    private static final long serialVersionUID = -1654391082989018462L;

    public AbstractMethodError() {
        super();
    }

    public AbstractMethodError(String message) {
        super(message);
    }

    public AbstractMethodError(String message, Throwable cause) {
        super(message, cause);
    }

    public AbstractMethodError(Throwable cause) {
        super(cause);
    }
}
