package java.net;

public final class Inet4Address extends InetAddress {
    Inet4Address(String hostName, byte[] addr) {
        super(hostName, addr);
    }

    public boolean isMulticastAddress() {
        return (address[0] & 0xf0) == 0xe0;
    }
}
