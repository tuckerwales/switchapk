package java.lang;

public class StackOverflowError extends VirtualMachineError {
    private static final long serialVersionUID = 8609175038441759607L;

    public StackOverflowError() {
        super();
    }

    public StackOverflowError(String message) {
        super(message);
    }

    public StackOverflowError(String message, Throwable cause) {
        super(message, cause);
    }

    public StackOverflowError(Throwable cause) {
        super(cause);
    }
}
