package java.security;

public class AccessControlException extends SecurityException {
    private final Permission perm;

    public AccessControlException(String s) {
        this(s, null);
    }

    public AccessControlException(String s, Permission p) {
        super(s);
        perm = p;
    }

    public Permission getPermission() {
        return perm;
    }
}
