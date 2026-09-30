package java.io;

public class WriteAbortedException extends ObjectStreamException {
    public WriteAbortedException() {
    }

    public WriteAbortedException(String message) {
        super(message);
    }
}
