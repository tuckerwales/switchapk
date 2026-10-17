package libcore.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A TLS client socket over another socket (the transport): one the app connected and handed to
 * SSLSocketFactory.createSocket(Socket, ...), or one this socket made and connects itself. The TLS work is a
 * libcore.ssl.Tls session; this class moves bytes between it and the transport's streams. Socket methods act on the
 * transport.
 *
 * Locks: engineLock serializes every call on the session (and is never held while blocking on the transport),
 * sendLock keeps records whole and in order on the way out, readLock and writeLock serialize the app's readers and
 * writers, and handshakeLock runs one handshake.
 */
final class SslSocketImpl extends SSLSocket {
    private static final int HANDSHAKE_NEW = 0, HANDSHAKE_DONE = 1, HANDSHAKE_FAILED = 2;

    private final SslContextImpl context;
    private final Socket transport;
    private final boolean autoClose;
    private String peerHost;
    private int peerPort;

    private final Object engineLock = new Object();
    private final Object sendLock = new Object();
    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private final Object handshakeLock = new Object();

    private long session; // Tls handle, 0 once closed
    private volatile int state = HANDSHAKE_NEW;
    private IOException handshakeFailure;
    private volatile boolean closed;
    private SslSessionImpl sslSession;
    private SslSessionImpl handshakeSession;
    private String applicationProtocol;

    private String[] enabledSuites = CipherSuites.SUPPORTED.clone();
    private String[] enabledProtocols = CipherSuites.PROTOCOLS.clone();
    private String[] alpn = new String[0];
    private String endpointIdentification;
    private List<SNIServerName> serverNames;
    private boolean useClientMode = true;
    private boolean enableSessionCreation = true;
    private final List<HandshakeCompletedListener> listeners = new ArrayList<HandshakeCompletedListener>();

    private InputStream rawIn;
    private OutputStream rawOut;
    private final byte[] inBuf = new byte[16 * 1024 + 512];
    private final byte[] outBuf = new byte[16 * 1024 + 512];
    private InputStream appIn;
    private OutputStream appOut;

    SslSocketImpl(SslContextImpl context, Socket transport, String host, int port, boolean autoClose) {
        this.context = context;
        this.transport = transport;
        this.peerHost = host;
        this.peerPort = port;
        this.autoClose = autoClose;
        if (peerHost == null && transport.getInetAddress() != null) {
            peerHost = hostOf(transport.getInetAddress());
        }
        if (peerPort < 0 && transport.isConnected()) peerPort = transport.getPort();
    }

    /** The name the app used for an address, when there is one (SNI and host checks need a name). */
    private static String hostOf(InetAddress a) {
        String s = a.toString();
        int slash = s.indexOf('/');
        return slash > 0 ? s.substring(0, slash) : null;
    }

    // ---- connection --------------------------------------------------------------------------------------------

    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        transport.connect(endpoint, timeout);
        if (endpoint instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) endpoint;
            if (peerHost == null) peerHost = isa.getHostString();
            peerPort = isa.getPort();
        }
    }

    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    public void bind(SocketAddress bindpoint) throws IOException {
        transport.bind(bindpoint);
    }

    /** Android's way of naming the peer for SNI and host checks on a socket made from an address. */
    public void setHostname(String hostname) {
        peerHost = hostname;
    }

    private String sniHost() {
        if (serverNames != null) {
            for (SNIServerName n : serverNames) {
                if (n instanceof SNIHostName) return ((SNIHostName) n).getAsciiName();
            }
            return null;
        }
        if (peerHost == null || HostnameVerifierImpl.isIpAddress(peerHost) || peerHost.indexOf('.') < 0) return null;
        return peerHost;
    }

    public void startHandshake() throws IOException {
        synchronized (handshakeLock) {
            if (state == HANDSHAKE_DONE) return;
            if (state == HANDSHAKE_FAILED) throw handshakeFailure;
            if (closed) throw new SocketException("Socket is closed");
            if (!useClientMode) {
                throw fail(new SSLHandshakeException("TLS server mode is not supported yet"));
            }
            try {
                handshake();
            } catch (IOException e) {
                throw fail(e);
            } catch (RuntimeException e) {
                throw fail(new SSLHandshakeException(e.toString()));
            }
        }
        notifyListeners();
    }

    private IOException fail(IOException e) {
        handshakeFailure = e;
        state = HANDSHAKE_FAILED;
        try {
            close();
        } catch (IOException ignored) {
            // the handshake failure is what matters
        }
        return e;
    }

    private void handshake() throws IOException {
        rawIn = transport.getInputStream();
        rawOut = transport.getOutputStream();
        synchronized (engineLock) {
            session = Tls.nNew(sniHost(), alpn.length > 0 ? alpn : null, CipherSuites.toMbedtls(enabledSuites));
        }
        for (;;) {
            int st;
            synchronized (engineLock) {
                st = Tls.nHandshake(handle());
            }
            sendPending();
            if (st == 0) break;
            if (st == Tls.WANT_READ) {
                if (!receive()) throw new SSLHandshakeException("Connection closed by peer");
            } else if (st == Tls.NEED_VERIFY) {
                verifyPeer();
            }
        }
        X509Certificate[] chain = handshakeSession.peerChain();
        synchronized (engineLock) {
            long h = handle();
            applicationProtocol = Tls.nAlpn(h);
            sslSession = new SslSessionImpl(context.clientSessions(), Tls.nSessionId(h), chain,
                    CipherSuites.fromMbedtls(Tls.nCipherSuite(h)), Tls.nProtocol(h), peerHost, peerPort);
        }
        state = HANDSHAKE_DONE;
    }

    private void verifyPeer() throws IOException {
        Object[] ders;
        String suite;
        String protocol;
        synchronized (engineLock) {
            ders = Tls.nPeerCerts(handle());
            suite = CipherSuites.fromMbedtls(Tls.nCipherSuite(handle()));
            protocol = Tls.nProtocol(handle());
        }
        X509Certificate[] chain = new X509Certificate[ders.length];
        try {
            for (int i = 0; i < ders.length; i++) chain[i] = TrustManagerImpl.parse((byte[]) ders[i]);
        } catch (CertificateException e) {
            throw rejectPeer(e, Tls.ALERT_BAD_CERTIFICATE);
        }
        handshakeSession = new SslSessionImpl(null, new byte[0], chain, suite, protocol, peerHost, peerPort);
        X509TrustManager tm = context.trustManager();
        String authType = CipherSuites.authType(suite);
        try {
            if (tm instanceof X509ExtendedTrustManager) {
                ((X509ExtendedTrustManager) tm).checkServerTrusted(chain.clone(), authType, this);
            } else {
                tm.checkServerTrusted(chain.clone(), authType);
                TrustManagerImpl.checkEndpoint(chain[0], getSSLParameters(), handshakeSession);
            }
        } catch (CertificateException e) {
            throw rejectPeer(e, Tls.ALERT_CERTIFICATE_UNKNOWN);
        } catch (RuntimeException e) {
            throw rejectPeer(e, Tls.ALERT_CERTIFICATE_UNKNOWN);
        }
        synchronized (engineLock) {
            Tls.nVerified(handle());
        }
    }

    private SSLHandshakeException rejectPeer(Exception cause, int alert) {
        try {
            synchronized (engineLock) {
                Tls.nAlert(handle(), alert);
            }
            sendPending();
        } catch (IOException ignored) {
            // the peer may be gone; the rejection stands
        }
        SSLHandshakeException e = new SSLHandshakeException(cause.toString());
        e.initCause(cause);
        return e;
    }

    private long handle() throws SocketException {
        if (session == 0) throw new SocketException("Socket is closed");
        return session;
    }

    /** Reads what the transport has into the session; false at end of stream. */
    private boolean receive() throws IOException {
        int n = rawIn.read(inBuf, 0, inBuf.length);
        if (n < 0) return false;
        synchronized (engineLock) {
            Tls.nFeed(handle(), inBuf, 0, n);
        }
        return true;
    }

    /** Sends every record the session produced, in order. */
    private void sendPending() throws IOException {
        synchronized (sendLock) {
            boolean sent = false;
            for (;;) {
                int n;
                synchronized (engineLock) {
                    if (session == 0) return;
                    n = Tls.nDrain(session, outBuf, 0, outBuf.length);
                }
                if (n <= 0) break;
                rawOut.write(outBuf, 0, n);
                sent = true;
            }
            if (sent) rawOut.flush();
        }
    }

    private void notifyListeners() {
        List<HandshakeCompletedListener> copy;
        synchronized (listeners) {
            if (listeners.isEmpty()) return;
            copy = new ArrayList<HandshakeCompletedListener>(listeners);
        }
        HandshakeCompletedEvent ev = new HandshakeCompletedEvent(this, sslSession);
        for (HandshakeCompletedListener l : copy) l.handshakeCompleted(ev);
    }

    // ---- application data -------------------------------------------------------------------------------------

    public InputStream getInputStream() throws IOException {
        if (closed) throw new SocketException("Socket is closed");
        synchronized (this) {
            if (appIn == null) appIn = new AppInput();
            return appIn;
        }
    }

    public OutputStream getOutputStream() throws IOException {
        if (closed) throw new SocketException("Socket is closed");
        synchronized (this) {
            if (appOut == null) appOut = new AppOutput();
            return appOut;
        }
    }

    private final class AppInput extends InputStream {
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n <= 0 ? -1 : b[0] & 0xff;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            startHandshake();
            if (len == 0) return 0;
            synchronized (readLock) {
                for (;;) {
                    int r;
                    synchronized (engineLock) {
                        r = Tls.nRead(handle(), b, off, len);
                    }
                    sendPending(); // alerts the session answered with
                    if (r > 0) return r;
                    if (r == Tls.EOF) return -1;
                    if (!receive()) return -1;
                }
            }
        }

        public int available() throws IOException {
            synchronized (engineLock) {
                return session == 0 || state != HANDSHAKE_DONE ? 0 : Tls.nAvailable(session);
            }
        }

        public void close() throws IOException {
            SslSocketImpl.this.close();
        }
    }

    private final class AppOutput extends OutputStream {
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            startHandshake();
            synchronized (writeLock) {
                while (len > 0) {
                    int n;
                    synchronized (engineLock) {
                        n = Tls.nWrite(handle(), b, off, len);
                    }
                    off += n;
                    len -= n;
                    sendPending();
                }
            }
        }

        public void close() throws IOException {
            SslSocketImpl.this.close();
        }
    }

    public void close() throws IOException {
        synchronized (engineLock) {
            if (closed) return;
            closed = true;
        }
        try {
            if (state == HANDSHAKE_DONE) {
                synchronized (engineLock) {
                    if (session != 0) Tls.nCloseNotify(session);
                }
                sendPending();
            }
        } catch (IOException ignored) {
            // the transport may already be gone
        } finally {
            synchronized (engineLock) {
                if (session != 0) {
                    Tls.nFree(session);
                    session = 0;
                }
            }
            if (autoClose || state != HANDSHAKE_DONE) transport.close();
        }
    }

    // ---- SSLSocket ---------------------------------------------------------------------------------------------

    public String[] getSupportedCipherSuites() {
        return CipherSuites.SUPPORTED.clone();
    }

    public String[] getEnabledCipherSuites() {
        return enabledSuites.clone();
    }

    public void setEnabledCipherSuites(String[] suites) {
        enabledSuites = CipherSuites.checkedSuites(suites);
    }

    public String[] getSupportedProtocols() {
        return CipherSuites.PROTOCOLS.clone();
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols.clone();
    }

    public void setEnabledProtocols(String[] protocols) {
        enabledProtocols = CipherSuites.checkedProtocols(protocols);
    }

    public SSLSession getSession() {
        try {
            startHandshake();
        } catch (IOException e) {
            return SslSessionImpl.nullSession(peerHost, peerPort);
        }
        sslSession.touch();
        return sslSession;
    }

    public SSLSession getHandshakeSession() {
        return state == HANDSHAKE_NEW ? handshakeSession : null;
    }

    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        if (listener == null) throw new IllegalArgumentException("Provided listener is null");
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        if (listener == null) throw new IllegalArgumentException("Provided listener is null");
        synchronized (listeners) {
            if (!listeners.remove(listener)) throw new IllegalArgumentException("Provided listener is not registered");
        }
    }

    public void setUseClientMode(boolean mode) {
        if (state != HANDSHAKE_NEW || session != 0) throw new IllegalArgumentException("Could not change the mode after the initial handshake has begun.");
        useClientMode = mode;
    }

    public boolean getUseClientMode() {
        return useClientMode;
    }

    public void setNeedClientAuth(boolean need) {
    }

    public boolean getNeedClientAuth() {
        return false;
    }

    public void setWantClientAuth(boolean want) {
    }

    public boolean getWantClientAuth() {
        return false;
    }

    public void setEnableSessionCreation(boolean flag) {
        enableSessionCreation = flag;
    }

    public boolean getEnableSessionCreation() {
        return enableSessionCreation;
    }

    public SSLParameters getSSLParameters() {
        SSLParameters p = super.getSSLParameters();
        p.setApplicationProtocols(alpn);
        p.setEndpointIdentificationAlgorithm(endpointIdentification);
        if (serverNames != null) {
            p.setServerNames(serverNames);
        } else {
            String sni = sniHost();
            if (sni != null) {
                List<SNIServerName> l = new ArrayList<SNIServerName>();
                l.add(new SNIHostName(sni));
                p.setServerNames(l);
            }
        }
        return p;
    }

    public void setSSLParameters(SSLParameters params) {
        super.setSSLParameters(params);
        alpn = params.getApplicationProtocols();
        endpointIdentification = params.getEndpointIdentificationAlgorithm();
        if (params.getServerNames() != null) serverNames = params.getServerNames();
    }

    /** The protocol ALPN chose: null before the handshake, "" when none was negotiated. */
    public String getApplicationProtocol() {
        if (state != HANDSHAKE_DONE) return null;
        return applicationProtocol == null ? "" : applicationProtocol;
    }

    public String getHandshakeApplicationProtocol() {
        return null;
    }

    /** android.net.ssl.SSLSockets.setUseSessionTickets: sessions are not resumed yet, so this is a setting only. */
    public void setUseSessionTickets(boolean use) {
    }

    // ---- Socket, delegated to the transport --------------------------------------------------------------------

    public InetAddress getInetAddress() {
        return transport.getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return transport.getLocalAddress();
    }

    public int getPort() {
        return transport.getPort();
    }

    public int getLocalPort() {
        return transport.getLocalPort();
    }

    public SocketAddress getRemoteSocketAddress() {
        return transport.getRemoteSocketAddress();
    }

    public SocketAddress getLocalSocketAddress() {
        return transport.getLocalSocketAddress();
    }

    public boolean isConnected() {
        return transport.isConnected();
    }

    public boolean isBound() {
        return transport.isBound();
    }

    public boolean isClosed() {
        return closed || transport.isClosed();
    }

    public boolean isInputShutdown() {
        return transport.isInputShutdown();
    }

    public boolean isOutputShutdown() {
        return transport.isOutputShutdown();
    }

    public void shutdownInput() throws IOException {
        throw new UnsupportedOperationException("The method shutdownInput() is not supported in SSLSocket");
    }

    public void shutdownOutput() throws IOException {
        throw new UnsupportedOperationException("The method shutdownOutput() is not supported in SSLSocket");
    }

    public void setSoTimeout(int timeout) throws SocketException {
        transport.setSoTimeout(timeout);
    }

    public int getSoTimeout() throws SocketException {
        return transport.getSoTimeout();
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        transport.setTcpNoDelay(on);
    }

    public boolean getTcpNoDelay() throws SocketException {
        return transport.getTcpNoDelay();
    }

    public void setKeepAlive(boolean on) throws SocketException {
        transport.setKeepAlive(on);
    }

    public boolean getKeepAlive() throws SocketException {
        return transport.getKeepAlive();
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        transport.setSoLinger(on, linger);
    }

    public int getSoLinger() throws SocketException {
        return transport.getSoLinger();
    }

    public void setSendBufferSize(int size) throws SocketException {
        transport.setSendBufferSize(size);
    }

    public int getSendBufferSize() throws SocketException {
        return transport.getSendBufferSize();
    }

    public void setReceiveBufferSize(int size) throws SocketException {
        transport.setReceiveBufferSize(size);
    }

    public int getReceiveBufferSize() throws SocketException {
        return transport.getReceiveBufferSize();
    }

    public void setTrafficClass(int tc) throws SocketException {
        transport.setTrafficClass(tc);
    }

    public int getTrafficClass() throws SocketException {
        return transport.getTrafficClass();
    }

    public void setReuseAddress(boolean on) throws SocketException {
        transport.setReuseAddress(on);
    }

    public boolean getReuseAddress() throws SocketException {
        return transport.getReuseAddress();
    }

    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Method sendUrgentData() is not supported.");
    }

    public void setOOBInline(boolean on) throws SocketException {
        throw new SocketException("Method setOOBInline() is not supported.");
    }

    public java.nio.channels.SocketChannel getChannel() {
        return null;
    }

    public String toString() {
        return "SSL socket over " + transport;
    }
}
