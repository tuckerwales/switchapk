package java.util;

public class InvalidPropertiesFormatException extends java.io.IOException {
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
