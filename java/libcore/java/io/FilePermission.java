package java.io;

import java.security.Permission;

/**
 * Legacy security permission. Android has no security manager, so like
 * AOSP's version this grants everything and reports no actions.
 */
public final class FilePermission extends Permission implements Serializable {
    private static final long serialVersionUID = 7930732926638008763L;

    public FilePermission(String path, String actions) {
        super(path);
    }

    public boolean implies(Permission p) {
        return true;
    }

    public String getActions() {
        return null;
    }
}
