package java.lang;

public class ClassCircularityError extends LinkageError {
    private static final long serialVersionUID = 1054362542914539689L;

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
