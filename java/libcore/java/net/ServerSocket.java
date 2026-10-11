package java.net;

import java.io.IOException;
import libcore.io.Net;

public class ServerSocket implements java.io.Closeable {
    private int fd = -1;
    private boolean bound, closed;
    private int soTimeout;
    private boolean reuse = true;
    private int rcvbuf;

    public ServerSocket() throws IOException {
    }

    public ServerSocket(int port) throws IOException {
        this(port, 50, null);
    }

    public ServerSocket(int port, int backlog) throws IOException {
        this(port, backlog, null);
    }

    public ServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
        if (port < 0 || port > 0xffff) {
            throw new IllegalArgumentException("Port value out of range: " + port);
        }
        try {
            bind(new InetSocketAddress(bindAddr, port), backlog);
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    public void bind(SocketAddress endpoint) throws IOException {
        bind(endpoint, 50);
    }

    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        if (bound) {
            throw new SocketException("Already bound");
        }
        if (endpoint == null) {
            endpoint = new InetSocketAddress(0);
        }
        if (!(endpoint instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }
        InetSocketAddress isa = (InetSocketAddress) endpoint;
        if (isa.isUnresolved()) {
            throw new SocketException("Unresolved address");
        }
        InetAddress addr = isa.getAddress();
        fd = Net.socket(addr instanceof Inet6Address, true);
        Net.setOption(fd, SocketOptions.SO_REUSEADDR, reuse ? 1 : 0);
        if (rcvbuf > 0) {
            Net.setOption(fd, SocketOptions.SO_RCVBUF, rcvbuf);
        }
        Net.bind(fd, addr.isAnyLocalAddress() ? null : addr.address, isa.getPort());
        Net.listen(fd, backlog < 1 ? 50 : backlog);
        bound = true;
    }

    public InetAddress getInetAddress() {
        if (!bound) {
            return null;
        }
        byte[] a = new byte[16];
        int[] info = new int[2];
        try {
            Net.getsockname(fd, a, info);
            return InetAddress.make(null, Net.trim(a, info));
        } catch (SocketException e) {
            return null;
        }
    }

    public int getLocalPort() {
        if (!bound) {
            return -1;
        }
        try {
            return Net.getsockname(fd, new byte[16], new int[2]);
        } catch (SocketException e) {
            return -1;
        }
    }

    public SocketAddress getLocalSocketAddress() {
        return bound ? new InetSocketAddress(getInetAddress(), getLocalPort()) : null;
    }

    public Socket accept() throws IOException {
        Socket s = new Socket();
        implAccept(s);
        return s;
    }

    protected final void implAccept(Socket s) throws IOException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        if (!bound) {
            throw new SocketException("Socket is not bound yet");
        }
        byte[] peer = new byte[16];
        int[] info = new int[2];
        int c = Net.accept(fd, soTimeout, peer, info);
        if (closed) {
            Net.close(c);
            throw new SocketException("Socket closed");
        }
        s.accepted(c, InetAddress.make(null, Net.trim(peer, info)), info[0]);
    }

    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            return;
        }
        closeInternal$();
    }

    /** @hide */
    public void closeInternal$() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (fd >= 0) {
            Net.close(fd);
        }
    }

    private java.nio.channels.ServerSocketChannel channel;

    public java.nio.channels.ServerSocketChannel getChannel() {
        return channel;
    }

    /** @hide */
    public void setChannel$(java.nio.channels.ServerSocketChannel ch) {
        channel = ch;
    }

    /** @hide The listening descriptor, or -1 before bind. */
    public int fd$() {
        return fd;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isClosed() {
        return closed;
    }

    public synchronized void setSoTimeout(int timeout) throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout can't be negative");
        }
        soTimeout = timeout;
    }

    public synchronized int getSoTimeout() throws IOException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        return soTimeout;
    }

    public void setReuseAddress(boolean on) throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        reuse = on;
        if (fd >= 0) {
            Net.setOption(fd, SocketOptions.SO_REUSEADDR, on ? 1 : 0);
        }
    }

    public boolean getReuseAddress() throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
        return reuse;
    }

    public String toString() {
        if (!bound) {
            return "ServerSocket[unbound]";
        }
        return "ServerSocket[addr=" + getInetAddress() + ",localport=" + getLocalPort() + "]";
    }

    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("negative receive size");
        }
        rcvbuf = size;
        if (fd >= 0) {
            Net.setOption(fd, SocketOptions.SO_RCVBUF, size);
        }
    }

    public synchronized int getReceiveBufferSize() throws SocketException {
        return fd >= 0 ? Net.getOption(fd, SocketOptions.SO_RCVBUF) : (rcvbuf > 0 ? rcvbuf : 65536);
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    }
}
