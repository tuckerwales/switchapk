package java.net;

import java.io.IOException;
import libcore.io.Net;

public class DatagramSocket implements java.io.Closeable {
    private int fd = -1;
    private boolean bound, closed;
    private InetAddress connectedAddress;
    private int connectedPort = -1;
    private int soTimeout;
    private boolean broadcast;
    private final Object recvLock = new Object();

    public DatagramSocket() throws SocketException {
        this(new InetSocketAddress(0));
    }

    public DatagramSocket(SocketAddress bindaddr) throws SocketException {
        if (bindaddr != null) {
            try {
                bind(bindaddr);
            } finally {
                if (!bound) {
                    close();
                }
            }
        }
    }

    public DatagramSocket(int port) throws SocketException {
        this(port, null);
    }

    public DatagramSocket(int port, InetAddress laddr) throws SocketException {
        this(new InetSocketAddress(laddr, port));
    }

    private void ensureFd(boolean ipv6) throws SocketException {
        if (fd < 0) {
            fd = Net.socket(ipv6, false);
        }
    }

    public synchronized void bind(SocketAddress addr) throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        if (bound) {
            throw new SocketException("already bound");
        }
        if (addr == null) {
            addr = new InetSocketAddress(0);
        }
        if (!(addr instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type!");
        }
        InetSocketAddress isa = (InetSocketAddress) addr;
        if (isa.isUnresolved()) {
            throw new SocketException("Unresolved address");
        }
        InetAddress a = isa.getAddress();
        ensureFd(a instanceof Inet6Address);
        Net.bind(fd, a.isAnyLocalAddress() ? null : a.address, isa.getPort());
        bound = true;
    }

    public void connect(InetAddress address, int port) {
        if (address == null) {
            throw new IllegalArgumentException("Address can't be null");
        }
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("connect: " + port);
        }
        connectedAddress = address;
        connectedPort = port;
    }

    public void connect(SocketAddress addr) throws SocketException {
        if (!(addr instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }
        InetSocketAddress isa = (InetSocketAddress) addr;
        if (isa.isUnresolved()) {
            throw new SocketException("Unresolved address");
        }
        connect(isa.getAddress(), isa.getPort());
    }

    public void disconnect() {
        connectedAddress = null;
        connectedPort = -1;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isConnected() {
        return connectedAddress != null;
    }

    public InetAddress getInetAddress() {
        return connectedAddress;
    }

    public int getPort() {
        return connectedPort;
    }

    public SocketAddress getRemoteSocketAddress() {
        return isConnected() ? new InetSocketAddress(connectedAddress, connectedPort) : null;
    }

    public SocketAddress getLocalSocketAddress() {
        if (closed || !bound) {
            return null;
        }
        return new InetSocketAddress(getLocalAddress(), getLocalPort());
    }

    public void send(DatagramPacket p) throws IOException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        InetAddress addr;
        int port;
        synchronized (p) {
            addr = p.getAddress();
            port = p.getPort();
            if (connectedAddress != null) {
                if (addr == null) {
                    addr = connectedAddress;
                    port = connectedPort;
                } else if (!addr.equals(connectedAddress) || port != connectedPort) {
                    throw new IllegalArgumentException("connected address and packet address differ");
                }
            } else if (addr == null) {
                throw new IllegalArgumentException("Address not set");
            }
            if (!bound) {
                bind(new InetSocketAddress(0));
            }
            Net.sendto(fd, p.buf, p.offset, p.length, addr.address, port);
        }
    }

    public void receive(DatagramPacket p) throws IOException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        if (!bound) {
            bind(new InetSocketAddress(0));
        }
        byte[] peer = new byte[16];
        int[] info = new int[2];
        synchronized (recvLock) {
            for (;;) {
                int n = Net.recvfrom(fd, p.buf, p.offset, p.bufLength, soTimeout, peer, info);
                InetAddress from = InetAddress.make(null, Net.trim(peer, info));
                if (connectedAddress != null && (!from.equals(connectedAddress) || info[0] != connectedPort)) {
                    continue;
                }
                p.length = Math.min(n, p.bufLength);
                p.address = from;
                p.port = info[0];
                return;
            }
        }
    }

    public InetAddress getLocalAddress() {
        if (closed) {
            return null;
        }
        if (fd < 0) {
            return InetAddress.anyLocal();
        }
        byte[] a = new byte[16];
        int[] info = new int[2];
        try {
            Net.getsockname(fd, a, info);
            return InetAddress.make(null, Net.trim(a, info));
        } catch (SocketException e) {
            return InetAddress.anyLocal();
        }
    }

    public int getLocalPort() {
        if (closed) {
            return -1;
        }
        if (fd < 0) {
            return 0;
        }
        try {
            return Net.getsockname(fd, new byte[16], new int[2]);
        } catch (SocketException e) {
            return -1;
        }
    }

    public synchronized void setSoTimeout(int timeout) throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout < 0");
        }
        soTimeout = timeout;
    }

    public synchronized int getSoTimeout() throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        return soTimeout;
    }

    private void setOpt(int opt, int v) throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        ensureFd(false);
        Net.setOption(fd, opt, v);
    }

    private int getOpt(int opt) throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        ensureFd(false);
        return Net.getOption(fd, opt);
    }

    public synchronized void setSendBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("negative send size");
        }
        setOpt(SocketOptions.SO_SNDBUF, size);
    }

    public synchronized int getSendBufferSize() throws SocketException {
        return getOpt(SocketOptions.SO_SNDBUF);
    }

    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("invalid receive size");
        }
        setOpt(SocketOptions.SO_RCVBUF, size);
    }

    public synchronized int getReceiveBufferSize() throws SocketException {
        return getOpt(SocketOptions.SO_RCVBUF);
    }

    public synchronized void setReuseAddress(boolean on) throws SocketException {
        setOpt(SocketOptions.SO_REUSEADDR, on ? 1 : 0);
    }

    public synchronized boolean getReuseAddress() throws SocketException {
        return getOpt(SocketOptions.SO_REUSEADDR) != 0;
    }

    public synchronized void setBroadcast(boolean on) throws SocketException {
        setOpt(SocketOptions.SO_BROADCAST, on ? 1 : 0);
        broadcast = on;
    }

    public synchronized boolean getBroadcast() throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        return broadcast;
    }

    public synchronized void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("tc is not in range 0 -- 255");
        }
        setOpt(SocketOptions.IP_TOS, tc);
    }

    public synchronized int getTrafficClass() throws SocketException {
        return getOpt(SocketOptions.IP_TOS);
    }

    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        if (fd >= 0) {
            Net.close(fd);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public java.nio.channels.DatagramChannel getChannel() {
        return null;
    }
}
