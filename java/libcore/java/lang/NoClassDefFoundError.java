package java.lang;

public class NoClassDefFoundError extends LinkageError {
    public NoClassDefFoundError() {
        super();
    }

    public NoClassDefFoundError(String message) {
        super(message);
    }

    public NoClassDefFoundError(String message, Throwable cause) {
        super(message, cause);
    }

    public NoClassDefFoundError(Throwable cause) {
        super(cause);
    }
}
