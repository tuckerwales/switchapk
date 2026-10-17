package javax.net.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.BiFunction;

public abstract class SSLSocket extends Socket {
    protected SSLSocket() {
    }

    protected SSLSocket(String host, int port) throws IOException, UnknownHostException {
        super(host, port);
    }

    protected SSLSocket(InetAddress address, int port) throws IOException {
        super(address, port);
    }

    protected SSLSocket(String host, int port, InetAddress clientAddress, int clientPort)
            throws IOException, UnknownHostException {
        super(host, port, clientAddress, clientPort);
    }

    protected SSLSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort) throws IOException {
        super(address, port, clientAddress, clientPort);
    }

    public abstract String[] getSupportedCipherSuites();

    public abstract String[] getEnabledCipherSuites();

    public abstract void setEnabledCipherSuites(String[] suites);

    public abstract String[] getSupportedProtocols();

    public abstract String[] getEnabledProtocols();

    public abstract void setEnabledProtocols(String[] protocols);

    public abstract SSLSession getSession();

    public SSLSession getHandshakeSession() {
        throw new UnsupportedOperationException();
    }

    public abstract void addHandshakeCompletedListener(HandshakeCompletedListener listener);

    public abstract void removeHandshakeCompletedListener(HandshakeCompletedListener listener);

    public abstract void startHandshake() throws IOException;

    public abstract void setUseClientMode(boolean mode);

    public abstract boolean getUseClientMode();

    public abstract void setNeedClientAuth(boolean need);

    public abstract boolean getNeedClientAuth();

    public abstract void setWantClientAuth(boolean want);

    public abstract boolean getWantClientAuth();

    public abstract void setEnableSessionCreation(boolean flag);

    public abstract boolean getEnableSessionCreation();

    public SSLParameters getSSLParameters() {
        SSLParameters p = new SSLParameters();
        p.setCipherSuites(getEnabledCipherSuites());
        p.setProtocols(getEnabledProtocols());
        if (getNeedClientAuth()) p.setNeedClientAuth(true);
        else if (getWantClientAuth()) p.setWantClientAuth(true);
        return p;
    }

    public void setSSLParameters(SSLParameters params) {
        String[] s = params.getCipherSuites();
        if (s != null) setEnabledCipherSuites(s);
        s = params.getProtocols();
        if (s != null) setEnabledProtocols(s);
        if (params.getNeedClientAuth()) setNeedClientAuth(true);
        else if (params.getWantClientAuth()) setWantClientAuth(true);
        else setWantClientAuth(false);
    }

    public String getApplicationProtocol() {
        throw new UnsupportedOperationException();
    }

    public String getHandshakeApplicationProtocol() {
        throw new UnsupportedOperationException();
    }

    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLSocket, List<String>, String> selector) {
        throw new UnsupportedOperationException();
    }

    public BiFunction<SSLSocket, List<String>, String> getHandshakeApplicationProtocolSelector() {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        return "SSL" + super.toString();
    }
}
