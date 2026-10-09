package java.security;

public class UnrecoverableKeyException extends UnrecoverableEntryException {
    public UnrecoverableKeyException() {
    }

    public UnrecoverableKeyException(String msg) {
        super(msg);
    }
}
