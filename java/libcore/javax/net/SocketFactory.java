package javax.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public abstract class SocketFactory {
    private static SocketFactory defaultFactory;

    protected SocketFactory() {
    }

    public static synchronized SocketFactory getDefault() {
        if (defaultFactory == null) {
            defaultFactory = new DefaultSocketFactory();
        }
        return defaultFactory;
    }

    public Socket createSocket() throws IOException {
        throw new SocketException("Unconnected sockets not implemented");
    }

    public abstract Socket createSocket(String host, int port) throws IOException, UnknownHostException;

    public abstract Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException;

    public abstract Socket createSocket(InetAddress host, int port) throws IOException;

    public abstract Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException;

    private static final class DefaultSocketFactory extends SocketFactory {
        public Socket createSocket() {
            return new Socket();
        }

        public Socket createSocket(String host, int port) throws IOException {
            return new Socket(host, port);
        }

        public Socket createSocket(InetAddress address, int port) throws IOException {
            return new Socket(address, port);
        }

        public Socket createSocket(String host, int port, InetAddress clientAddress, int clientPort)
                throws IOException {
            return new Socket(host, port, clientAddress, clientPort);
        }

        public Socket createSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort)
                throws IOException {
            return new Socket(address, port, clientAddress, clientPort);
        }
    }
}
