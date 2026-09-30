package java.lang;

public class VerifyError extends LinkageError {
    public VerifyError() {
        super();
    }

    public VerifyError(String message) {
        super(message);
    }

    public VerifyError(String message, Throwable cause) {
        super(message, cause);
    }

    public VerifyError(Throwable cause) {
        super(cause);
    }
}
