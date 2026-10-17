package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * HttpsURLConnection behind URL.openConnection() for https URLs: the HTTP/1.1 client of HttpURLConnectionImpl over a
 * TLS socket from getSSLSocketFactory(), with the peer checked by getHostnameVerifier() after the handshake (as
 * Android's OkHttp does). Every URLConnection method goes to that client.
 */
final class HttpsURLConnectionImpl extends HttpsURLConnection {
    private final HttpURLConnectionImpl http;
    private SSLSession session;

    HttpsURLConnectionImpl(URL u) {
        super(u);
        http = new HttpURLConnectionImpl(u, this);
    }

    /** Called by the client for each connection: TLS over the connected socket, checked against the host name. */
    Socket secure(Socket plain, String host, int port) throws IOException {
        SSLSocket s = (SSLSocket) getSSLSocketFactory().createSocket(plain, host, port, true);
        try {
            s.startHandshake();
            SSLSession ss = s.getSession();
            if (!getHostnameVerifier().verify(host, ss)) {
                throw new SSLPeerUnverifiedException("Hostname " + host + " not verified:\n    certificate: "
                        + describe(ss));
            }
            session = ss;
            return s;
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    private static String describe(SSLSession ss) {
        try {
            Certificate c = ss.getPeerCertificates()[0];
            return c instanceof java.security.cert.X509Certificate
                    ? ((java.security.cert.X509Certificate) c).getSubjectX500Principal().getName() : c.getType();
        } catch (SSLPeerUnverifiedException e) {
            return "none";
        }
    }

    private SSLSession session() {
        if (session == null) throw new IllegalStateException("Connection has not yet been established");
        return session;
    }

    public String getCipherSuite() {
        return session().getCipherSuite();
    }

    public Certificate[] getLocalCertificates() {
        return session().getLocalCertificates();
    }

    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        return session().getPeerCertificates();
    }

    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return session().getPeerPrincipal();
    }

    public Principal getLocalPrincipal() {
        return session().getLocalPrincipal();
    }

    // ---- everything else is the HTTP client's ----

    public void connect() throws IOException {
        http.connect();
        connected = true;
    }

    public void disconnect() {
        http.disconnect();
    }

    public boolean usingProxy() {
        return http.usingProxy();
    }

    public URL getURL() {
        return http.getURL();
    }

    public void setConnectTimeout(int timeout) {
        http.setConnectTimeout(timeout);
    }

    public int getConnectTimeout() {
        return http.getConnectTimeout();
    }

    public void setReadTimeout(int timeout) {
        http.setReadTimeout(timeout);
    }

    public int getReadTimeout() {
        return http.getReadTimeout();
    }

    public int getContentLength() {
        return http.getContentLength();
    }

    public long getContentLengthLong() {
        return http.getContentLengthLong();
    }

    public String getContentType() {
        return http.getContentType();
    }

    public String getContentEncoding() {
        return http.getContentEncoding();
    }

    public long getExpiration() {
        return http.getExpiration();
    }

    public long getDate() {
        return http.getDate();
    }

    public long getLastModified() {
        return http.getLastModified();
    }

    public String getHeaderField(String name) {
        return http.getHeaderField(name);
    }

    public Map<String, List<String>> getHeaderFields() {
        return http.getHeaderFields();
    }

    public String getHeaderField(int n) {
        return http.getHeaderField(n);
    }

    public String getHeaderFieldKey(int n) {
        return http.getHeaderFieldKey(n);
    }

    public long getHeaderFieldDate(String name, long Default) {
        return http.getHeaderFieldDate(name, Default);
    }

    public int getHeaderFieldInt(String name, int Default) {
        return http.getHeaderFieldInt(name, Default);
    }

    public long getHeaderFieldLong(String name, long Default) {
        return http.getHeaderFieldLong(name, Default);
    }

    public Object getContent() throws IOException {
        return http.getContent();
    }

    public Object getContent(Class[] classes) throws IOException {
        return http.getContent(classes);
    }

    public java.security.Permission getPermission() throws IOException {
        return http.getPermission();
    }

    public InputStream getInputStream() throws IOException {
        return http.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return http.getOutputStream();
    }

    public InputStream getErrorStream() {
        return http.getErrorStream();
    }

    public void setDoInput(boolean doinput) {
        http.setDoInput(doinput);
    }

    public boolean getDoInput() {
        return http.getDoInput();
    }

    public void setDoOutput(boolean dooutput) {
        http.setDoOutput(dooutput);
    }

    public boolean getDoOutput() {
        return http.getDoOutput();
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
        http.setAllowUserInteraction(allowuserinteraction);
    }

    public boolean getAllowUserInteraction() {
        return http.getAllowUserInteraction();
    }

    public void setUseCaches(boolean usecaches) {
        http.setUseCaches(usecaches);
    }

    public boolean getUseCaches() {
        return http.getUseCaches();
    }

    public void setIfModifiedSince(long ifmodifiedsince) {
        http.setIfModifiedSince(ifmodifiedsince);
    }

    public long getIfModifiedSince() {
        return http.getIfModifiedSince();
    }

    public boolean getDefaultUseCaches() {
        return http.getDefaultUseCaches();
    }

    public void setDefaultUseCaches(boolean defaultusecaches) {
        http.setDefaultUseCaches(defaultusecaches);
    }

    public void setRequestProperty(String key, String value) {
        http.setRequestProperty(key, value);
    }

    public void addRequestProperty(String key, String value) {
        http.addRequestProperty(key, value);
    }

    public String getRequestProperty(String key) {
        return http.getRequestProperty(key);
    }

    public Map<String, List<String>> getRequestProperties() {
        return http.getRequestProperties();
    }

    public void setInstanceFollowRedirects(boolean followRedirects) {
        http.setInstanceFollowRedirects(followRedirects);
    }

    public boolean getInstanceFollowRedirects() {
        return http.getInstanceFollowRedirects();
    }

    public void setRequestMethod(String method) throws ProtocolException {
        http.setRequestMethod(method);
    }

    public String getRequestMethod() {
        return http.getRequestMethod();
    }

    public int getResponseCode() throws IOException {
        return http.getResponseCode();
    }

    public String getResponseMessage() throws IOException {
        return http.getResponseMessage();
    }

    public void setAuthenticator(Authenticator auth) {
        http.setAuthenticator(auth);
    }

    public void setChunkedStreamingMode(int chunklen) {
        http.setChunkedStreamingMode(chunklen);
    }

    public void setFixedLengthStreamingMode(int contentLength) {
        http.setFixedLengthStreamingMode(contentLength);
    }

    public void setFixedLengthStreamingMode(long contentLength) {
        http.setFixedLengthStreamingMode(contentLength);
    }

    public String toString() {
        return http.toString();
    }
}
