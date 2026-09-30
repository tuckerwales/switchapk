package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class URLConnection {
    protected URL url;
    protected boolean doInput = true;
    protected boolean doOutput = false;
    protected boolean useCaches = true;
    protected boolean connected = false;
    protected long ifModifiedSince = 0;
    protected boolean allowUserInteraction;
    private int connectTimeout;
    private int readTimeout;
    final HashMap<String, String> requestProperties = new HashMap<String, String>();

    protected URLConnection(URL url) {
        this.url = url;
    }

    public abstract void connect() throws IOException;

    public URL getURL() {
        return url;
    }

    public void setConnectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setReadTimeout(int timeout) {
        readTimeout = timeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getContentLength() {
        return -1;
    }

    public long getContentLengthLong() {
        return getContentLength();
    }

    public String getContentType() {
        return getHeaderField("content-type");
    }

    public String getContentEncoding() {
        return getHeaderField("content-encoding");
    }

    public String getHeaderField(String name) {
        return null;
    }

    public Map<String, List<String>> getHeaderFields() {
        return Collections.emptyMap();
    }

    public String getHeaderField(int n) {
        return null;
    }

    public String getHeaderFieldKey(int n) {
        return null;
    }

    public long getHeaderFieldDate(String name, long Default) {
        return Default;
    }

    public int getHeaderFieldInt(String name, int Default) {
        String value = getHeaderField(name);
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return Default;
        }
    }

    public InputStream getInputStream() throws IOException {
        throw new UnknownServiceException("protocol doesn't support input");
    }

    public OutputStream getOutputStream() throws IOException {
        throw new UnknownServiceException("protocol doesn't support output");
    }

    public void setDoInput(boolean doinput) {
        doInput = doinput;
    }

    public boolean getDoInput() {
        return doInput;
    }

    public void setDoOutput(boolean dooutput) {
        doOutput = dooutput;
    }

    public boolean getDoOutput() {
        return doOutput;
    }

    public void setUseCaches(boolean usecaches) {
        useCaches = usecaches;
    }

    public boolean getUseCaches() {
        return useCaches;
    }

    public void setIfModifiedSince(long ifmodifiedsince) {
        ifModifiedSince = ifmodifiedsince;
    }

    public void setRequestProperty(String key, String value) {
        requestProperties.put(key, value);
    }

    public void addRequestProperty(String key, String value) {
        requestProperties.put(key, value);
    }

    public String getRequestProperty(String key) {
        return requestProperties.get(key);
    }

    public Map<String, List<String>> getRequestProperties() {
        return Collections.emptyMap();
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
        allowUserInteraction = allowuserinteraction;
    }

    public String toString() {
        return getClass().getName() + ":" + url;
    }
}
