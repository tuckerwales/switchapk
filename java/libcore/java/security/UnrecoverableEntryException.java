package java.security;

public class UnrecoverableEntryException extends GeneralSecurityException {
    public UnrecoverableEntryException() {
    }

    public UnrecoverableEntryException(String msg) {
        super(msg);
    }
}
