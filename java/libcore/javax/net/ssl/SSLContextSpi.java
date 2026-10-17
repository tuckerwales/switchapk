package javax.net.ssl;

import java.security.KeyManagementException;
import java.security.SecureRandom;

public abstract class SSLContextSpi {
    public SSLContextSpi() {
    }

    protected abstract void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr)
            throws KeyManagementException;

    protected abstract SSLSocketFactory engineGetSocketFactory();

    protected abstract SSLServerSocketFactory engineGetServerSocketFactory();

    protected abstract SSLEngine engineCreateSSLEngine();

    protected abstract SSLEngine engineCreateSSLEngine(String host, int port);

    protected abstract SSLSessionContext engineGetServerSessionContext();

    protected abstract SSLSessionContext engineGetClientSessionContext();

    protected SSLParameters engineGetDefaultSSLParameters() {
        SSLSocket s = socket();
        return s == null ? new SSLParameters() : s.getSSLParameters();
    }

    protected SSLParameters engineGetSupportedSSLParameters() {
        SSLSocket s = socket();
        if (s == null) return new SSLParameters();
        SSLParameters p = new SSLParameters();
        p.setCipherSuites(s.getSupportedCipherSuites());
        p.setProtocols(s.getSupportedProtocols());
        return p;
    }

    private SSLSocket socket() {
        try {
            return (SSLSocket) engineGetSocketFactory().createSocket();
        } catch (Exception e) {
            return null;
        }
    }
}
