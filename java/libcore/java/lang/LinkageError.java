package java.lang;

public class LinkageError extends Error {
    public LinkageError() {
        super();
    }

    public LinkageError(String message) {
        super(message);
    }

    public LinkageError(String message, Throwable cause) {
        super(message, cause);
    }

    public LinkageError(Throwable cause) {
        super(cause);
    }
}
