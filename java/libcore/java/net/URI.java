package java.net;

public final class URI implements Comparable<URI>, java.io.Serializable {
    private final String string;
    private String scheme, authority, userInfo, host, path, query, fragment, schemeSpecificPart;
    private int port = -1;

    public URI(String str) throws URISyntaxException {
        if (str == null) {
            throw new NullPointerException();
        }
        this.string = str;
        parse(str);
    }

    public URI(String scheme, String ssp, String fragment) throws URISyntaxException {
        this((scheme != null ? scheme + ":" : "") + ssp + (fragment != null ? "#" + fragment : ""));
    }

    public URI(String scheme, String userInfo, String host, int port, String path, String query, String fragment)
            throws URISyntaxException {
        this(build(scheme, userInfo, host, port, path, query, fragment));
    }

    public URI(String scheme, String host, String path, String fragment) throws URISyntaxException {
        this(scheme, null, host, -1, path, null, fragment);
    }

    public URI(String scheme, String authority, String path, String query, String fragment) throws URISyntaxException {
        this((scheme != null ? scheme + ":" : "") + (authority != null ? "//" + authority : "") + (path != null ? path : "")
                + (query != null ? "?" + query : "") + (fragment != null ? "#" + fragment : ""));
    }

    private static String build(String scheme, String userInfo, String host, int port, String path, String query,
            String fragment) {
        StringBuilder sb = new StringBuilder();
        if (scheme != null) {
            sb.append(scheme).append(':');
        }
        if (host != null) {
            sb.append("//");
            if (userInfo != null) {
                sb.append(userInfo).append('@');
            }
            sb.append(host);
            if (port != -1) {
                sb.append(':').append(port);
            }
        }
        if (path != null) {
            sb.append(path);
        }
        if (query != null) {
            sb.append('?').append(query);
        }
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    public static URI create(String str) {
        try {
            return new URI(str);
        } catch (URISyntaxException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    private void parse(String s) throws URISyntaxException {
        int hash = s.indexOf('#');
        if (hash >= 0) {
            fragment = s.substring(hash + 1);
            s = s.substring(0, hash);
        }
        int colon = s.indexOf(':');
        int slash = s.indexOf('/');
        if (colon > 0 && (slash < 0 || colon < slash)) {
            scheme = s.substring(0, colon);
            s = s.substring(colon + 1);
        }
        schemeSpecificPart = s;
        int q = s.indexOf('?');
        if (q >= 0) {
            query = s.substring(q + 1);
            s = s.substring(0, q);
        }
        if (s.startsWith("//")) {
            int end = s.indexOf('/', 2);
            authority = end < 0 ? s.substring(2) : s.substring(2, end);
            s = end < 0 ? "" : s.substring(end);
            String hp = authority;
            int at = hp.lastIndexOf('@');
            if (at >= 0) {
                userInfo = hp.substring(0, at);
                hp = hp.substring(at + 1);
            }
            int pc = hp.lastIndexOf(':');
            if (pc >= 0 && hp.indexOf(']') < pc) {
                try {
                    port = Integer.parseInt(hp.substring(pc + 1));
                } catch (NumberFormatException e) {
                    throw new URISyntaxException(string, "Invalid port");
                }
                hp = hp.substring(0, pc);
            }
            host = hp.isEmpty() ? null : hp;
        }
        path = s;
    }

    public String getScheme() {
        return scheme;
    }

    public boolean isAbsolute() {
        return scheme != null;
    }

    public boolean isOpaque() {
        return path == null;
    }

    public String getSchemeSpecificPart() {
        return schemeSpecificPart;
    }

    public String getRawSchemeSpecificPart() {
        return schemeSpecificPart;
    }

    public String getAuthority() {
        return authority;
    }

    public String getRawAuthority() {
        return authority;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public String getRawUserInfo() {
        return userInfo;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path == null ? null : URLDecoder.decode(path);
    }

    public String getRawPath() {
        return path;
    }

    public String getQuery() {
        return query == null ? null : URLDecoder.decode(query);
    }

    public String getRawQuery() {
        return query;
    }

    public String getFragment() {
        return fragment;
    }

    public String getRawFragment() {
        return fragment;
    }

    public URI resolve(String str) {
        return resolve(create(str));
    }

    public URI resolve(URI uri) {
        if (uri.isAbsolute()) {
            return uri;
        }
        String base = string;
        if (uri.string.startsWith("/")) {
            int i = base.indexOf("//");
            int j = i >= 0 ? base.indexOf('/', i + 2) : base.indexOf('/');
            return create((j >= 0 ? base.substring(0, j) : base) + uri.string);
        }
        int last = base.lastIndexOf('/');
        return create((last >= 0 ? base.substring(0, last + 1) : base) + uri.string);
    }

    public URI normalize() {
        return this;
    }

    public URI relativize(URI uri) {
        if (uri.string.startsWith(string)) {
            return create(uri.string.substring(string.length()));
        }
        return uri;
    }

    public URL toURL() throws MalformedURLException {
        return new URL(string);
    }

    public int compareTo(URI that) {
        return string.compareTo(that.string);
    }

    public boolean equals(Object ob) {
        return ob instanceof URI && ((URI) ob).string.equals(string);
    }

    public int hashCode() {
        return string.hashCode();
    }

    public String toString() {
        return string;
    }

    public String toASCIIString() {
        return string;
    }
}
