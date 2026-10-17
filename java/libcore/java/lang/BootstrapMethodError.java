package java.lang;

public class BootstrapMethodError extends LinkageError {
    private static final long serialVersionUID = 292L;

    public BootstrapMethodError() {
        super();
    }

    public BootstrapMethodError(String message) {
        super(message);
    }

    public BootstrapMethodError(String message, Throwable cause) {
        super(message, cause);
    }

    public BootstrapMethodError(Throwable cause) {
        super(cause);
    }
}
