package java.lang;

public class UnsupportedClassVersionError extends ClassFormatError {
    private static final long serialVersionUID = -7123279212883497373L;

    public UnsupportedClassVersionError() {
        super();
    }

    public UnsupportedClassVersionError(String message) {
        super(message);
    }

    public UnsupportedClassVersionError(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedClassVersionError(Throwable cause) {
        super(cause);
    }
}
