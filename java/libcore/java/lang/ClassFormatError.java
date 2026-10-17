package java.lang;

public class ClassFormatError extends LinkageError {
    private static final long serialVersionUID = -8420114879011949195L;

    public ClassFormatError() {
        super();
    }

    public ClassFormatError(String message) {
        super(message);
    }

    public ClassFormatError(String message, Throwable cause) {
        super(message, cause);
    }

    public ClassFormatError(Throwable cause) {
        super(cause);
    }
}
