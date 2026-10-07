package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import libcore.io.Net;

public class Socket implements java.io.Closeable {
    int fd = -1;
    private final Object lock = new Object();
    private InetAddress remoteAddr;
    private int remotePort;
    private boolean bound, connected, closed, shutIn, shutOut;
    int soTimeout;
    // options set before the descriptor exists (it is created on bind or connect, once the family is known)
    private final Map<Integer, Integer> pending = new LinkedHashMap<Integer, Integer>();
    private InputStream in;
    private OutputStream out;

    public Socket() {
    }

    public Socket(Proxy proxy) {
        if (proxy == null || proxy.type() != Proxy.Type.DIRECT) {
            throw new IllegalArgumentException("Invalid Proxy");
        }
    }

    public Socket(String host, int port) throws UnknownHostException, IOException {
        this(InetAddress.getAllByName(host), port, null, 0);
    }

    public Socket(InetAddress address, int port) throws IOException {
        this(new InetAddress[] {address}, port, null, 0);
    }

    public Socket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
        this(InetAddress.getAllByName(host), port, localAddr, localPort);
    }

    public Socket(InetAddress address, int port, InetAddress localAddr, int localPort) throws IOException {
        this(new InetAddress[] {address}, port, localAddr, localPort);
    }

    private Socket(InetAddress[] addrs, int port, InetAddress localAddr, int localPort) throws IOException {
        if (addrs[0] == null) {
            throw new NullPointerException("address == null");
        }
        IOException last = null;
        for (InetAddress a : addrs) {
            try {
                if (localAddr != null || localPort != 0) {
                    bind(new InetSocketAddress(localAddr, localPort));
                }
                connect(new InetSocketAddress(a, port));
                return;
            } catch (IOException e) {
                last = e;
                closeFd();
            }
        }
        close();
        throw last;
    }

    /** Framework-internal: wraps an accepted descriptor. */
    void accepted(int fd, InetAddress peer, int port) {
        this.fd = fd;
        remoteAddr = peer;
        remotePort = port;
        bound = connected = true;
    }

    private void closeFd() {
        synchronized (lock) {
            if (fd >= 0) {
                Net.close(fd);
                fd = -1;
            }
            bound = connected = false;
        }
    }

    private void ensureFd(boolean ipv6) throws SocketException {
        if (fd >= 0) {
            return;
        }
        fd = Net.socket(ipv6, true);
        for (Map.Entry<Integer, Integer> e : pending.entrySet()) {
            Net.setOption(fd, e.getKey(), e.getValue());
        }
    }

    private void checkOpen() throws SocketException {
        if (closed) {
            throw new SocketException("Socket is closed");
        }
    }

    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (endpoint == null) {
            throw new IllegalArgumentException("connect: The address can't be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("connect: timeout can't be negative");
        }
        checkOpen();
        if (connected) {
            throw new SocketException("already connected");
        }
        if (!(endpoint instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }
        InetSocketAddress isa = (InetSocketAddress) endpoint;
        InetAddress addr = isa.getAddress();
        if (addr == null) {
            throw new UnknownHostException(isa.getHostName());
        }
        if (addr.isAnyLocalAddress()) {
            addr = InetAddress.getLoopbackAddress();
        }
        ensureFd(addr instanceof Inet6Address);
        Net.connect(fd, addr.address, isa.getPort(), timeout);
        remoteAddr = addr;
        remotePort = isa.getPort();
        bound = connected = true;
    }

    public void bind(SocketAddress bindpoint) throws IOException {
        checkOpen();
        if (bound) {
            throw new SocketException("Already bound");
        }
        if (bindpoint != null && !(bindpoint instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }
        InetSocketAddress isa = (InetSocketAddress) bindpoint;
        InetAddress addr = isa != null ? isa.getAddress() : null;
        ensureFd(addr instanceof Inet6Address);
        Net.bind(fd, addr != null && !addr.isAnyLocalAddress() ? addr.address : null, isa != null ? isa.getPort() : 0);
        bound = true;
    }

    public InetAddress getInetAddress() {
        return connected ? remoteAddr : null;
    }

    public InetAddress getLocalAddress() {
        if (closed || fd < 0) {
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

    public int getPort() {
        return connected ? remotePort : 0;
    }

    public int getLocalPort() {
        if (!bound || fd < 0) {
            return -1;
        }
        try {
            return Net.getsockname(fd, new byte[16], new int[2]);
        } catch (SocketException e) {
            return -1;
        }
    }

    public SocketAddress getRemoteSocketAddress() {
        return connected ? new InetSocketAddress(remoteAddr, remotePort) : null;
    }

    public SocketAddress getLocalSocketAddress() {
        return bound ? new InetSocketAddress(getLocalAddress(), getLocalPort()) : null;
    }

    public java.nio.channels.SocketChannel getChannel() {
        return null;
    }

    public InputStream getInputStream() throws IOException {
        checkOpen();
        if (!connected) {
            throw new SocketException("Socket is not connected");
        }
        if (shutIn) {
            throw new SocketException("Socket input is shutdown");
        }
        if (in == null) {
            in = new SocketInputStream();
        }
        return in;
    }

    public OutputStream getOutputStream() throws IOException {
        checkOpen();
        if (!connected) {
            throw new SocketException("Socket is not connected");
        }
        if (shutOut) {
            throw new SocketException("Socket output is shutdown");
        }
        if (out == null) {
            out = new SocketOutputStream();
        }
        return out;
    }

    private void setOpt(int opt, int value) throws SocketException {
        checkOpen();
        if (fd >= 0) {
            Net.setOption(fd, opt, value);
        } else {
            pending.put(opt, value);
        }
    }

    private int getOpt(int opt, int dflt) throws SocketException {
        checkOpen();
        if (fd >= 0) {
            return Net.getOption(fd, opt);
        }
        Integer v = pending.get(opt);
        return v != null ? v : dflt;
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        setOpt(SocketOptions.TCP_NODELAY, on ? 1 : 0);
    }

    public boolean getTcpNoDelay() throws SocketException {
        return getOpt(SocketOptions.TCP_NODELAY, 0) != 0;
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        if (on && linger < 0) {
            throw new IllegalArgumentException("invalid value for SO_LINGER");
        }
        setOpt(SocketOptions.SO_LINGER, on ? Math.min(linger, 65535) : -1);
    }

    public int getSoLinger() throws SocketException {
        return getOpt(SocketOptions.SO_LINGER, -1);
    }

    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Urgent data not supported");
    }

    public void setOOBInline(boolean on) throws SocketException {
        setOpt(SocketOptions.SO_OOBINLINE, on ? 1 : 0);
    }

    public boolean getOOBInline() throws SocketException {
        return getOpt(SocketOptions.SO_OOBINLINE, 0) != 0;
    }

    public synchronized void setSoTimeout(int timeout) throws SocketException {
        checkOpen();
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout can't be negative");
        }
        soTimeout = timeout;
    }

    public synchronized int getSoTimeout() throws SocketException {
        checkOpen();
        return soTimeout;
    }

    public synchronized void setSendBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("negative send size");
        }
        setOpt(SocketOptions.SO_SNDBUF, size);
    }

    public synchronized int getSendBufferSize() throws SocketException {
        return getOpt(SocketOptions.SO_SNDBUF, 65536);
    }

    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("invalid receive size");
        }
        setOpt(SocketOptions.SO_RCVBUF, size);
    }

    public synchronized int getReceiveBufferSize() throws SocketException {
        return getOpt(SocketOptions.SO_RCVBUF, 65536);
    }

    public void setKeepAlive(boolean on) throws SocketException {
        setOpt(SocketOptions.SO_KEEPALIVE, on ? 1 : 0);
    }

    public boolean getKeepAlive() throws SocketException {
        return getOpt(SocketOptions.SO_KEEPALIVE, 0) != 0;
    }

    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255) {
            throw new IllegalArgumentException("tc is not in range 0 -- 255");
        }
        setOpt(SocketOptions.IP_TOS, tc);
    }

    public int getTrafficClass() throws SocketException {
        return getOpt(SocketOptions.IP_TOS, 0);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        setOpt(SocketOptions.SO_REUSEADDR, on ? 1 : 0);
    }

    public boolean getReuseAddress() throws SocketException {
        return getOpt(SocketOptions.SO_REUSEADDR, 0) != 0;
    }

    public synchronized void close() throws IOException {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            if (fd >= 0) {
                Net.close(fd);
                fd = -1;
            }
        }
    }

    public void shutdownInput() throws IOException {
        checkOpen();
        if (!connected) {
            throw new SocketException("Socket is not connected");
        }
        if (shutIn) {
            throw new SocketException("Socket input is already shutdown");
        }
        Net.shutdown(fd, 0);
        shutIn = true;
    }

    public void shutdownOutput() throws IOException {
        checkOpen();
        if (!connected) {
            throw new SocketException("Socket is not connected");
        }
        if (shutOut) {
            throw new SocketException("Socket output is already shutdown");
        }
        Net.shutdown(fd, 1);
        shutOut = true;
    }

    public String toString() {
        if (connected) {
            return "Socket[address=" + remoteAddr + ",port=" + remotePort + ",localPort=" + getLocalPort() + "]";
        }
        return "Socket[unconnected]";
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isInputShutdown() {
        return shutIn;
    }

    public boolean isOutputShutdown() {
        return shutOut;
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    }

    private int liveFd() throws SocketException {
        int f = fd;
        if (closed || f < 0) {
            throw new SocketException("Socket closed");
        }
        return f;
    }

    private final class SocketInputStream extends InputStream {
        private boolean eof;

        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n <= 0 ? -1 : b[0] & 0xff;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int f = liveFd();
            if (eof || shutIn) {
                return -1;
            }
            if (len == 0) {
                return 0;
            }
            int n = Net.recv(f, b, off, len, soTimeout);
            if (n < 0) {
                eof = true;
            }
            return n;
        }

        public int available() throws IOException {
            int f = liveFd();
            return eof || shutIn ? 0 : Net.available(f);
        }

        public void close() throws IOException {
            Socket.this.close();
        }
    }

    private final class SocketOutputStream extends OutputStream {
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (shutOut) {
                throw new SocketException("Socket output is shutdown");
            }
            Net.send(liveFd(), b, off, len);
        }

        public void close() throws IOException {
            Socket.this.close();
        }
    }
}
