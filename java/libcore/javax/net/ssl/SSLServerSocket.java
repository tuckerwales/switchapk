package javax.net.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public abstract class SSLServerSocket extends ServerSocket {
    protected SSLServerSocket() throws IOException {
    }

    protected SSLServerSocket(int port) throws IOException {
        super(port);
    }

    protected SSLServerSocket(int port, int backlog) throws IOException {
        super(port, backlog);
    }

    protected SSLServerSocket(int port, int backlog, InetAddress address) throws IOException {
        super(port, backlog, address);
    }

    public abstract String[] getEnabledCipherSuites();

    public abstract void setEnabledCipherSuites(String[] suites);

    public abstract String[] getSupportedCipherSuites();

    public abstract String[] getSupportedProtocols();

    public abstract String[] getEnabledProtocols();

    public abstract void setEnabledProtocols(String[] protocols);

    public abstract void setNeedClientAuth(boolean need);

    public abstract boolean getNeedClientAuth();

    public abstract void setWantClientAuth(boolean want);

    public abstract boolean getWantClientAuth();

    public abstract void setUseClientMode(boolean mode);

    public abstract boolean getUseClientMode();

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

    public String toString() {
        return "SSL" + super.toString();
    }
}
