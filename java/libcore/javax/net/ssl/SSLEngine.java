package javax.net.ssl;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;

/** API only so far: SSLContext.createSSLEngine throws UnsupportedOperationException until an engine exists. */
public abstract class SSLEngine {
    private final String peerHost;
    private final int peerPort;

    protected SSLEngine() {
        this(null, -1);
    }

    protected SSLEngine(String peerHost, int peerPort) {
        this.peerHost = peerHost;
        this.peerPort = peerPort;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return wrap(new ByteBuffer[] {src}, 0, 1, dst);
    }

    public SSLEngineResult wrap(ByteBuffer[] srcs, ByteBuffer dst) throws SSLException {
        if (srcs == null) throw new IllegalArgumentException("src == null");
        return wrap(srcs, 0, srcs.length, dst);
    }

    public abstract SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException;

    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return unwrap(src, new ByteBuffer[] {dst}, 0, 1);
    }

    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts) throws SSLException {
        if (dsts == null) throw new IllegalArgumentException("dsts == null");
        return unwrap(src, dsts, 0, dsts.length);
    }

    public abstract SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length)
            throws SSLException;

    public abstract Runnable getDelegatedTask();

    public abstract void closeInbound() throws SSLException;

    public abstract boolean isInboundDone();

    public abstract void closeOutbound();

    public abstract boolean isOutboundDone();

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

    public abstract void beginHandshake() throws SSLException;

    public abstract SSLEngineResult.HandshakeStatus getHandshakeStatus();

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

    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
        throw new UnsupportedOperationException();
    }

    public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
        throw new UnsupportedOperationException();
    }
}
