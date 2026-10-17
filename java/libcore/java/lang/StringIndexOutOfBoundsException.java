package java.lang;

public class StringIndexOutOfBoundsException extends IndexOutOfBoundsException {
    private static final long serialVersionUID = -6762910422159637258L;

    public StringIndexOutOfBoundsException() {
        super();
    }

    public StringIndexOutOfBoundsException(String message) {
        super(message);
    }

    public StringIndexOutOfBoundsException(String message, Throwable cause) {
        super(message, cause);
    }

    public StringIndexOutOfBoundsException(int index) {
        super("String index out of range: " + index);
    }

    public StringIndexOutOfBoundsException(Throwable cause) {
        super(cause);
    }
}
