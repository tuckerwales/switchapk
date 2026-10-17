package java.lang;

public class OutOfMemoryError extends VirtualMachineError {
    private static final long serialVersionUID = 8228564086184010517L;

    public OutOfMemoryError() {
        super();
    }

    public OutOfMemoryError(String message) {
        super(message);
    }

    public OutOfMemoryError(String message, Throwable cause) {
        super(message, cause);
    }

    public OutOfMemoryError(Throwable cause) {
        super(cause);
    }
}
