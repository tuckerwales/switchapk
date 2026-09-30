package java.lang;

public class UnsupportedClassVersionError extends ClassFormatError {
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
