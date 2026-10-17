package java.lang;

public class VerifyError extends LinkageError {
    private static final long serialVersionUID = 7001962396098498785L;

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
