package java.net;

import java.io.IOException;
import java.io.InputStream;

public abstract class HttpURLConnection extends URLConnection {
    private static boolean followRedirects = true;
    private static final String[] METHODS = {"GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE"};

    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_ACCEPTED = 202;
    public static final int HTTP_NOT_AUTHORITATIVE = 203;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_RESET = 205;
    public static final int HTTP_PARTIAL = 206;
    public static final int HTTP_MULT_CHOICE = 300;
    public static final int HTTP_MOVED_PERM = 301;
    public static final int HTTP_MOVED_TEMP = 302;
    public static final int HTTP_SEE_OTHER = 303;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_USE_PROXY = 305;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_PAYMENT_REQUIRED = 402;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_BAD_METHOD = 405;
    public static final int HTTP_NOT_ACCEPTABLE = 406;
    public static final int HTTP_PROXY_AUTH = 407;
    public static final int HTTP_CLIENT_TIMEOUT = 408;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_GONE = 410;
    public static final int HTTP_LENGTH_REQUIRED = 411;
    public static final int HTTP_PRECON_FAILED = 412;
    public static final int HTTP_ENTITY_TOO_LARGE = 413;
    public static final int HTTP_REQ_TOO_LONG = 414;
    public static final int HTTP_UNSUPPORTED_TYPE = 415;
    @Deprecated
    public static final int HTTP_SERVER_ERROR = 500;
    public static final int HTTP_INTERNAL_ERROR = 500;
    public static final int HTTP_NOT_IMPLEMENTED = 501;
    public static final int HTTP_BAD_GATEWAY = 502;
    public static final int HTTP_UNAVAILABLE = 503;
    public static final int HTTP_GATEWAY_TIMEOUT = 504;
    public static final int HTTP_VERSION = 505;

    protected String method = "GET";
    protected int responseCode = -1;
    protected String responseMessage;
    protected boolean instanceFollowRedirects = followRedirects;
    protected int chunkLength = -1;
    protected int fixedContentLength = -1;
    protected long fixedContentLengthLong = -1;

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
        if (connected) {
            throw new ProtocolException("Can't reset method: already connected");
        }
        for (String m : METHODS) {
            if (m.equals(method)) {
                this.method = method;
                return;
            }
        }
        throw new ProtocolException("Invalid HTTP method: " + method);
    }

    public String getRequestMethod() {
        return method;
    }

    public int getResponseCode() throws IOException {
        getInputStreamQuietly();
        return responseCode;
    }

    public String getResponseMessage() throws IOException {
        getInputStreamQuietly();
        return responseMessage;
    }

    private void getInputStreamQuietly() throws IOException {
        try {
            getInputStream();
        } catch (java.io.FileNotFoundException e) {
            // 4xx responses still have a code
        } catch (IOException e) {
            if (responseCode == -1) {
                throw e;
            }
        }
    }

    public String getHeaderFieldKey(int n) {
        return null;
    }

    public String getHeaderField(int n) {
        return null;
    }

    public long getHeaderFieldDate(String name, long Default) {
        return super.getHeaderFieldDate(name, Default);
    }

    public abstract void disconnect();

    public abstract boolean usingProxy();

    public java.security.Permission getPermission() throws IOException {
        return null;
    }

    public InputStream getErrorStream() {
        return null;
    }

    public void setAuthenticator(Authenticator auth) {
        if (auth == null) {
            throw new NullPointerException("auth");
        }
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
    }

    public void setChunkedStreamingMode(int chunklen) {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        if (fixedContentLength != -1 || fixedContentLengthLong != -1) {
            throw new IllegalStateException("Fixed length streaming mode set");
        }
        chunkLength = chunklen <= 0 ? 4096 : chunklen;
    }

    public void setFixedLengthStreamingMode(int contentLength) {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        if (chunkLength != -1) {
            throw new IllegalStateException("Chunked encoding streaming mode set");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("invalid content length");
        }
        fixedContentLength = contentLength;
    }

    public void setFixedLengthStreamingMode(long contentLength) {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        if (chunkLength != -1) {
            throw new IllegalStateException("Chunked encoding streaming mode set");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("invalid content length");
        }
        fixedContentLengthLong = contentLength;
    }
}
