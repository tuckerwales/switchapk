package java.net;

public class InetAddress implements java.io.Serializable {
    private final String hostName;
    private final byte[] address;

    InetAddress(String hostName, byte[] address) {
        this.hostName = hostName;
        this.address = address;
    }

    public static InetAddress getByName(String host) throws UnknownHostException {
        if (host == null || host.isEmpty() || host.equals("localhost")) {
            return getLoopbackAddress();
        }
        String[] parts = host.split("\\.");
        if (parts.length == 4) {
            try {
                byte[] a = new byte[4];
                for (int i = 0; i < 4; i++) {
                    a[i] = (byte) Integer.parseInt(parts[i]);
                }
                return new Inet4Address(host, a);
            } catch (NumberFormatException e) {
            }
        }
        throw new UnknownHostException("Unable to resolve host \"" + host + "\": No address associated with hostname");
    }

    public static InetAddress[] getAllByName(String host) throws UnknownHostException {
        return new InetAddress[] {getByName(host)};
    }

    public static InetAddress getByAddress(byte[] addr) throws UnknownHostException {
        return new Inet4Address(null, addr.clone());
    }

    public static InetAddress getByAddress(String host, byte[] addr) throws UnknownHostException {
        return new Inet4Address(host, addr.clone());
    }

    public static InetAddress getLoopbackAddress() {
        return new Inet4Address("localhost", new byte[] {127, 0, 0, 1});
    }

    public static InetAddress getLocalHost() throws UnknownHostException {
        return getLoopbackAddress();
    }

    public String getHostName() {
        return hostName != null ? hostName : getHostAddress();
    }

    public String getCanonicalHostName() {
        return getHostName();
    }

    public byte[] getAddress() {
        return address.clone();
    }

    public String getHostAddress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < address.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(address[i] & 0xff);
        }
        return sb.toString();
    }

    public boolean isLoopbackAddress() {
        return address.length == 4 && address[0] == 127;
    }

    public boolean isAnyLocalAddress() {
        return false;
    }

    public boolean isReachable(int timeout) {
        return isLoopbackAddress();
    }

    public int hashCode() {
        return java.util.Arrays.hashCode(address);
    }

    public boolean equals(Object obj) {
        return obj instanceof InetAddress && java.util.Arrays.equals(address, ((InetAddress) obj).address);
    }

    public String toString() {
        return (hostName != null ? hostName : "") + "/" + getHostAddress();
    }
}
