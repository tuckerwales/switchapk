package java.lang;

public class IllegalAccessException extends ReflectiveOperationException {
    private static final long serialVersionUID = 6616958222490762034L;

    public IllegalAccessException() {
        super();
    }

    public IllegalAccessException(String message) {
        super(message);
    }

    public IllegalAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalAccessException(Throwable cause) {
        super(cause);
    }
}
