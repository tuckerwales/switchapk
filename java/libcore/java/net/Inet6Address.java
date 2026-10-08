package java.net;

public final class Inet6Address extends InetAddress {
    Inet6Address(String hostName, byte[] addr) {
        super(hostName, addr);
    }

    public static Inet6Address getByAddress(String host, byte[] addr, int scope_id) throws UnknownHostException {
        if (addr == null || addr.length != 16) {
            throw new UnknownHostException("Not an IPv6 address");
        }
        return new Inet6Address(host, addr.clone());
    }

    public int getScopeId() {
        return 0;
    }

    public boolean isIPv4CompatibleAddress() {
        for (int i = 0; i < 12; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isMulticastAddress() {
        return (address[0] & 0xff) == 0xff;
    }
}
