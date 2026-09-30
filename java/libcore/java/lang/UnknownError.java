package java.lang;

public class UnknownError extends VirtualMachineError {
    public UnknownError() {
        super();
    }

    public UnknownError(String message) {
        super(message);
    }

    public UnknownError(String message, Throwable cause) {
        super(message, cause);
    }

    public UnknownError(Throwable cause) {
        super(cause);
    }
}
