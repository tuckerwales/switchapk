package java.net;

import libcore.io.Net;

public class InetAddress implements java.io.Serializable {
    private String hostName;
    final byte[] address;

    InetAddress(String hostName, byte[] address) {
        this.hostName = hostName;
        this.address = address;
    }

    static InetAddress make(String host, byte[] addr) {
        if (addr.length == 16 && isMapped(addr)) {
            byte[] v4 = new byte[4];
            System.arraycopy(addr, 12, v4, 0, 4);
            return new Inet4Address(host, v4);
        }
        return addr.length == 16 ? new Inet6Address(host, addr) : new Inet4Address(host, addr);
    }

    private static boolean isMapped(byte[] a) {
        for (int i = 0; i < 10; i++) {
            if (a[i] != 0) {
                return false;
            }
        }
        return a[10] == (byte) 0xff && a[11] == (byte) 0xff;
    }

    static InetAddress anyLocal() {
        return new Inet4Address("0.0.0.0", new byte[4]);
    }

    /** Framework-internal: the host name given at creation, without a reverse lookup. */
    String holderName() {
        return hostName;
    }

    /** Parses a dotted IPv4 literal (four parts), or returns null. */
    static byte[] parseIPv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] a = new byte[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            int v = 0;
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                v = v * 10 + (c - '0');
            }
            if (v > 255) {
                return null;
            }
            a[i] = (byte) v;
        }
        return a;
    }

    private static boolean isNumeric(String host) {
        return parseIPv4(host) != null || host.indexOf(':') >= 0;
    }

    private static String stripBrackets(String host) {
        if (host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    public static InetAddress getByName(String host) throws UnknownHostException {
        return getAllByName(host)[0];
    }

    public static InetAddress[] getAllByName(String host) throws UnknownHostException {
        if (host == null || host.isEmpty()) {
            return new InetAddress[] {getLoopbackAddress()};
        }
        host = stripBrackets(host);
        byte[] v4 = parseIPv4(host);
        if (v4 != null) {
            return new InetAddress[] {new Inet4Address(null, v4)};
        }
        boolean numeric = isNumeric(host);
        byte[][] addrs = Net.split(Net.getaddrinfo(host));
        if (addrs.length == 0) {
            throw new UnknownHostException("Unable to resolve host \"" + host + "\": No address associated with hostname");
        }
        InetAddress[] out = new InetAddress[addrs.length];
        for (int i = 0; i < addrs.length; i++) {
            out[i] = make(numeric ? null : host, addrs[i]);
        }
        return out;
    }

    public static InetAddress getByAddress(byte[] addr) throws UnknownHostException {
        return getByAddress(null, addr);
    }

    public static InetAddress getByAddress(String host, byte[] addr) throws UnknownHostException {
        if (addr == null || (addr.length != 4 && addr.length != 16)) {
            throw new UnknownHostException("addr is of illegal length");
        }
        return make(host, addr.clone());
    }

    public static InetAddress getLoopbackAddress() {
        return new Inet4Address("localhost", new byte[] {127, 0, 0, 1});
    }

    public static InetAddress getLocalHost() throws UnknownHostException {
        return getLoopbackAddress();
    }

    public String getHostName() {
        if (hostName == null) {
            String name = Net.getnameinfo(address);
            hostName = name != null ? name : getHostAddress();
        }
        return hostName;
    }

    public String getCanonicalHostName() {
        String name = Net.getnameinfo(address);
        return name != null ? name : getHostAddress();
    }

    public byte[] getAddress() {
        return address.clone();
    }

    public String getHostAddress() {
        if (address.length == 4) {
            return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff);
        }
        // RFC 5952: lower-case hex, longest run of two or more zero groups collapsed to "::"
        int[] g = new int[8];
        for (int i = 0; i < 8; i++) {
            g[i] = ((address[2 * i] & 0xff) << 8) | (address[2 * i + 1] & 0xff);
        }
        int bestStart = -1, bestLen = 0;
        for (int i = 0; i < 8;) {
            if (g[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && g[j] == 0) {
                j++;
            }
            if (j - i > bestLen && j - i >= 2) {
                bestStart = i;
                bestLen = j - i;
            }
            i = j;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen - 1;
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(g[i]));
        }
        return sb.toString();
    }

    public boolean isLoopbackAddress() {
        if (address.length == 4) {
            return address[0] == 127;
        }
        for (int i = 0; i < 15; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return address[15] == 1;
    }

    public boolean isAnyLocalAddress() {
        for (byte b : address) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public boolean isMulticastAddress() {
        return false;
    }

    public boolean isLinkLocalAddress() {
        if (address.length == 4) {
            return (address[0] & 0xff) == 169 && (address[1] & 0xff) == 254;
        }
        return (address[0] & 0xff) == 0xfe && (address[1] & 0xc0) == 0x80;
    }

    public boolean isSiteLocalAddress() {
        if (address.length == 4) {
            int a = address[0] & 0xff, b = address[1] & 0xff;
            return a == 10 || (a == 172 && (b & 0xf0) == 16) || (a == 192 && b == 168);
        }
        return (address[0] & 0xff) == 0xfe && (address[1] & 0xc0) == 0xc0;
    }

    public boolean isMCGlobal() {
        return false;
    }

    public boolean isMCNodeLocal() {
        return false;
    }

    public boolean isMCLinkLocal() {
        return false;
    }

    public boolean isMCSiteLocal() {
        return false;
    }

    public boolean isMCOrgLocal() {
        return false;
    }

    public boolean isReachable(int timeout) throws java.io.IOException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout < 0");
        }
        if (isLoopbackAddress()) {
            return true;
        }
        // No ICMP without privileges; like Android, fall back to a TCP connect to the echo port.
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(this, 7), timeout);
            return true;
        } catch (ConnectException e) {
            return true; // refused means the host answered
        } catch (java.io.IOException e) {
            return false;
        } finally {
            s.close();
        }
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
