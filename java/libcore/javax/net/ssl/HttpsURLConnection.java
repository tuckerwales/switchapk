package javax.net.ssl;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;

public abstract class HttpsURLConnection extends HttpURLConnection {
    private static HostnameVerifier defaultHostnameVerifier;
    private static SSLSocketFactory defaultSSLSocketFactory;

    protected HostnameVerifier hostnameVerifier;
    private SSLSocketFactory sslSocketFactory;

    protected HttpsURLConnection(URL url) {
        super(url);
        hostnameVerifier = getDefaultHostnameVerifier();
        sslSocketFactory = getDefaultSSLSocketFactory();
    }

    public abstract String getCipherSuite();

    public abstract Certificate[] getLocalCertificates();

    public abstract Certificate[] getServerCertificates() throws SSLPeerUnverifiedException;

    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        Certificate[] certs = getServerCertificates();
        return ((X509Certificate) certs[0]).getSubjectX500Principal();
    }

    public Principal getLocalPrincipal() {
        Certificate[] certs = getLocalCertificates();
        return certs == null || certs.length == 0 ? null : ((X509Certificate) certs[0]).getSubjectX500Principal();
    }

    public static synchronized void setDefaultHostnameVerifier(HostnameVerifier v) {
        if (v == null) throw new IllegalArgumentException("no default HostnameVerifier specified");
        defaultHostnameVerifier = v;
    }

    public static synchronized HostnameVerifier getDefaultHostnameVerifier() {
        if (defaultHostnameVerifier == null) defaultHostnameVerifier = libcore.ssl.HostnameVerifierImpl.INSTANCE;
        return defaultHostnameVerifier;
    }

    public void setHostnameVerifier(HostnameVerifier v) {
        if (v == null) throw new IllegalArgumentException("no HostnameVerifier specified");
        hostnameVerifier = v;
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public static synchronized void setDefaultSSLSocketFactory(SSLSocketFactory sf) {
        if (sf == null) throw new IllegalArgumentException("no default SSLSocketFactory specified");
        defaultSSLSocketFactory = sf;
    }

    public static synchronized SSLSocketFactory getDefaultSSLSocketFactory() {
        if (defaultSSLSocketFactory == null) defaultSSLSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return defaultSSLSocketFactory;
    }

    public void setSSLSocketFactory(SSLSocketFactory sf) {
        if (sf == null) throw new IllegalArgumentException("no SSLSocketFactory specified");
        sslSocketFactory = sf;
    }

    public SSLSocketFactory getSSLSocketFactory() {
        return sslSocketFactory;
    }

    public Optional<SSLSession> getSSLSession() {
        throw new UnsupportedOperationException();
    }
}
