package android.webkit;

import java.io.InputStream;
import java.util.Map;

public class WebResourceResponse {
    private boolean mImmutable;
    private String mMimeType;
    private String mEncoding;
    private int mStatusCode;
    private String mReasonPhrase;
    private Map<String, String> mResponseHeaders;
    private InputStream mInputStream;

    public WebResourceResponse(String mimeType, String encoding, InputStream data) {
        mMimeType = mimeType;
        mEncoding = encoding;
        setData(data);
    }

    public WebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase,
            Map<String, String> responseHeaders, InputStream data) {
        this(mimeType, encoding, data);
        setStatusCodeAndReasonPhrase(statusCode, reasonPhrase);
        setResponseHeaders(responseHeaders);
    }

    /** Hidden on Android; package-private here. An immutable response throws from every setter. */
    WebResourceResponse(boolean immutable, String mimeType, String encoding, int statusCode, String reasonPhrase,
            Map<String, String> responseHeaders, InputStream data) {
        mImmutable = immutable;
        mMimeType = mimeType;
        mEncoding = encoding;
        mStatusCode = statusCode;
        mReasonPhrase = reasonPhrase;
        mResponseHeaders = responseHeaders;
        mInputStream = data;
    }

    public void setMimeType(String mimeType) {
        checkImmutable();
        mMimeType = mimeType;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public void setEncoding(String encoding) {
        checkImmutable();
        mEncoding = encoding;
    }

    public String getEncoding() {
        return mEncoding;
    }

    public void setStatusCodeAndReasonPhrase(int statusCode, String reasonPhrase) {
        checkImmutable();
        if (statusCode < 100) throw new IllegalArgumentException("statusCode can't be less than 100.");
        if (statusCode > 599) throw new IllegalArgumentException("statusCode can't be greater than 599.");
        if (statusCode > 299 && statusCode < 400) {
            throw new IllegalArgumentException("statusCode can't be in the [300, 399] range.");
        }
        if (reasonPhrase == null) throw new IllegalArgumentException("reasonPhrase can't be null.");
        if (reasonPhrase.trim().isEmpty()) throw new IllegalArgumentException("reasonPhrase can't be empty.");
        for (int i = 0; i < reasonPhrase.length(); i++) {
            int c = reasonPhrase.charAt(i);
            if (c > 0x7F) throw new IllegalArgumentException("reasonPhrase can't contain non-ASCII characters.");
        }
        mStatusCode = statusCode;
        mReasonPhrase = reasonPhrase;
    }

    public int getStatusCode() {
        return mStatusCode;
    }

    public String getReasonPhrase() {
        return mReasonPhrase;
    }

    public void setResponseHeaders(Map<String, String> headers) {
        checkImmutable();
        mResponseHeaders = headers;
    }

    public Map<String, String> getResponseHeaders() {
        return mResponseHeaders;
    }

    public void setData(InputStream data) {
        checkImmutable();
        if (data != null && isStringBufferInputStream(data.getClass())) {
            throw new IllegalArgumentException("StringBufferInputStream is deprecated and must "
                    + "not be used with a WebResourceResponse");
        }
        mInputStream = data;
    }

    public InputStream getData() {
        return mInputStream;
    }

    // By name: our libcore has no java.io.StringBufferInputStream, but an app could bring its own.
    private static boolean isStringBufferInputStream(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getName().equals("java.io.StringBufferInputStream")) return true;
        }
        return false;
    }

    private void checkImmutable() {
        if (mImmutable) throw new IllegalStateException("This WebResourceResponse instance is immutable");
    }
}
