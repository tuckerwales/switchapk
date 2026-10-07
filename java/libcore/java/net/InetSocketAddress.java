package java.net;

public class InetSocketAddress extends SocketAddress {
    private final String hostname;
    private final InetAddress addr;
    private final int port;

    public InetSocketAddress(int port) {
        this((InetAddress) null, port);
    }

    public InetSocketAddress(InetAddress addr, int port) {
        this.port = checkPort(port);
        this.addr = addr == null ? InetAddress.anyLocal() : addr;
        this.hostname = null;
    }

    public InetSocketAddress(String hostname, int port) {
        if (hostname == null) {
            throw new IllegalArgumentException("hostname can't be null");
        }
        this.port = checkPort(port);
        InetAddress a;
        try {
            a = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            a = null;
        }
        this.addr = a;
        this.hostname = hostname;
    }

    private InetSocketAddress(String hostname, int port, boolean unresolved) {
        this.port = checkPort(port);
        this.addr = null;
        this.hostname = hostname;
    }

    public static InetSocketAddress createUnresolved(String host, int port) {
        if (host == null) {
            throw new IllegalArgumentException("hostname can't be null");
        }
        return new InetSocketAddress(host, port, true);
    }

    private static int checkPort(int port) {
        if (port < 0 || port > 0xffff) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
        return port;
    }

    public final int getPort() {
        return port;
    }

    public final InetAddress getAddress() {
        return addr;
    }

    public final String getHostName() {
        if (hostname != null) {
            return hostname;
        }
        return addr != null ? addr.getHostName() : null;
    }

    public final String getHostString() {
        if (hostname != null) {
            return hostname;
        }
        return addr.holderName() != null ? addr.holderName() : addr.getHostAddress();
    }

    public final boolean isUnresolved() {
        return addr == null;
    }

    public String toString() {
        if (isUnresolved()) {
            return hostname + "/<unresolved>:" + port;
        }
        return addr.toString() + ":" + port;
    }

    public final boolean equals(Object obj) {
        if (!(obj instanceof InetSocketAddress)) {
            return false;
        }
        InetSocketAddress o = (InetSocketAddress) obj;
        if (port != o.port) {
            return false;
        }
        if (addr != null) {
            return addr.equals(o.addr);
        }
        return o.addr == null && hostname.equalsIgnoreCase(o.hostname);
    }

    public final int hashCode() {
        return (addr != null ? addr.hashCode() : hostname.toLowerCase().hashCode()) + port;
    }
}
