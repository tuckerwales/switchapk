package javax.net.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import javax.net.ServerSocketFactory;

public abstract class SSLServerSocketFactory extends ServerSocketFactory {
    private static SSLServerSocketFactory defaultFactory;

    protected SSLServerSocketFactory() {
    }

    public static synchronized ServerSocketFactory getDefault() {
        if (defaultFactory == null) {
            try {
                defaultFactory = SSLContext.getDefault().getServerSocketFactory();
            } catch (NoSuchAlgorithmException e) {
                defaultFactory = new Failing(e);
            }
        }
        return defaultFactory;
    }

    public abstract String[] getDefaultCipherSuites();

    public abstract String[] getSupportedCipherSuites();

    private static final class Failing extends SSLServerSocketFactory {
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

        public ServerSocket createServerSocket(int port) throws IOException {
            throw fail();
        }

        public ServerSocket createServerSocket(int port, int backlog) throws IOException {
            throw fail();
        }

        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            throw fail();
        }
    }
}
