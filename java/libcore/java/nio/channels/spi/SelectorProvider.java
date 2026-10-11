package java.nio.channels.spi;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public abstract class SelectorProvider {
    private static SelectorProvider provider;

    protected SelectorProvider() {
    }

    public static SelectorProvider provider() {
        synchronized (SelectorProvider.class) {
            if (provider == null) {
                provider = new sun.nio.ch.SelectorProviderImpl();
            }
            return provider;
        }
    }

    public abstract DatagramChannel openDatagramChannel() throws IOException;

    public abstract DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException;

    public abstract Pipe openPipe() throws IOException;

    public abstract AbstractSelector openSelector() throws IOException;

    public abstract ServerSocketChannel openServerSocketChannel() throws IOException;

    public abstract SocketChannel openSocketChannel() throws IOException;

    public Channel inheritedChannel() throws IOException {
        return null;
    }

    public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
        throw new UnsupportedOperationException("Protocol family not supported");
    }

    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException {
        throw new UnsupportedOperationException("Protocol family not supported");
    }
}
