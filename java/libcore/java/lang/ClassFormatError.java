package java.lang;

public class ClassFormatError extends LinkageError {
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
