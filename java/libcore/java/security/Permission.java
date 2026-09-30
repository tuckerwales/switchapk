package java.security;

public abstract class Permission implements java.io.Serializable {
    private final String name;

    public Permission(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public abstract String getActions();

    public boolean implies(Permission permission) {
        return true;
    }
}
