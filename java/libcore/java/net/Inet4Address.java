package java.net;

public final class Inet4Address extends InetAddress {
    private static final long serialVersionUID = 3286316764910316507L;

    Inet4Address(String hostName, byte[] addr) {
        super(hostName, addr);
    }

    public boolean isMulticastAddress() {
        return (address[0] & 0xf0) == 0xe0;
    }
}
