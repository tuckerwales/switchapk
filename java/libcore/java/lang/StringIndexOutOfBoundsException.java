package java.lang;

public class StringIndexOutOfBoundsException extends IndexOutOfBoundsException {
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
