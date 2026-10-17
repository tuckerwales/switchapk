package java.security;

public class PrivilegedActionException extends Exception {
    private static final long serialVersionUID = 4724086851538908602L;

    public PrivilegedActionException(Exception exception) {
        super(null, exception);
    }

    public Exception getException() {
        return (Exception) getCause();
    }
}
