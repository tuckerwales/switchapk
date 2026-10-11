package sun.nio.ch;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

public class SelectorProviderImpl extends SelectorProvider {
    public DatagramChannel openDatagramChannel() throws IOException {
        return new DatagramChannelImpl(this);
    }

    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        checkFamily(family);
        return new DatagramChannelImpl(this);
    }

    public Pipe openPipe() throws IOException {
        throw new IOException("Pipes are not supported");
    }

    public AbstractSelector openSelector() throws IOException {
        return new PollSelectorImpl(this);
    }

    public ServerSocketChannel openServerSocketChannel() throws IOException {
        return new ServerSocketChannelImpl(this);
    }

    public SocketChannel openSocketChannel() throws IOException {
        return new SocketChannelImpl(this);
    }

    public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
        checkFamily(family);
        return new SocketChannelImpl(this);
    }

    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException {
        checkFamily(family);
        return new ServerSocketChannelImpl(this);
    }

    private static void checkFamily(ProtocolFamily family) {
        if (family != StandardProtocolFamily.INET && family != StandardProtocolFamily.INET6) {
            throw new UnsupportedOperationException("Protocol family not supported");
        }
    }
}
