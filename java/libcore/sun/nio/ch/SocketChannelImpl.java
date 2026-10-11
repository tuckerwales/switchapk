package sun.nio.ch;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import libcore.io.Net;

/** A SocketChannel over a java.net.Socket, which owns the descriptor. */
public class SocketChannelImpl extends SocketChannel implements SelChImpl {
    private static final int UNCONNECTED = 0;
    private static final int PENDING = 1;
    private static final int CONNECTED = 2;

    private static final Set<SocketOption<?>> OPTIONS = Collections.unmodifiableSet(new HashSet<SocketOption<?>>(
            Arrays.<SocketOption<?>>asList(StandardSocketOptions.SO_SNDBUF, StandardSocketOptions.SO_RCVBUF,
                    StandardSocketOptions.SO_KEEPALIVE, StandardSocketOptions.SO_REUSEADDR,
                    StandardSocketOptions.SO_LINGER, StandardSocketOptions.TCP_NODELAY,
                    StandardSocketOptions.IP_TOS)));

    private final Socket socket;
    private final Object stateLock = new Object();
    private volatile int state;
    private InetSocketAddress pendingRemote;
    private int nonBlockingFd = -1; /* the descriptor O_NONBLOCK was set on */

    SocketChannelImpl(SelectorProvider provider) {
        super(provider);
        socket = new Socket();
        socket.setChannel$(this);
    }

    SocketChannelImpl(SelectorProvider provider, Socket accepted) {
        super(provider);
        socket = accepted;
        socket.setChannel$(this);
        state = CONNECTED;
    }

    public int selFd() {
        return socket.fd$();
    }

    public int pollEvents(int ops) {
        int ev = 0;
        if ((ops & SelectionKey.OP_READ) != 0) {
            ev |= 1;
        }
        if ((ops & (SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT)) != 0) {
            ev |= 2;
        }
        return ev;
    }

    public int readyOps(int revents, int ops) {
        int r = 0;
        boolean error = (revents & (4 | 8)) != 0;
        if ((revents & 1) != 0 || error) {
            r |= ops & SelectionKey.OP_READ;
        }
        if ((revents & 2) != 0 || error) {
            r |= ops & (state == PENDING ? SelectionKey.OP_CONNECT : SelectionKey.OP_WRITE);
        }
        return r;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    /** Applies the blocking mode to the descriptor once it exists. */
    private void syncBlocking() throws IOException {
        int fd = socket.fd$();
        if (fd >= 0 && !isBlocking() && nonBlockingFd != fd) {
            Net.setNonBlocking(fd, true);
            nonBlockingFd = fd;
        }
    }

    protected void implConfigureBlocking(boolean block) throws IOException {
        int fd = socket.fd$();
        if (fd >= 0) {
            Net.setNonBlocking(fd, !block);
            nonBlockingFd = block ? -1 : fd;
        }
    }

    public SocketChannel bind(SocketAddress local) throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            if (state == PENDING) {
                throw new ConnectionPendingException();
            }
            if (socket.isBound()) {
                throw new java.nio.channels.AlreadyBoundException();
            }
            socket.bind(local == null ? new InetSocketAddress(0) : NioIo.checkAddress(local));
            syncBlocking();
        }
        return this;
    }

    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        ensureOpen();
        if (name == StandardSocketOptions.SO_SNDBUF) {
            socket.setSendBufferSize((Integer) value);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            socket.setReceiveBufferSize((Integer) value);
        } else if (name == StandardSocketOptions.SO_KEEPALIVE) {
            socket.setKeepAlive((Boolean) value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            socket.setReuseAddress((Boolean) value);
        } else if (name == StandardSocketOptions.SO_LINGER) {
            int v = (Integer) value;
            socket.setSoLinger(v >= 0, Math.max(v, 0));
        } else if (name == StandardSocketOptions.TCP_NODELAY) {
            socket.setTcpNoDelay((Boolean) value);
        } else if (name == StandardSocketOptions.IP_TOS) {
            socket.setTrafficClass((Integer) value);
        } else {
            throw new UnsupportedOperationException("'" + name + "' not supported");
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name) throws IOException {
        ensureOpen();
        Object v;
        if (name == StandardSocketOptions.SO_SNDBUF) {
            v = socket.getSendBufferSize();
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            v = socket.getReceiveBufferSize();
        } else if (name == StandardSocketOptions.SO_KEEPALIVE) {
            v = socket.getKeepAlive();
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            v = socket.getReuseAddress();
        } else if (name == StandardSocketOptions.SO_LINGER) {
            v = socket.getSoLinger();
        } else if (name == StandardSocketOptions.TCP_NODELAY) {
            v = socket.getTcpNoDelay();
        } else if (name == StandardSocketOptions.IP_TOS) {
            v = socket.getTrafficClass();
        } else {
            throw new UnsupportedOperationException("'" + name + "' not supported");
        }
        return (T) v;
    }

    public Set<SocketOption<?>> supportedOptions() {
        return OPTIONS;
    }

    public SocketChannel shutdownInput() throws IOException {
        ensureOpen();
        if (state != CONNECTED) {
            throw new NotYetConnectedException();
        }
        if (!socket.isInputShutdown()) {
            socket.shutdownInput();
        }
        return this;
    }

    public SocketChannel shutdownOutput() throws IOException {
        ensureOpen();
        if (state != CONNECTED) {
            throw new NotYetConnectedException();
        }
        if (!socket.isOutputShutdown()) {
            socket.shutdownOutput();
        }
        return this;
    }

    public Socket socket() {
        return socket;
    }

    public boolean isConnected() {
        return state == CONNECTED;
    }

    public boolean isConnectionPending() {
        return state == PENDING;
    }

    public boolean connect(SocketAddress remote) throws IOException {
        InetSocketAddress isa = NioIo.checkAddress(remote);
        InetAddress addr = NioIo.target(isa);
        synchronized (stateLock) {
            ensureOpen();
            if (state == CONNECTED) {
                throw new AlreadyConnectedException();
            }
            if (state == PENDING) {
                throw new ConnectionPendingException();
            }
            if (isBlocking()) {
                socket.connect(new InetSocketAddress(addr, isa.getPort()));
                state = CONNECTED;
                return true;
            }
            int fd = socket.prepareFd$(addr instanceof java.net.Inet6Address);
            syncBlocking();
            if (Net.connectNow(fd, addr.getAddress(), isa.getPort())) {
                socket.markConnected$(addr, isa.getPort());
                state = CONNECTED;
                return true;
            }
            pendingRemote = new InetSocketAddress(addr, isa.getPort());
            state = PENDING;
            return false;
        }
    }

    public boolean finishConnect() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            if (state == CONNECTED) {
                return true;
            }
            if (state != PENDING) {
                throw new NoConnectionPendingException();
            }
            int fd = socket.fd$();
            for (;;) {
                boolean done;
                try {
                    done = Net.finishConnectNow(fd);
                } catch (IOException e) {
                    state = UNCONNECTED;
                    close();
                    throw e;
                }
                if (done) {
                    socket.markConnected$(pendingRemote.getAddress(), pendingRemote.getPort());
                    state = CONNECTED;
                    return true;
                }
                if (!isBlocking()) {
                    return false;
                }
                Net.poll(new int[] {fd}, new int[] {2}, new int[1], 1, 50);
            }
        }
    }

    public SocketAddress getRemoteAddress() throws IOException {
        ensureOpen();
        return state == CONNECTED ? socket.getRemoteSocketAddress() : null;
    }

    public SocketAddress getLocalAddress() throws IOException {
        ensureOpen();
        return socket.isBound() ? socket.getLocalSocketAddress() : null;
    }

    private int checkedFd() throws IOException {
        ensureOpen();
        if (state != CONNECTED) {
            throw new NotYetConnectedException();
        }
        return socket.fd$();
    }

    public int read(ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new NullPointerException();
        }
        int fd = checkedFd();
        int len = dst.remaining();
        if (len == 0) {
            return 0;
        }
        if (socket.isInputShutdown()) {
            return -1;
        }
        byte[] b = NioIo.readArray(dst);
        int off = NioIo.readOffset(dst);
        int n = isBlocking() ? Net.recv(fd, b, off, len, 0) : Net.recvNow(fd, b, off, len);
        NioIo.finishRead(dst, b, n);
        return n;
    }

    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > dsts.length - length) {
            throw new IndexOutOfBoundsException();
        }
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            if (!dsts[i].hasRemaining()) {
                continue;
            }
            int want = dsts[i].remaining();
            int n = read(dsts[i]);
            if (n < 0) {
                return total == 0 ? -1 : total;
            }
            total += n;
            if (n < want) {
                break;
            }
        }
        return total;
    }

    public int write(ByteBuffer src) throws IOException {
        if (src == null) {
            throw new NullPointerException();
        }
        int fd = checkedFd();
        if (socket.isOutputShutdown()) {
            throw new IOException("Socket output is shutdown");
        }
        int len = src.remaining();
        if (len == 0) {
            return 0;
        }
        byte[] b = NioIo.writeArray(src);
        int off = NioIo.writeOffset(src);
        int n;
        if (isBlocking()) {
            Net.send(fd, b, off, len);
            n = len;
        } else {
            n = Net.sendNow(fd, b, off, len);
        }
        NioIo.finishWrite(src, n);
        return n;
    }

    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > srcs.length - length) {
            throw new IndexOutOfBoundsException();
        }
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            int want = srcs[i].remaining();
            int n = write(srcs[i]);
            total += n;
            if (n < want) {
                break;
            }
        }
        return total;
    }

    protected void implCloseSelectableChannel() throws IOException {
        state = UNCONNECTED;
        socket.closeInternal$();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSuperclass().getName()).append('[');
        if (!isOpen()) {
            sb.append("closed");
        } else if (state == CONNECTED) {
            sb.append("connected local=").append(socket.getLocalSocketAddress()).append(" remote=")
                    .append(socket.getRemoteSocketAddress());
        } else if (state == PENDING) {
            sb.append("connection-pending remote=").append(pendingRemote);
        } else {
            sb.append("unconnected");
        }
        return sb.append(']').toString();
    }
}
