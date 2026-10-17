package java.lang;

public class NoSuchMethodError extends IncompatibleClassChangeError {
    private static final long serialVersionUID = -3765521442372831335L;

    public NoSuchMethodError() {
        super();
    }

    public NoSuchMethodError(String message) {
        super(message);
    }

    public NoSuchMethodError(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchMethodError(Throwable cause) {
        super(cause);
    }
}
