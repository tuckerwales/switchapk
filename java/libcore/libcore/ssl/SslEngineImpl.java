package libcore.ssl;

import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A TLS client SSLEngine over a libcore.ssl.Tls session. unwrap() takes one whole TLS record per call (and reports
 * BUFFER_UNDERFLOW, consuming nothing, until one is there), so no plaintext waits inside the engine for input that
 * may never come; wrap() turns at most one record's worth of application data into a record. Certificates are
 * checked synchronously inside unwrap (no delegated tasks).
 */
final class SslEngineImpl extends SSLEngine {
    private static final int RECORD_HEADER = 5;
    private static final int MAX_RECORD = 16384 + 2048;
    private static final int RECORD_OVERHEAD = 256; // header, explicit IV, MAC or tag, CBC padding
    private static final int MAX_PLAINTEXT = 16384;

    private final SslContextImpl context;
    private long session;
    private boolean begun, handshakeDone, finishedReported, verified;
    private boolean inboundDone, outboundClosed, outboundDone;
    private SslSessionImpl sslSession;
    private SslSessionImpl handshakeSession;
    private String applicationProtocol;
    private SSLException failure;

    private String[] enabledSuites = CipherSuites.SUPPORTED.clone();
    private String[] enabledProtocols = CipherSuites.PROTOCOLS.clone();
    private String[] alpn = new String[0];
    private String endpointIdentification;
    private boolean useClientMode = true;
    private boolean enableSessionCreation = true;
    private final byte[] buf = new byte[MAX_RECORD];

    SslEngineImpl(SslContextImpl context, String host, int port) {
        super(host, port);
        this.context = context;
    }

    private String sniHost() {
        String h = getPeerHost();
        if (h == null || HostnameVerifierImpl.isIpAddress(h) || h.indexOf('.') < 0) return null;
        return h;
    }

    public synchronized void beginHandshake() throws SSLException {
        if (failure != null) throw failure;
        if (outboundClosed || inboundDone) throw new SSLException("Engine is closed");
        if (!useClientMode) throw new SSLException("TLS server mode is not supported yet");
        if (begun) return;
        begun = true;
        session = Tls.nNew(sniHost(), alpn.length > 0 ? alpn : null, CipherSuites.toMbedtls(enabledSuites));
        step();
    }

    /** Runs the handshake as far as the input allows, checking the peer when it asks to. */
    private void step() throws SSLException {
        try {
            for (;;) {
                int st = Tls.nHandshake(session);
                if (st == 0) {
                    finishHandshake();
                    return;
                }
                if (st == Tls.WANT_READ) return;
                verifyPeer();
            }
        } catch (SSLException e) {
            throw fail(e);
        } catch (java.io.IOException e) {
            throw fail(new SSLHandshakeException(e.toString()));
        }
    }

    private SSLException fail(SSLException e) {
        failure = e;
        return e;
    }

    private void verifyPeer() throws SSLException, java.io.IOException {
        Object[] ders = Tls.nPeerCerts(session);
        String suite = CipherSuites.fromMbedtls(Tls.nCipherSuite(session));
        X509Certificate[] chain = new X509Certificate[ders.length];
        try {
            for (int i = 0; i < ders.length; i++) chain[i] = TrustManagerImpl.parse((byte[]) ders[i]);
            handshakeSession = new SslSessionImpl(null, new byte[0], chain, suite, Tls.nProtocol(session), getPeerHost(),
                    getPeerPort());
            X509TrustManager tm = context.trustManager();
            String authType = CipherSuites.authType(suite);
            if (tm instanceof X509ExtendedTrustManager) {
                ((X509ExtendedTrustManager) tm).checkServerTrusted(chain.clone(), authType, this);
            } else {
                tm.checkServerTrusted(chain.clone(), authType);
                TrustManagerImpl.checkEndpoint(chain[0], getSSLParameters(), handshakeSession);
            }
        } catch (CertificateException e) {
            throw reject(e);
        } catch (RuntimeException e) {
            throw reject(e);
        }
        Tls.nVerified(session);
        verified = true;
    }

    private SSLHandshakeException reject(Exception cause) {
        Tls.nAlert(session, Tls.ALERT_CERTIFICATE_UNKNOWN);
        SSLHandshakeException e = new SSLHandshakeException(cause.toString());
        e.initCause(cause);
        return e;
    }

    private void finishHandshake() {
        X509Certificate[] chain = handshakeSession != null ? handshakeSession.peerChain() : null;
        applicationProtocol = Tls.nAlpn(session);
        sslSession = new SslSessionImpl(context.clientSessions(), Tls.nSessionId(session), chain,
                CipherSuites.fromMbedtls(Tls.nCipherSuite(session)), Tls.nProtocol(session), getPeerHost(), getPeerPort());
        handshakeDone = true;
    }

    private HandshakeStatus status() {
        if (session != 0 && Tls.nPending(session) > 0) return HandshakeStatus.NEED_WRAP;
        if (failure != null) return HandshakeStatus.NOT_HANDSHAKING;
        if (handshakeDone && !finishedReported) {
            finishedReported = true;
            return HandshakeStatus.FINISHED;
        }
        if (!begun || handshakeDone || outboundDone || inboundDone) return HandshakeStatus.NOT_HANDSHAKING;
        return HandshakeStatus.NEED_UNWRAP;
    }

    public synchronized HandshakeStatus getHandshakeStatus() {
        if (session != 0 && Tls.nPending(session) > 0) return HandshakeStatus.NEED_WRAP;
        if (!begun || handshakeDone || failure != null || outboundDone || inboundDone) {
            return HandshakeStatus.NOT_HANDSHAKING;
        }
        return HandshakeStatus.NEED_UNWRAP;
    }

    private static int remaining(ByteBuffer[] bufs, int off, int len) {
        long n = 0;
        for (int i = off; i < off + len; i++) n += bufs[i].remaining();
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    public synchronized SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst)
            throws SSLException {
        checkBuffers(srcs, offset, length, dst);
        if (dst.isReadOnly()) throw new java.nio.ReadOnlyBufferException();
        if (!begun && !outboundClosed) beginHandshake();
        int produced = 0, consumed = 0;
        if (session != 0) {
            int pending = Tls.nPending(session);
            if (pending > 0) {
                if (dst.remaining() < Math.min(pending, buf.length)) {
                    return new SSLEngineResult(Status.BUFFER_OVERFLOW, getHandshakeStatus(), 0, 0);
                }
                int n = Tls.nDrain(session, buf, 0, Math.min(dst.remaining(), buf.length));
                dst.put(buf, 0, n);
                produced = n;
            } else if (handshakeDone && !outboundClosed && failure == null) {
                int want = Math.min(remaining(srcs, offset, length), MAX_PLAINTEXT);
                if (want > 0) {
                    if (dst.remaining() < want + RECORD_OVERHEAD) {
                        return new SSLEngineResult(Status.BUFFER_OVERFLOW, getHandshakeStatus(), 0, 0);
                    }
                    byte[] plain = gather(srcs, offset, length, want);
                    int took = Tls.nWrite(session, plain, 0, want);
                    unread(srcs, offset, length, want - took);
                    consumed = took;
                    int n = Tls.nDrain(session, buf, 0, Math.min(dst.remaining(), buf.length));
                    dst.put(buf, 0, n);
                    produced = n;
                }
            }
        }
        if (failure != null && produced == 0) throw failure;
        if (outboundClosed && (session == 0 || Tls.nPending(session) == 0)) {
            outboundDone = true;
            release();
            return new SSLEngineResult(Status.CLOSED, status(), consumed, produced);
        }
        return new SSLEngineResult(Status.OK, status(), consumed, produced);
    }

    private static byte[] gather(ByteBuffer[] srcs, int off, int len, int n) {
        byte[] out = new byte[n];
        int at = 0;
        for (int i = off; i < off + len && at < n; i++) {
            int k = Math.min(srcs[i].remaining(), n - at);
            srcs[i].get(out, at, k);
            at += k;
        }
        return out;
    }

    /** Puts back the last n gathered bytes the session did not take. */
    private static void unread(ByteBuffer[] srcs, int off, int len, int n) {
        for (int i = off + len - 1; i >= off && n > 0; i--) {
            int k = Math.min(srcs[i].position(), n);
            srcs[i].position(srcs[i].position() - k);
            n -= k;
        }
    }

    public synchronized SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length)
            throws SSLException {
        checkBuffers(dsts, offset, length, src);
        for (int i = offset; i < offset + length; i++) {
            if (dsts[i].isReadOnly()) throw new java.nio.ReadOnlyBufferException();
        }
        if (inboundDone) return new SSLEngineResult(Status.CLOSED, status(), 0, 0);
        if (!begun) beginHandshake();
        if (failure != null) throw failure;
        if (src.remaining() < RECORD_HEADER) return new SSLEngineResult(Status.BUFFER_UNDERFLOW, status(), 0, 0);
        int p = src.position();
        int recordLen = RECORD_HEADER + (((src.get(p + 3) & 0xff) << 8) | (src.get(p + 4) & 0xff));
        if (recordLen > MAX_RECORD) throw fail(new SSLException("Record too large: " + recordLen));
        if (src.remaining() < recordLen) return new SSLEngineResult(Status.BUFFER_UNDERFLOW, status(), 0, 0);
        if (handshakeDone && remaining(dsts, offset, length) < Math.min(recordLen, MAX_PLAINTEXT)) {
            return new SSLEngineResult(Status.BUFFER_OVERFLOW, status(), 0, 0);
        }
        src.get(buf, 0, recordLen);
        Tls.nFeed(session, buf, 0, recordLen);
        int produced = 0;
        if (!handshakeDone) {
            step();
            if (failure != null) throw failure;
        } else {
            for (;;) {
                int room = remaining(dsts, offset, length);
                if (room == 0) break;
                int r = Tls.nRead(session, buf, 0, Math.min(room, buf.length));
                if (r == Tls.EOF) {
                    inboundDone = true;
                    if (!outboundClosed) closeOutbound();
                    return new SSLEngineResult(Status.CLOSED, status(), recordLen, produced);
                }
                if (r <= 0) break;
                scatter(dsts, offset, length, r);
                produced += r;
            }
        }
        return new SSLEngineResult(Status.OK, status(), recordLen, produced);
    }

    private void scatter(ByteBuffer[] dsts, int off, int len, int n) {
        int at = 0;
        for (int i = off; i < off + len && at < n; i++) {
            int k = Math.min(dsts[i].remaining(), n - at);
            dsts[i].put(buf, at, k);
            at += k;
        }
    }

    private static void checkBuffers(ByteBuffer[] bufs, int off, int len, ByteBuffer other) {
        if (bufs == null || other == null) throw new IllegalArgumentException("buffers == null");
        if (off < 0 || len < 0 || off > bufs.length - len) throw new IndexOutOfBoundsException();
        for (int i = off; i < off + len; i++) {
            if (bufs[i] == null) throw new IllegalArgumentException("buffers[" + i + "] == null");
        }
    }

    private void release() {
        if (session != 0 && outboundDone && inboundDone) {
            Tls.nFree(session);
            session = 0;
        }
    }

    public Runnable getDelegatedTask() {
        return null;
    }

    public synchronized void closeInbound() throws SSLException {
        inboundDone = true;
        release();
    }

    public synchronized boolean isInboundDone() {
        return inboundDone;
    }

    public synchronized void closeOutbound() {
        if (outboundClosed) return;
        outboundClosed = true;
        if (session != 0 && handshakeDone && failure == null) {
            Tls.nCloseNotify(session);
        }
        if (session == 0 || Tls.nPending(session) == 0) outboundDone = true;
    }

    public synchronized boolean isOutboundDone() {
        return outboundDone;
    }

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

    public synchronized SSLSession getSession() {
        return sslSession != null ? sslSession : SslSessionImpl.nullSession(getPeerHost(), getPeerPort());
    }

    public synchronized SSLSession getHandshakeSession() {
        return handshakeDone ? null : handshakeSession;
    }

    public void setUseClientMode(boolean mode) {
        if (begun) throw new IllegalArgumentException("Can not change mode after handshake: in progress");
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
        return p;
    }

    public void setSSLParameters(SSLParameters params) {
        super.setSSLParameters(params);
        alpn = params.getApplicationProtocols();
        endpointIdentification = params.getEndpointIdentificationAlgorithm();
    }

    public synchronized String getApplicationProtocol() {
        if (!handshakeDone) return null;
        return applicationProtocol == null ? "" : applicationProtocol;
    }

    public String getHandshakeApplicationProtocol() {
        return null;
    }
}
