package android.content;

public class OperationApplicationException extends Exception {
    public OperationApplicationException() {}
    public OperationApplicationException(String message) { super(message); }
    public OperationApplicationException(String message, Throwable cause) { super(message, cause); }
    public OperationApplicationException(Throwable cause) { super(cause); }
    public int getNumSuccessfulYieldPoints() { return 0; }
}
