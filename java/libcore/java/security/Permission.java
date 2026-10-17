package java.security;

public abstract class Permission implements Guard, java.io.Serializable {
    private static final long serialVersionUID = -5636570222231596674L;

    private final String name;

    public Permission(String name) {
        this.name = name;
    }

    /** Android does not run a security manager, so every guard check passes. */
    public void checkGuard(Object object) throws SecurityException {
    }

    public abstract boolean implies(Permission permission);

    public final String getName() {
        return name;
    }

    public abstract String getActions();

    public PermissionCollection newPermissionCollection() {
        return null;
    }
}
