package javax.net.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import javax.net.SocketFactory;

public abstract class SSLSocketFactory extends SocketFactory {
    private static SSLSocketFactory defaultFactory;

    public SSLSocketFactory() {
    }

    public static synchronized SocketFactory getDefault() {
        if (defaultFactory == null) {
            try {
                defaultFactory = SSLContext.getDefault().getSocketFactory();
            } catch (NoSuchAlgorithmException e) {
                defaultFactory = new Failing(e);
            }
        }
        return defaultFactory;
    }

    public abstract String[] getDefaultCipherSuites();

    public abstract String[] getSupportedCipherSuites();

    public abstract Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException;

    public Socket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException {
        throw new UnsupportedOperationException();
    }

    /** What getDefault returns when no SSLContext can be made: every socket fails to open. */
    private static final class Failing extends SSLSocketFactory {
        private final Exception reason;

        Failing(Exception reason) {
            this.reason = reason;
        }

        private SocketException fail() {
            SocketException e = new SocketException(reason.toString());
            e.initCause(reason);
            return e;
        }

        public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        public Socket createSocket() throws IOException {
            throw fail();
        }

        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            throw fail();
        }

        public Socket createSocket(String host, int port) throws IOException {
            throw fail();
        }

        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            throw fail();
        }

        public Socket createSocket(InetAddress host, int port) throws IOException {
            throw fail();
        }

        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            throw fail();
        }
    }
}
