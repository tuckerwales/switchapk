package java.net;

public class Proxy {
    public enum Type {
        DIRECT, HTTP, SOCKS
    }

    public static final Proxy NO_PROXY = new Proxy();

    private Proxy() {
    }

    public Type type() {
        return Type.DIRECT;
    }
}
