package sun.nio.ch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import libcore.io.Net;

/** A ServerSocketChannel over a java.net.ServerSocket, which owns the descriptor. */
public class ServerSocketChannelImpl extends ServerSocketChannel implements SelChImpl {
    private static final Set<SocketOption<?>> OPTIONS = Collections.unmodifiableSet(new HashSet<SocketOption<?>>(
            Arrays.<SocketOption<?>>asList(StandardSocketOptions.SO_RCVBUF, StandardSocketOptions.SO_REUSEADDR)));

    private final ServerSocket socket;
    private int nonBlockingFd = -1;

    ServerSocketChannelImpl(SelectorProvider provider) throws IOException {
        super(provider);
        socket = new ServerSocket();
        socket.setChannel$(this);
    }

    public int selFd() {
        return socket.fd$();
    }

    public int pollEvents(int ops) {
        return (ops & SelectionKey.OP_ACCEPT) != 0 ? 1 : 0;
    }

    public int readyOps(int revents, int ops) {
        return (revents & (1 | 4 | 8)) != 0 ? ops & SelectionKey.OP_ACCEPT : 0;
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    protected void implConfigureBlocking(boolean block) throws IOException {
        int fd = socket.fd$();
        if (fd >= 0) {
            Net.setNonBlocking(fd, !block);
            nonBlockingFd = block ? -1 : fd;
        }
    }

    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        ensureOpen();
        if (socket.isBound()) {
            throw new java.nio.channels.AlreadyBoundException();
        }
        socket.bind(local == null ? new InetSocketAddress(0) : NioIo.checkAddress(local), backlog < 1 ? 50 : backlog);
        int fd = socket.fd$();
        if (!isBlocking() && fd >= 0) {
            Net.setNonBlocking(fd, true);
            nonBlockingFd = fd;
        }
        return this;
    }

    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        ensureOpen();
        if (name == StandardSocketOptions.SO_RCVBUF) {
            socket.setReceiveBufferSize((Integer) value);
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            socket.setReuseAddress((Boolean) value);
        } else {
            throw new UnsupportedOperationException("'" + name + "' not supported");
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name) throws IOException {
        ensureOpen();
        if (name == StandardSocketOptions.SO_RCVBUF) {
            return (T) Integer.valueOf(socket.getReceiveBufferSize());
        } else if (name == StandardSocketOptions.SO_REUSEADDR) {
            return (T) Boolean.valueOf(socket.getReuseAddress());
        }
        throw new UnsupportedOperationException("'" + name + "' not supported");
    }

    public Set<SocketOption<?>> supportedOptions() {
        return OPTIONS;
    }

    public ServerSocket socket() {
        return socket;
    }

    public SocketChannel accept() throws IOException {
        ensureOpen();
        if (!socket.isBound()) {
            throw new NotYetBoundException();
        }
        int fd = socket.fd$();
        byte[] peer = new byte[16];
        int[] info = new int[2];
        int c = isBlocking() ? Net.accept(fd, 0, peer, info) : Net.acceptNow(fd, peer, info);
        if (c < 0) {
            return null;
        }
        Socket s = Socket.accepted$(c, NioIo.peer(peer, info), info[0]);
        return new SocketChannelImpl(provider(), s);
    }

    public SocketAddress getLocalAddress() throws IOException {
        ensureOpen();
        return socket.isBound() ? socket.getLocalSocketAddress() : null;
    }

    protected void implCloseSelectableChannel() throws IOException {
        socket.closeInternal$();
    }

    public String toString() {
        return "sun.nio.ch.ServerSocketChannelImpl[" + (isOpen() ? String.valueOf(socket.getLocalSocketAddress())
                : "closed") + "]";
    }
}
