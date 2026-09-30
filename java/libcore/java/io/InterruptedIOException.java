package java.io;

public class InterruptedIOException extends IOException {
    public InterruptedIOException() {
    }

    public InterruptedIOException(String message) {
        super(message);
    }
}
