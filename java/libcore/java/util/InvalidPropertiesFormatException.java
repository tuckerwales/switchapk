package java.util;

public class InvalidPropertiesFormatException extends java.io.IOException {
    private static final long serialVersionUID = 7763056076009360219L;

    public InvalidPropertiesFormatException() {
        super();
    }

    public InvalidPropertiesFormatException(String message) {
        super(message);
    }

    public InvalidPropertiesFormatException(Throwable cause) {
        super(cause == null ? null : cause.toString());
        this.initCause(cause);
    }
}
