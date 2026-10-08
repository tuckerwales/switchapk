package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class URLConnection {
    protected URL url;
    protected boolean doInput = true;
    protected boolean doOutput = false;
    protected boolean useCaches = defaultUseCaches;
    protected boolean connected = false;
    protected long ifModifiedSince = 0;
    protected boolean allowUserInteraction = defaultAllowUserInteraction;
    private static boolean defaultAllowUserInteraction;
    private static boolean defaultUseCaches = true;
    private static FileNameMap fileNameMap;
    private int connectTimeout;
    private int readTimeout;
    // keys keep the caller's case; lookups ignore case like the JDK's MessageHeader
    final LinkedHashMap<String, List<String>> requestProperties = new LinkedHashMap<String, List<String>>();

    protected URLConnection(URL url) {
        this.url = url;
    }

    public abstract void connect() throws IOException;

    public static FileNameMap getFileNameMap() {
        if (fileNameMap == null) {
            return new FileNameMap() {
                public String getContentTypeFor(String fileName) {
                    return guessContentTypeFromName(fileName);
                }
            };
        }
        return fileNameMap;
    }

    public static void setFileNameMap(FileNameMap map) {
        fileNameMap = map;
    }

    public URL getURL() {
        return url;
    }

    public void setConnectTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout can not be negative");
        }
        connectTimeout = timeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setReadTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout can not be negative");
        }
        readTimeout = timeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getContentLength() {
        long l = getContentLengthLong();
        return l > Integer.MAX_VALUE ? -1 : (int) l;
    }

    public long getContentLengthLong() {
        return getHeaderFieldLong("content-length", -1);
    }

    public String getContentType() {
        return getHeaderField("content-type");
    }

    public String getContentEncoding() {
        return getHeaderField("content-encoding");
    }

    public long getExpiration() {
        return getHeaderFieldDate("expires", 0);
    }

    public long getDate() {
        return getHeaderFieldDate("date", 0);
    }

    public long getLastModified() {
        return getHeaderFieldDate("last-modified", 0);
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
        long t = parseHttpDate(getHeaderField(name));
        return t == Long.MIN_VALUE ? Default : t;
    }

    public int getHeaderFieldInt(String name, int Default) {
        String value = getHeaderField(name);
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return Default;
        }
    }

    public long getHeaderFieldLong(String name, long Default) {
        String value = getHeaderField(name);
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return Default;
        }
    }

    public Object getContent() throws IOException {
        return getInputStream();
    }

    public Object getContent(Class[] classes) throws IOException {
        for (Class c : classes) {
            if (c.isInstance(getInputStream())) {
                return getInputStream();
            }
        }
        return null;
    }

    public java.security.Permission getPermission() throws IOException {
        return null;
    }

    public InputStream getInputStream() throws IOException {
        throw new UnknownServiceException("protocol doesn't support input");
    }

    public OutputStream getOutputStream() throws IOException {
        throw new UnknownServiceException("protocol doesn't support output");
    }

    public void setDoInput(boolean doinput) {
        checkNotConnected();
        doInput = doinput;
    }

    public boolean getDoInput() {
        return doInput;
    }

    public void setDoOutput(boolean dooutput) {
        checkNotConnected();
        doOutput = dooutput;
    }

    public boolean getDoOutput() {
        return doOutput;
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
        checkNotConnected();
        allowUserInteraction = allowuserinteraction;
    }

    public boolean getAllowUserInteraction() {
        return allowUserInteraction;
    }

    public static void setDefaultAllowUserInteraction(boolean defaultallowuserinteraction) {
        defaultAllowUserInteraction = defaultallowuserinteraction;
    }

    public static boolean getDefaultAllowUserInteraction() {
        return defaultAllowUserInteraction;
    }

    public void setUseCaches(boolean usecaches) {
        checkNotConnected();
        useCaches = usecaches;
    }

    public boolean getUseCaches() {
        return useCaches;
    }

    public void setIfModifiedSince(long ifmodifiedsince) {
        checkNotConnected();
        ifModifiedSince = ifmodifiedsince;
    }

    public long getIfModifiedSince() {
        return ifModifiedSince;
    }

    public boolean getDefaultUseCaches() {
        return defaultUseCaches;
    }

    public void setDefaultUseCaches(boolean defaultusecaches) {
        defaultUseCaches = defaultusecaches;
    }

    public static void setDefaultUseCaches(String protocol, boolean defaultVal) {
    }

    public static boolean getDefaultUseCaches(String protocol) {
        return defaultUseCaches;
    }

    private void checkNotConnected() {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
    }

    private String findKey(String key) {
        for (String k : requestProperties.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return k;
            }
        }
        return null;
    }

    public void setRequestProperty(String key, String value) {
        checkNotConnected();
        if (key == null) {
            throw new NullPointerException("key is null");
        }
        String k = findKey(key);
        if (k != null) {
            requestProperties.remove(k);
        }
        List<String> l = new ArrayList<String>();
        l.add(value);
        requestProperties.put(key, l);
    }

    public void addRequestProperty(String key, String value) {
        checkNotConnected();
        if (key == null) {
            throw new NullPointerException("key is null");
        }
        String k = findKey(key);
        if (k == null) {
            requestProperties.put(key, new ArrayList<String>());
            k = key;
        }
        requestProperties.get(k).add(value);
    }

    public String getRequestProperty(String key) {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        String k = key != null ? findKey(key) : null;
        if (k == null) {
            return null;
        }
        List<String> l = requestProperties.get(k);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    public Map<String, List<String>> getRequestProperties() {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }
        LinkedHashMap<String, List<String>> m = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : requestProperties.entrySet()) {
            m.put(e.getKey(), Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
        }
        return Collections.unmodifiableMap(m);
    }

    public static void setDefaultRequestProperty(String key, String value) {
    }

    public static String getDefaultRequestProperty(String key) {
        return null;
    }

    public static synchronized void setContentHandlerFactory(ContentHandlerFactory fac) {
    }

    public static String guessContentTypeFromName(String fname) {
        if (fname == null) {
            return null;
        }
        int dot = fname.lastIndexOf('.');
        String ext = dot >= 0 ? fname.substring(dot + 1).toLowerCase() : "";
        switch (ext) {
            case "html": case "htm": return "text/html";
            case "txt": case "text": case "java": case "c": case "h": return "text/plain";
            case "xml": return "application/xml";
            case "json": return "application/json";
            case "css": return "text/css";
            case "js": return "application/javascript";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "zip": return "application/zip";
            case "pdf": return "application/pdf";
            case "mp3": return "audio/mpeg";
            case "ogg": return "audio/ogg";
            case "wav": return "audio/x-wav";
            case "mp4": return "video/mp4";
            default: return null;
        }
    }

    public static String guessContentTypeFromStream(InputStream is) throws IOException {
        if (!is.markSupported()) {
            return null;
        }
        is.mark(16);
        byte[] b = new byte[8];
        int n = is.read(b);
        is.reset();
        if (n >= 4 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (n >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (n >= 1 && b[0] == '<') {
            return n >= 5 && b[1] == '?' && b[2] == 'x' ? "application/xml" : "text/html";
        }
        return null;
    }

    public String toString() {
        return getClass().getName() + ":" + url;
    }

    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov",
        "dec"};

    /**
     * Parses an HTTP date (RFC 1123, RFC 850 or asctime) as GMT. Returns Long.MIN_VALUE when it does not parse.
     * Framework-internal.
     */
    static long parseHttpDate(String s) {
        if (s == null) {
            return Long.MIN_VALUE;
        }
        String[] tok = s.trim().replace(',', ' ').replace('-', ' ').split("\\s+");
        int day = -1, month = -1, year = -1, h = -1, m = 0, sec = 0;
        for (String t : tok) {
            if (t.indexOf(':') > 0) {
                String[] p = t.split(":");
                try {
                    h = Integer.parseInt(p[0]);
                    m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                    sec = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                } catch (NumberFormatException e) {
                    return Long.MIN_VALUE;
                }
            } else if (Character.isDigit(t.charAt(0))) {
                int v;
                try {
                    v = Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    return Long.MIN_VALUE;
                }
                if (day < 0 && t.length() <= 2) {
                    day = v;
                } else {
                    year = v < 70 ? 2000 + v : v < 100 ? 1900 + v : v;
                }
            } else if (t.length() >= 3) {
                String lc = t.substring(0, 3).toLowerCase();
                for (int i = 0; i < 12; i++) {
                    if (MONTHS[i].equals(lc) && month < 0 && t.length() <= 9) {
                        month = i + 1;
                    }
                }
            }
        }
        if (day < 1 || month < 1 || year < 0 || h < 0) {
            return Long.MIN_VALUE;
        }
        // days from civil (proleptic Gregorian)
        int y = month <= 2 ? year - 1 : year;
        int era = (y >= 0 ? y : y - 399) / 400;
        int yoe = y - era * 400;
        int doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        long days = (long) era * 146097 + doe - 719468;
        return ((days * 24 + h) * 60 + m) * 60000L + sec * 1000L;
    }
}
