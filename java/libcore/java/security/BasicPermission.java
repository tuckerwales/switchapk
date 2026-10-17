package java.security;

/** A named permission without actions. Android grants everything, so this only keeps Java's name matching. */
public abstract class BasicPermission extends Permission implements java.io.Serializable {
    private static final long serialVersionUID = 6279438298436773498L;

    public BasicPermission(String name) {
        super(name);
        if (name == null) {
            throw new NullPointerException("name can't be null");
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("name can't be empty");
        }
    }

    public BasicPermission(String name, String actions) {
        this(name);
    }

    public boolean implies(Permission p) {
        if (p == null || p.getClass() != getClass()) {
            return false;
        }
        String mine = getName();
        String theirs = p.getName();
        if (mine.equals("*")) {
            return true;
        }
        if (mine.endsWith(".*")) {
            return theirs.startsWith(mine.substring(0, mine.length() - 1));
        }
        return mine.equals(theirs);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        return getName().equals(((BasicPermission) obj).getName());
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public String getActions() {
        return "";
    }

    public PermissionCollection newPermissionCollection() {
        return null;
    }

    final String getCanonicalName() {
        return getName();
    }
}
