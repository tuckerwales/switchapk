package java.lang;

public class ArrayIndexOutOfBoundsException extends IndexOutOfBoundsException {
    public ArrayIndexOutOfBoundsException() {
        super();
    }

    public ArrayIndexOutOfBoundsException(String message) {
        super(message);
    }

    public ArrayIndexOutOfBoundsException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArrayIndexOutOfBoundsException(int index) {
        super("Array index out of range: " + index);
    }

    public ArrayIndexOutOfBoundsException(Throwable cause) {
        super(cause);
    }
}
