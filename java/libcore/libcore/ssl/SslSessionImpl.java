package libcore.ssl;

import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;

/** A client session: what the handshake agreed on. Sessions are not resumed yet, so each socket has its own. */
final class SslSessionImpl implements SSLSession {
    private final SessionContextImpl context;
    private final byte[] id;
    private final long creationTime = System.currentTimeMillis();
    private long lastAccessedTime = creationTime;
    private final X509Certificate[] peerCertificates;
    private final String cipherSuite;
    private final String protocol;
    private final String peerHost;
    private final int peerPort;
    private boolean valid = true;
    private final Map<String, Object> values = new HashMap<String, Object>();

    SslSessionImpl(SessionContextImpl context, byte[] id, X509Certificate[] peerCertificates, String cipherSuite,
            String protocol, String peerHost, int peerPort) {
        this.context = context;
        this.id = id;
        this.peerCertificates = peerCertificates;
        this.cipherSuite = cipherSuite;
        this.protocol = protocol;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
    }

    /** The session a failed or unfinished handshake reports, as Conscrypt's null session. */
    static SslSessionImpl nullSession(String peerHost, int peerPort) {
        SslSessionImpl s = new SslSessionImpl(null, new byte[0], null, "SSL_NULL_WITH_NULL_NULL", "NONE", peerHost,
                peerPort);
        s.valid = false;
        return s;
    }

    public byte[] getId() {
        return id.clone();
    }

    public SSLSessionContext getSessionContext() {
        return context;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    void touch() {
        lastAccessedTime = System.currentTimeMillis();
    }

    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    public void putValue(String name, Object value) {
        if (name == null || value == null) throw new IllegalArgumentException("name == null || value == null");
        Object old;
        synchronized (values) {
            old = values.put(name, value);
        }
        if (value instanceof SSLSessionBindingListener) {
            ((SSLSessionBindingListener) value).valueBound(new SSLSessionBindingEvent(this, name));
        }
        if (old instanceof SSLSessionBindingListener) {
            ((SSLSessionBindingListener) old).valueUnbound(new SSLSessionBindingEvent(this, name));
        }
    }

    public Object getValue(String name) {
        if (name == null) throw new IllegalArgumentException("name == null");
        synchronized (values) {
            return values.get(name);
        }
    }

    public void removeValue(String name) {
        if (name == null) throw new IllegalArgumentException("name == null");
        Object old;
        synchronized (values) {
            old = values.remove(name);
        }
        if (old instanceof SSLSessionBindingListener) {
            ((SSLSessionBindingListener) old).valueUnbound(new SSLSessionBindingEvent(this, name));
        }
    }

    public String[] getValueNames() {
        synchronized (values) {
            return values.keySet().toArray(new String[0]);
        }
    }

    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        if (peerCertificates == null || peerCertificates.length == 0) {
            throw new SSLPeerUnverifiedException("No peer certificates");
        }
        return peerCertificates.clone();
    }

    X509Certificate[] peerChain() {
        return peerCertificates;
    }

    public Certificate[] getLocalCertificates() {
        return null;
    }

    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        getPeerCertificates();
        return peerCertificates[0].getSubjectX500Principal();
    }

    public Principal getLocalPrincipal() {
        return null;
    }

    public String getCipherSuite() {
        return cipherSuite;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public int getPacketBufferSize() {
        return 16709; // a TLS 1.2 record: 5 header bytes, 2^14 data, compression and MAC/padding room
    }

    public int getApplicationBufferSize() {
        return 16384;
    }

    public String toString() {
        return "SSLSession(" + protocol + ", " + cipherSuite + ")";
    }
}
