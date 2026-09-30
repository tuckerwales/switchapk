package java.net;

import java.io.IOException;
import java.io.InputStream;

public abstract class HttpURLConnection extends URLConnection {
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_ACCEPTED = 202;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_MOVED_PERM = 301;
    public static final int HTTP_MOVED_TEMP = 302;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_INTERNAL_ERROR = 500;
    public static final int HTTP_UNAVAILABLE = 503;

    protected String method = "GET";
    protected int responseCode = -1;
    protected String responseMessage;
    protected boolean instanceFollowRedirects = true;
    private static boolean followRedirects = true;

    protected HttpURLConnection(URL u) {
        super(u);
    }

    public static void setFollowRedirects(boolean set) {
        followRedirects = set;
    }

    public static boolean getFollowRedirects() {
        return followRedirects;
    }

    public void setInstanceFollowRedirects(boolean followRedirects) {
        instanceFollowRedirects = followRedirects;
    }

    public boolean getInstanceFollowRedirects() {
        return instanceFollowRedirects;
    }

    public void setRequestMethod(String method) throws ProtocolException {
        this.method = method;
    }

    public String getRequestMethod() {
        return method;
    }

    public int getResponseCode() throws IOException {
        connect();
        return responseCode;
    }

    public String getResponseMessage() throws IOException {
        connect();
        return responseMessage;
    }

    public abstract void disconnect();

    public abstract boolean usingProxy();

    public InputStream getErrorStream() {
        return null;
    }

    public void setChunkedStreamingMode(int chunklen) {
    }

    public void setFixedLengthStreamingMode(int contentLength) {
    }

    public void setFixedLengthStreamingMode(long contentLength) {
    }

    /** Networking is unavailable in this runtime; connections fail like an offline device. */
    static final class Impl extends HttpURLConnection {
        Impl(URL u) {
            super(u);
        }

        public void connect() throws IOException {
            throw new UnknownHostException("Unable to resolve host \"" + url.getHost() + "\": No address associated with hostname");
        }

        public void disconnect() {
        }

        public boolean usingProxy() {
            return false;
        }

        public InputStream getInputStream() throws IOException {
            connect();
            return null;
        }
    }
}
