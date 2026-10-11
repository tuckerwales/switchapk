package libcore.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSocketFactory;

final class SslSocketFactoryImpl extends SSLSocketFactory {
    private final SslContextImpl context;

    SslSocketFactoryImpl(SslContextImpl context) {
        this.context = context;
    }

    public String[] getDefaultCipherSuites() {
        return CipherSuites.SUPPORTED.clone();
    }

    public String[] getSupportedCipherSuites() {
        return CipherSuites.SUPPORTED.clone();
    }

    public Socket createSocket() throws IOException {
        return new SslSocketImpl(context, new Socket(), null, -1, true);
    }

    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        if (s == null) throw new NullPointerException("s == null");
        if (!s.isConnected()) throw new java.net.SocketException("Socket is not connected.");
        return new SslSocketImpl(context, s, host, port, autoClose);
    }

    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return new SslSocketImpl(context, new Socket(host, port), host, port, true);
    }

    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        return new SslSocketImpl(context, new Socket(host, port, localHost, localPort), host, port, true);
    }

    public Socket createSocket(InetAddress host, int port) throws IOException {
        return new SslSocketImpl(context, new Socket(host, port), null, port, true);
    }

    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return new SslSocketImpl(context, new Socket(address, port, localAddress, localPort), null, port, true);
    }
}
