package java.lang;

public class NoClassDefFoundError extends LinkageError {
    private static final long serialVersionUID = 9095859863287012458L;

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
