package java.lang;

public class IncompatibleClassChangeError extends LinkageError {
    private static final long serialVersionUID = -4914975503642802119L;

    public IncompatibleClassChangeError() {
        super();
    }

    public IncompatibleClassChangeError(String message) {
        super(message);
    }

    public IncompatibleClassChangeError(String message, Throwable cause) {
        super(message, cause);
    }

    public IncompatibleClassChangeError(Throwable cause) {
        super(cause);
    }
}
