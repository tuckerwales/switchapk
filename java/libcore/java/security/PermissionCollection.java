package java.security;

import java.util.Enumeration;

/** A set of permissions. Android does not enforce permissions, so this only keeps the bookkeeping. */
public abstract class PermissionCollection implements java.io.Serializable {
    private static final long serialVersionUID = -6727011328946861783L;

    private volatile boolean readOnly;

    public PermissionCollection() {
    }

    public abstract void add(Permission permission);

    public abstract boolean implies(Permission permission);

    public abstract Enumeration<Permission> elements();

    public void setReadOnly() {
        readOnly = true;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
