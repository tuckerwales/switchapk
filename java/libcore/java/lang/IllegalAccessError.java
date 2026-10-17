package java.lang;

public class IllegalAccessError extends IncompatibleClassChangeError {
    private static final long serialVersionUID = -8988904074992417891L;

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
