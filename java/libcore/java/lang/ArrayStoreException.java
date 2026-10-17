package java.lang;

public class ArrayStoreException extends RuntimeException {
    private static final long serialVersionUID = -4522193890499838241L;

    public ArrayStoreException() {
        super();
    }

    public ArrayStoreException(String message) {
        super(message);
    }

    public ArrayStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public ArrayStoreException(Throwable cause) {
        super(cause);
    }
}
