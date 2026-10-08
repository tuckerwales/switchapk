package java.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * HTTP/1.1 client behind URL.openConnection() for http URLs. One socket per request (no connection pool, no cache,
 * no gzip), redirects within the same scheme, fixed-length, chunked and read-until-close bodies. Error behaviour
 * follows Android's OkHttp-based implementation: getInputStream() throws FileNotFoundException for any status of
 * 400 or more and getErrorStream() returns that body.
 */
final class HttpURLConnectionImpl extends HttpURLConnection {
    private static final int MAX_FOLLOW_UPS = 20;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean requestSent;
    private ByteArrayOutputStream bufferedBody;
    private byte[] replayBody;
    private StreamingBody streamingBody;
    private List<String[]> headers; // response headers in order; {null, status line} first
    private InputStream body;
    private IOException failure;
    private int followUps;

    HttpURLConnectionImpl(URL u) {
        super(u);
    }

    public void connect() throws IOException {
        if (connected) {
            return;
        }
        openSocket();
        connected = true;
    }

    private void openSocket() throws IOException {
        if (url.getProtocol().equals("https")) {
            // TODO(WS11): TLS. Until then https fails the way a handshake failure does.
            throw new javax.net.ssl.SSLHandshakeException("TLS is not supported yet: " + url.getHost());
        }
        String host = url.getHost();
        if (host.isEmpty()) {
            throw new UnknownHostException("No host in " + url);
        }
        int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
        InetAddress[] addrs = InetAddress.getAllByName(host);
        IOException last = null;
        for (InetAddress a : addrs) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(a, port), getConnectTimeout());
                s.setSoTimeout(getReadTimeout());
                s.setTcpNoDelay(true);
                socket = s;
                in = new BufferedInputStream(s.getInputStream(), 8192);
                out = s.getOutputStream();
                return;
            } catch (IOException e) {
                s.close();
                last = e;
            }
        }
        if (last instanceof ConnectException) {
            ConnectException ce = new ConnectException("Failed to connect to " + host + "/" + addrs[0].getHostAddress()
                    + ":" + port);
            ce.initCause(last);
            throw ce;
        }
        throw last;
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // nothing to do
            }
            socket = null;
        }
    }

    private static boolean permitsBody(String m) {
        return m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
    }

    private String requestTarget() {
        String f = url.getFile();
        return f == null || f.isEmpty() ? "/" : f;
    }

    private boolean hasRequestProperty(String key) {
        for (String k : requestProperties.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private void writeHead(long contentLength, boolean chunked) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(requestTarget()).append(" HTTP/1.1\r\n");
        if (!hasRequestProperty("Host")) {
            int port = url.getPort();
            sb.append("Host: ").append(url.getHost());
            if (port != -1 && port != url.getDefaultPort()) {
                sb.append(':').append(port);
            }
            sb.append("\r\n");
        }
        if (!hasRequestProperty("User-Agent")) {
            String agent = System.getProperty("http.agent");
            if (agent != null) {
                sb.append("User-Agent: ").append(agent).append("\r\n");
            }
        }
        if (!hasRequestProperty("Connection")) {
            sb.append("Connection: close\r\n");
        }
        if (ifModifiedSince != 0 && !hasRequestProperty("If-Modified-Since")) {
            sb.append("If-Modified-Since: ").append(formatHttpDate(ifModifiedSince)).append("\r\n");
        }
        boolean hasBody = chunked || contentLength >= 0;
        if (hasBody && !hasRequestProperty("Content-Type")) {
            sb.append("Content-Type: application/x-www-form-urlencoded\r\n");
        }
        for (Map.Entry<String, List<String>> e : requestProperties.entrySet()) {
            String k = e.getKey();
            if (chunked && k.equalsIgnoreCase("Content-Length")) {
                continue;
            }
            if (hasBody && (k.equalsIgnoreCase("Content-Length") || k.equalsIgnoreCase("Transfer-Encoding"))) {
                continue;
            }
            for (String v : e.getValue()) {
                sb.append(k).append(": ").append(v == null ? "" : v).append("\r\n");
            }
        }
        if (chunked) {
            sb.append("Transfer-Encoding: chunked\r\n");
        } else if (contentLength >= 0) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes("ISO-8859-1"));
    }

    private static final String[] DAYS = {"Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"};
    private static final String[] MONTH_NAMES = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
        "Nov", "Dec"};

    static String formatHttpDate(long millis) {
        long secs = Math.floorDiv(millis, 1000L);
        long days = Math.floorDiv(secs, 86400L);
        long sod = secs - days * 86400L;
        // civil from days
        long z = days + 719468;
        long era = Math.floorDiv(z, 146097);
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp < 10 ? mp + 3 : mp - 9;
        long y = yoe + era * 400 + (m <= 2 ? 1 : 0);
        return String.format(java.util.Locale.US, "%s, %02d %s %d %02d:%02d:%02d GMT",
                DAYS[(int) Math.floorMod(days, 7L)], d, MONTH_NAMES[(int) m - 1], y, sod / 3600, (sod / 60) % 60,
                sod % 60);
    }

    public OutputStream getOutputStream() throws IOException {
        if (doOutput && method.equals("GET")) {
            method = "POST";
        }
        if (!doOutput || !permitsBody(method)) {
            throw new ProtocolException("method does not support a request body: " + method);
        }
        if (headers != null || failure != null) {
            throw new ProtocolException("cannot write request body after response has been read");
        }
        if (streamingBody != null) {
            return streamingBody;
        }
        if (bufferedBody != null) {
            return bufferedBody;
        }
        connect();
        if (chunkLength > 0) {
            writeHead(-1, true);
            requestSent = true;
            return streamingBody = new StreamingBody(-1, chunkLength);
        }
        long fixed = fixedContentLengthLong != -1 ? fixedContentLengthLong : fixedContentLength;
        if (fixed != -1) {
            writeHead(fixed, false);
            requestSent = true;
            return streamingBody = new StreamingBody(fixed, 0);
        }
        return bufferedBody = new ByteArrayOutputStream();
    }

    private void sendRequest() throws IOException {
        if (doOutput && method.equals("GET")) {
            method = "POST";
        }
        byte[] payload = bufferedBody != null ? bufferedBody.toByteArray() : replayBody;
        if (payload == null && permitsBody(method) && !method.equals("DELETE")) {
            payload = new byte[0];
        }
        writeHead(payload != null ? payload.length : -1, false);
        if (payload != null && payload.length > 0) {
            out.write(payload);
        }
        out.flush();
        replayBody = payload;
        requestSent = true;
    }

    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            int c = in.read();
            if (c < 0) {
                if (sb.length() == 0) {
                    throw new IOException("unexpected end of stream on " + url);
                }
                break;
            }
            if (c == '\n') {
                break;
            }
            if (sb.length() > 65536) {
                throw new ProtocolException("header line too long");
            }
            sb.append((char) c);
        }
        int n = sb.length();
        if (n > 0 && sb.charAt(n - 1) == '\r') {
            sb.setLength(n - 1);
        }
        return sb.toString();
    }

    private void readResponseHead() throws IOException {
        for (;;) {
            String status = readLine();
            if (!status.startsWith("HTTP/") || status.length() < 12 || status.charAt(8) != ' ') {
                throw new ProtocolException("Unexpected status line: " + status);
            }
            int code;
            try {
                code = Integer.parseInt(status.substring(9, 12));
            } catch (NumberFormatException e) {
                throw new ProtocolException("Unexpected status line: " + status);
            }
            List<String[]> h = new ArrayList<String[]>();
            h.add(new String[] {null, status});
            for (;;) {
                String line = readLine();
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                h.add(new String[] {line.substring(0, colon).trim(), line.substring(colon + 1).trim()});
            }
            if (code >= 100 && code < 200 && code != 101) {
                continue; // informational, the real response follows
            }
            responseCode = code;
            responseMessage = status.length() > 13 ? status.substring(13) : "";
            headers = h;
            return;
        }
    }

    private String header(String name) {
        String v = null;
        for (int i = 1; i < headers.size(); i++) {
            String[] kv = headers.get(i);
            if (kv[0].equalsIgnoreCase(name)) {
                v = kv[1];
            }
        }
        return v;
    }

    private InputStream makeBody() throws IOException {
        if (method.equals("HEAD") || responseCode == HTTP_NO_CONTENT || responseCode == HTTP_NOT_MODIFIED
                || (responseCode >= 100 && responseCode < 200)) {
            closeSocket();
            return new FixedBody(0);
        }
        String te = header("Transfer-Encoding");
        if (te != null && te.toLowerCase().contains("chunked")) {
            return new ChunkedBody();
        }
        String cl = header("Content-Length");
        if (cl != null) {
            try {
                long n = Long.parseLong(cl.trim());
                if (n >= 0) {
                    FixedBody b = new FixedBody(n);
                    if (n == 0) {
                        closeSocket();
                    }
                    return b;
                }
            } catch (NumberFormatException e) {
                // fall through to read-until-close
            }
        }
        return new UntilCloseBody();
    }

    private static boolean isRedirect(int code) {
        return code == HTTP_MULT_CHOICE || code == HTTP_MOVED_PERM || code == HTTP_MOVED_TEMP
                || code == HTTP_SEE_OTHER || code == 307 || code == 308;
    }

    private void getResponse() throws IOException {
        if (headers != null) {
            return;
        }
        if (failure != null) {
            throw failure;
        }
        try {
            for (;;) {
                connect();
                if (streamingBody != null) {
                    streamingBody.finish();
                } else if (!requestSent) {
                    sendRequest();
                }
                readResponseHead();
                body = makeBody();
                if (!followRedirect()) {
                    return;
                }
            }
        } catch (IOException e) {
            failure = e;
            closeSocket();
            throw e;
        }
    }

    /** Prepares the next request when the response is a redirect we follow; false otherwise. */
    private boolean followRedirect() throws IOException {
        if (!instanceFollowRedirects || !isRedirect(responseCode) || streamingBody != null) {
            return false;
        }
        String location = header("Location");
        if (location == null) {
            return false;
        }
        URL next;
        try {
            next = new URL(url, location);
        } catch (MalformedURLException e) {
            return false;
        }
        if (!next.getProtocol().equals(url.getProtocol())) {
            return false; // like Android, never follow between http and https
        }
        if (++followUps > MAX_FOLLOW_UPS) {
            throw new ProtocolException("Too many follow-up requests: " + followUps);
        }
        body.close();
        closeSocket();
        boolean keepMethod = responseCode == 307 || responseCode == 308;
        if (!keepMethod && !method.equals("GET") && !method.equals("HEAD")) {
            method = "GET";
            doOutput = false;
            bufferedBody = null;
            replayBody = null;
        }
        if (keepMethod && bufferedBody != null) {
            replayBody = bufferedBody.toByteArray();
            bufferedBody = null;
        }
        url = next;
        headers = null;
        body = null;
        requestSent = false;
        connected = false;
        return true;
    }

    public int getResponseCode() throws IOException {
        getResponse();
        return responseCode;
    }

    public String getResponseMessage() throws IOException {
        getResponse();
        return responseMessage;
    }

    public InputStream getInputStream() throws IOException {
        if (!doInput) {
            throw new ProtocolException("This protocol does not support input");
        }
        getResponse();
        if (responseCode >= HTTP_BAD_REQUEST) {
            throw new FileNotFoundException(url.toString());
        }
        return body;
    }

    public InputStream getErrorStream() {
        try {
            getResponse();
        } catch (IOException e) {
            return null;
        }
        return responseCode >= HTTP_BAD_REQUEST ? body : null;
    }

    private boolean responseQuietly() {
        try {
            getResponse();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public String getHeaderField(String name) {
        if (!responseQuietly()) {
            return null;
        }
        return name == null ? headers.get(0)[1] : header(name);
    }

    public String getHeaderField(int n) {
        if (!responseQuietly() || n < 0 || n >= headers.size()) {
            return null;
        }
        return headers.get(n)[1];
    }

    public String getHeaderFieldKey(int n) {
        if (!responseQuietly() || n < 0 || n >= headers.size()) {
            return null;
        }
        return headers.get(n)[0];
    }

    public Map<String, List<String>> getHeaderFields() {
        if (!responseQuietly()) {
            return Collections.emptyMap();
        }
        TreeMap<String, List<String>> m = new TreeMap<String, List<String>>(new Comparator<String>() {
            public int compare(String a, String b) {
                if (a == b) {
                    return 0;
                }
                if (a == null) {
                    return -1;
                }
                if (b == null) {
                    return 1;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a, b);
            }
        });
        for (String[] kv : headers) {
            List<String> l = m.get(kv[0]);
            if (l == null) {
                l = new ArrayList<String>();
                m.put(kv[0], l);
            }
            l.add(kv[1]);
        }
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(m);
    }

    public void disconnect() {
        closeSocket();
    }

    public boolean usingProxy() {
        return false;
    }

    /** Request body sent while it is written: fixed length (limit >= 0) or chunked (chunk > 0). */
    private final class StreamingBody extends OutputStream {
        private final long limit;
        private final byte[] chunk;
        private int pending;
        private long written;
        private boolean finished;

        StreamingBody(long limit, int chunkSize) {
            this.limit = limit;
            this.chunk = chunkSize > 0 ? new byte[chunkSize] : null;
        }

        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (finished) {
                throw new IOException("closed");
            }
            if (chunk == null) {
                if (written + len > limit) {
                    throw new ProtocolException("expected " + limit + " bytes but received " + (written + len));
                }
                out.write(b, off, len);
                written += len;
                return;
            }
            while (len > 0) {
                int n = Math.min(len, chunk.length - pending);
                System.arraycopy(b, off, chunk, pending, n);
                pending += n;
                off += n;
                len -= n;
                if (pending == chunk.length) {
                    flushChunk();
                }
            }
        }

        private void flushChunk() throws IOException {
            if (pending > 0) {
                out.write((Integer.toHexString(pending) + "\r\n").getBytes("ISO-8859-1"));
                out.write(chunk, 0, pending);
                out.write(new byte[] {'\r', '\n'});
                pending = 0;
            }
        }

        public void flush() throws IOException {
            if (chunk != null && !finished) {
                flushChunk();
            }
            out.flush();
        }

        void finish() throws IOException {
            if (finished) {
                return;
            }
            finished = true;
            if (chunk == null) {
                if (written != limit) {
                    throw new ProtocolException("expected " + limit + " bytes but received " + written);
                }
            } else {
                flushChunk();
                out.write(new byte[] {'0', '\r', '\n', '\r', '\n'});
            }
            out.flush();
        }

        public void close() throws IOException {
            finish();
        }
    }

    private abstract class Body extends InputStream {
        boolean done;

        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n <= 0 ? -1 : b[0] & 0xff;
        }

        void end() {
            if (!done) {
                done = true;
                closeSocket();
            }
        }

        public void close() throws IOException {
            end();
        }
    }

    private final class FixedBody extends Body {
        private long remaining;

        FixedBody(long length) {
            remaining = length;
            if (length == 0) {
                done = true;
            }
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining == 0 || done) {
                end();
                return -1;
            }
            int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n < 0) {
                end();
                throw new ProtocolException("unexpected end of stream");
            }
            remaining -= n;
            if (remaining == 0) {
                end();
            }
            return n;
        }

        public int available() throws IOException {
            return done ? 0 : (int) Math.min(remaining, in.available());
        }
    }

    private final class ChunkedBody extends Body {
        private long chunkLeft = -1; // -1: before the first chunk header

        public int read(byte[] b, int off, int len) throws IOException {
            if (done) {
                return -1;
            }
            if (len == 0) {
                return 0;
            }
            if (chunkLeft <= 0) {
                if (chunkLeft == 0) {
                    readLine(); // CRLF after the previous chunk
                }
                String size = readLine();
                int semi = size.indexOf(';');
                try {
                    chunkLeft = Long.parseLong((semi >= 0 ? size.substring(0, semi) : size).trim(), 16);
                } catch (NumberFormatException e) {
                    throw new ProtocolException("Expected a hex chunk size but was " + size);
                }
                if (chunkLeft == 0) {
                    while (!readLine().isEmpty()) {
                        // trailers
                    }
                    end();
                    return -1;
                }
            }
            int n = in.read(b, off, (int) Math.min(len, chunkLeft));
            if (n < 0) {
                end();
                throw new ProtocolException("unexpected end of stream");
            }
            chunkLeft -= n;
            return n;
        }

        public int available() throws IOException {
            return done || chunkLeft <= 0 ? 0 : (int) Math.min(chunkLeft, in.available());
        }
    }

    private final class UntilCloseBody extends Body {
        public int read(byte[] b, int off, int len) throws IOException {
            if (done) {
                return -1;
            }
            int n = in.read(b, off, len);
            if (n < 0) {
                end();
            }
            return n;
        }

        public int available() throws IOException {
            return done ? 0 : in.available();
        }
    }
}
