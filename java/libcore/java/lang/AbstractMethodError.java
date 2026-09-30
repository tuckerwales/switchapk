package java.lang;

public class AbstractMethodError extends IncompatibleClassChangeError {
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
