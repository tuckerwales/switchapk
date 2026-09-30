package java.security;

public class PrivilegedActionException extends Exception {
    public PrivilegedActionException(Exception exception) {
        super(null, exception);
    }

    public Exception getException() {
        return (Exception) getCause();
    }
}
