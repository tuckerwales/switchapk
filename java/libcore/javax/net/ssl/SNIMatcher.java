package javax.net.ssl;

public abstract class SNIMatcher {
    private final int type;

    protected SNIMatcher(int type) {
        if (type < 0 || type > 255) throw new IllegalArgumentException("Server name type is not in range [0, 255]");
        this.type = type;
    }

    public final int getType() {
        return type;
    }

    public abstract boolean matches(SNIServerName serverName);
}
