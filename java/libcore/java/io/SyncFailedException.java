package java.io;

public class SyncFailedException extends IOException {
    public SyncFailedException() {
    }

    public SyncFailedException(String message) {
        super(message);
    }
}
