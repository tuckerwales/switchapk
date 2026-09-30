package java.lang;

public class ClassCircularityError extends LinkageError {
    public ClassCircularityError() {
        super();
    }

    public ClassCircularityError(String message) {
        super(message);
    }

    public ClassCircularityError(String message, Throwable cause) {
        super(message, cause);
    }

    public ClassCircularityError(Throwable cause) {
        super(cause);
    }
}
