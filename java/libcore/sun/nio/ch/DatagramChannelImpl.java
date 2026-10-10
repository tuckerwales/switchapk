package sun.nio.ch;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import libcore.io.Net;

/** A DatagramChannel over a java.net.DatagramSocket, which owns the descriptor. */
public class DatagramChannelImpl extends DatagramChannel implements SelChImpl {
    private static final Set<SocketOption<?>> OPTIONS = Collections.unmodifiableSet(new HashSet<SocketOption<?>>(
            Arrays.<SocketOption<?>>asList(StandardSocketOptions.SO_SNDBUF, StandardSocketOptions.SO_RCVBUF,
                    StandardSocketOptions.SO_REUSEADDR, StandardSocketOptions.SO_BROADCAST,
                    StandardSocketOptions.IP_TOS)));

    private final DatagramSocket socket;
    private InetSocketAddress remote;
    private int nonBlockingFd = -1;

    DatagramChannelImpl(SelectorProvider provider) throws IOException {
        super(provider);
        socket = new DatagramSocket((SocketAddress) null);
        socket.setChannel$(this);
    }

    public int selFd() {
        return socket.fd$();
    }

    public int pollEvents(int ops) {
        int ev = 0;
        if ((ops & SelectionKey.OP_READ) != 0) {
            ev |= 1;
        }
        if ((ops & SelectionKey.OP_WRITE) != 0) {
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
            r |= ops & SelectionKey.OP_WRITE;
        }
        return r;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

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

    /** Sending or receiving binds an unbound channel to an ephemeral port, as in the JDK. */
    private int boundFd() throws IOException {
        ensureOpen();
        if (!socket.isBound()) {
            socket.bind(new InetSocketAddress(0));
        }
        syncBlocking();
        return socket.fd$();
    }

    public DatagramChannel bind(SocketAddress local) throws IOException {
        ensureOpen();
        if (socket.isBound()) {
            throw new java.nio.channels.AlreadyBoundException();
        }
        socket.bind(local == null ? new InetSocketAddress(0) : NioIo.checkAddress(local));
        syncBlocking();
        return this;
    }

    public <T> DatagramChannel setOption(SocketOption<T> name, T value) throws IOException {
        ensureOpen();
        if (name == StandardSocketOptions.SO_SNDBUF) {
            socket.setSendBufferSize((Integer) value);
        } else if (name == StandardSocketOptions.SO_RCVBUF) {
            socket.setReceiveBufferSize((Integer) value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            socket.setReuseAddress((Boolean) value);
        } else if (name == StandardSocketOptions.SO_BROADCAST) {
            socket.setBroadcast((Boolean) value);
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
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            v = socket.getReuseAddress();
        } else if (name == StandardSocketOptions.SO_BROADCAST) {
            v = socket.getBroadcast();
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

    public DatagramSocket socket() {
        return socket;
    }

    public boolean isConnected() {
        return remote != null;
    }

    public DatagramChannel connect(SocketAddress sa) throws IOException {
        InetSocketAddress isa = NioIo.checkAddress(sa);
        synchronized (this) {
            int fd = boundFd();
            InetAddress addr = NioIo.target(isa);
            Net.connectDatagram(fd, addr.getAddress(), isa.getPort());
            remote = new InetSocketAddress(addr, isa.getPort());
            socket.connect(remote);
        }
        return this;
    }

    public DatagramChannel disconnect() throws IOException {
        synchronized (this) {
            if (remote == null || !isOpen()) {
                return this;
            }
            Net.connectDatagram(socket.fd$(), null, 0);
            remote = null;
            socket.disconnect();
        }
        return this;
    }

    public SocketAddress getRemoteAddress() throws IOException {
        ensureOpen();
        return remote;
    }

    public SocketAddress receive(ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new NullPointerException();
        }
        int fd = boundFd();
        int len = dst.remaining();
        byte[] b = new byte[Math.max(len, 1)];
        byte[] peer = new byte[16];
        int[] info = new int[2];
        int n = isBlocking() ? Net.recvfrom(fd, b, 0, len, 0, peer, info) : Net.recvfromNow(fd, b, 0, len, peer, info);
        if (n < 0) {
            return null;
        }
        dst.put(b, 0, Math.min(n, len));
        return new InetSocketAddress(NioIo.peer(peer, info), info[0]);
    }

    public int send(ByteBuffer src, SocketAddress target) throws IOException {
        if (src == null) {
            throw new NullPointerException();
        }
        InetSocketAddress isa = NioIo.checkAddress(target);
        if (remote != null && !remote.equals(isa)) {
            throw new IllegalArgumentException("Connected address not equal to target address");
        }
        int fd = boundFd();
        InetAddress addr = NioIo.target(isa);
        int len = src.remaining();
        byte[] b = NioIo.writeArray(src);
        int off = NioIo.writeOffset(src);
        int n;
        if (remote != null) {
            n = isBlocking() ? sendConnected(fd, b, off, len) : Net.sendtoNow(fd, b, off, len, null, 0);
        } else if (isBlocking()) {
            Net.sendto(fd, b, off, len, addr.getAddress(), isa.getPort());
            n = len;
        } else {
            n = Net.sendtoNow(fd, b, off, len, addr.getAddress(), isa.getPort());
        }
        NioIo.finishWrite(src, n);
        return n;
    }

    private static int sendConnected(int fd, byte[] b, int off, int len) throws IOException {
        Net.send(fd, b, off, len);
        return len;
    }

    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (remote == null) {
            throw new NotYetConnectedException();
        }
        int fd = socket.fd$();
        int len = dst.remaining();
        byte[] b = new byte[Math.max(len, 1)];
        int n = isBlocking() ? Net.recvfrom(fd, b, 0, len, 0, new byte[16], new int[2])
                : Net.recvfromNow(fd, b, 0, len, new byte[16], new int[2]);
        if (n < 0) {
            return 0;
        }
        dst.put(b, 0, Math.min(n, len));
        return Math.min(n, len);
    }

    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > dsts.length - length) {
            throw new IndexOutOfBoundsException();
        }
        int total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += dsts[i].remaining();
        }
        ByteBuffer tmp = ByteBuffer.allocate(total);
        int n = read(tmp);
        tmp.flip();
        for (int i = offset; i < offset + length && tmp.hasRemaining(); i++) {
            int take = Math.min(tmp.remaining(), dsts[i].remaining());
            ByteBuffer slice = tmp.duplicate();
            slice.limit(slice.position() + take);
            dsts[i].put(slice);
            tmp.position(tmp.position() + take);
        }
        return n;
    }

    public int write(ByteBuffer src) throws IOException {
        ensureOpen();
        if (remote == null) {
            throw new NotYetConnectedException();
        }
        return send(src, remote);
    }

    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > srcs.length - length) {
            throw new IndexOutOfBoundsException();
        }
        int total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += srcs[i].remaining();
        }
        ByteBuffer tmp = ByteBuffer.allocate(total);
        for (int i = offset; i < offset + length; i++) {
            tmp.put(srcs[i].duplicate());
        }
        tmp.flip();
        int n = write(tmp);
        int left = n;
        for (int i = offset; i < offset + length && left > 0; i++) {
            int take = Math.min(left, srcs[i].remaining());
            srcs[i].position(srcs[i].position() + take);
            left -= take;
        }
        return n;
    }

    public SocketAddress getLocalAddress() throws IOException {
        ensureOpen();
        return socket.isBound() ? socket.getLocalSocketAddress() : null;
    }

    protected void implCloseSelectableChannel() throws IOException {
        remote = null;
        socket.closeInternal$();
    }
}
