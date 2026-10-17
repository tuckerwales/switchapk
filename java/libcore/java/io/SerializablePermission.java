package java.io;

import java.security.BasicPermission;

public final class SerializablePermission extends BasicPermission {
    private static final long serialVersionUID = 8537212141160296410L;

    public SerializablePermission(String name) {
        super(name);
    }

    public SerializablePermission(String name, String actions) {
        super(name, actions);
    }
}
