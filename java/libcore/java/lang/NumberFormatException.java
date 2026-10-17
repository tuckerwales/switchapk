package java.lang;

public class NumberFormatException extends IllegalArgumentException {
    private static final long serialVersionUID = -2848938806368998894L;

    public NumberFormatException() {
        super();
    }

    public NumberFormatException(String message) {
        super(message);
    }

    public NumberFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public NumberFormatException(Throwable cause) {
        super(cause);
    }
}
