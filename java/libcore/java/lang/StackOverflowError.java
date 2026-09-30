package java.lang;

public class StackOverflowError extends VirtualMachineError {
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
