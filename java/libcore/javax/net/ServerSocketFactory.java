package javax.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;

public abstract class ServerSocketFactory {
    private static ServerSocketFactory defaultFactory;

    protected ServerSocketFactory() {
    }

    public static synchronized ServerSocketFactory getDefault() {
        if (defaultFactory == null) {
            defaultFactory = new DefaultServerSocketFactory();
        }
        return defaultFactory;
    }

    public ServerSocket createServerSocket() throws IOException {
        throw new SocketException("Unbound server sockets not implemented");
    }

    public abstract ServerSocket createServerSocket(int port) throws IOException;

    public abstract ServerSocket createServerSocket(int port, int backlog) throws IOException;

    public abstract ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException;

    private static final class DefaultServerSocketFactory extends ServerSocketFactory {
        public ServerSocket createServerSocket() throws IOException {
            return new ServerSocket();
        }

        public ServerSocket createServerSocket(int port) throws IOException {
            return new ServerSocket(port);
        }

        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            return new ServerSocket(port, backlog);
        }

        public ServerSocket createServerSocket(int port, int backlog, InetAddress iAddress) throws IOException {
            return new ServerSocket(port, backlog, iAddress);
        }
    }
}
