package javax.net.ssl;

import java.security.Principal;
import java.security.cert.Certificate;
import java.util.EventObject;

public class HandshakeCompletedEvent extends EventObject {
    private transient SSLSession session;

    public HandshakeCompletedEvent(SSLSocket sock, SSLSession s) {
        super(sock);
        session = s;
    }

    public SSLSession getSession() {
        return session;
    }

    public String getCipherSuite() {
        return session.getCipherSuite();
    }

    public Certificate[] getLocalCertificates() {
        return session.getLocalCertificates();
    }

    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return session.getPeerCertificates();
    }

    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return session.getPeerPrincipal();
    }

    public Principal getLocalPrincipal() {
        return session.getLocalPrincipal();
    }

    public SSLSocket getSocket() {
        return (SSLSocket) getSource();
    }
}
