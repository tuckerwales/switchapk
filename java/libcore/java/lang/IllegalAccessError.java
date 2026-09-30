package java.lang;

public class IllegalAccessError extends IncompatibleClassChangeError {
    public IllegalAccessError() {
        super();
    }

    public IllegalAccessError(String message) {
        super(message);
    }

    public IllegalAccessError(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalAccessError(Throwable cause) {
        super(cause);
    }
}
