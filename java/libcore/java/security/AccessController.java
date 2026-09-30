package java.security;

public final class AccessController {
    private AccessController() {
    }

    public static <T> T doPrivileged(PrivilegedAction<T> action) {
        return action.run();
    }

    public static <T> T doPrivileged(PrivilegedExceptionAction<T> action) throws PrivilegedActionException {
        try {
            return action.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PrivilegedActionException(e);
        }
    }
}
