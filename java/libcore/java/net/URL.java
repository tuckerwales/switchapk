package java.net;

public final class URL implements java.io.Serializable {
    private final String spec;
    private final URI uri;

    public URL(String spec) throws MalformedURLException {
        this.spec = spec;
        try {
            uri = new URI(spec);
        } catch (URISyntaxException e) {
            throw new MalformedURLException(e.getMessage());
        }
        if (uri.getScheme() == null) {
            throw new MalformedURLException("no protocol: " + spec);
        }
    }

    public URL(URL context, String spec) throws MalformedURLException {
        this(context == null ? spec : context.uri.resolve(spec).toString());
    }

    public URL(String protocol, String host, int port, String file) throws MalformedURLException {
        this(protocol + "://" + host + (port >= 0 ? ":" + port : "") + file);
    }

    public URL(String protocol, String host, String file) throws MalformedURLException {
        this(protocol, host, -1, file);
    }

    public String getProtocol() {
        return uri.getScheme();
    }

    public String getHost() {
        return uri.getHost() == null ? "" : uri.getHost();
    }

    public int getPort() {
        return uri.getPort();
    }

    public int getDefaultPort() {
        String p = getProtocol();
        return p.equals("http") ? 80 : p.equals("https") ? 443 : -1;
    }

    public String getPath() {
        return uri.getRawPath() == null ? "" : uri.getRawPath();
    }

    public String getFile() {
        String q = uri.getRawQuery();
        return getPath() + (q != null ? "?" + q : "");
    }

    public String getQuery() {
        return uri.getRawQuery();
    }

    public String getRef() {
        return uri.getFragment();
    }

    public String getAuthority() {
        return uri.getAuthority();
    }

    public String getUserInfo() {
        return uri.getUserInfo();
    }

    public URI toURI() throws URISyntaxException {
        return uri;
    }

    public String toExternalForm() {
        return spec;
    }

    public URLConnection openConnection() throws java.io.IOException {
        if (getProtocol().equals("file")) {
            return new FileURLConnection(this);
        }
        String p = getProtocol();
        if (p.equals("http") || p.equals("https")) {
            return new HttpURLConnectionImpl(this);
        }
        throw new java.net.MalformedURLException("unknown protocol: " + p);
    }

    public URLConnection openConnection(Proxy proxy) throws java.io.IOException {
        return openConnection();
    }

    public final java.io.InputStream openStream() throws java.io.IOException {
        return openConnection().getInputStream();
    }

    public boolean sameFile(URL other) {
        return spec.equals(other.spec);
    }

    public boolean equals(Object obj) {
        return obj instanceof URL && ((URL) obj).spec.equals(spec);
    }

    public int hashCode() {
        return spec.hashCode();
    }

    public String toString() {
        return spec;
    }

    static final class FileURLConnection extends URLConnection {
        FileURLConnection(URL u) {
            super(u);
        }

        public void connect() {
            connected = true;
        }

        public java.io.InputStream getInputStream() throws java.io.IOException {
            return new java.io.FileInputStream(url.getPath());
        }
    }
}
