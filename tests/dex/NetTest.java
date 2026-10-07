import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * java.net conformance: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 * Everything runs over loopback against servers in this process; ports are never printed.
 */
public class NetTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    public static void main(String[] args) throws Exception {
        addresses();
        tcpEcho();
        timeouts();
        refused();
        closeWhileAccepting();
        udp();
        http();
        p("done");
    }

    static void addresses() throws Exception {
        p("-- addresses");
        InetAddress a = InetAddress.getByName("127.0.0.1");
        p(a + " loopback=" + a.isLoopbackAddress() + " any=" + a.isAnyLocalAddress());
        p("localhost loopback=" + InetAddress.getByName("localhost").isLoopbackAddress());
        p("v6 loopback=" + InetAddress.getByName("::1").isLoopbackAddress() + " "
                + (InetAddress.getByName("::1") instanceof Inet6Address));
        InetAddress b = InetAddress.getByAddress(new byte[] {10, 1, 2, 3});
        p(b.getHostAddress() + " site=" + b.isSiteLocalAddress() + " v4=" + (b instanceof Inet4Address));
        p("192.168 site=" + InetAddress.getByName("192.168.4.5").isSiteLocalAddress());
        p("169.254 link=" + InetAddress.getByName("169.254.1.1").isLinkLocalAddress());
        p("equals=" + InetAddress.getByName("127.0.0.1").equals(InetAddress.getByAddress(new byte[] {127, 0, 0, 1})));
        p("named=" + InetAddress.getByAddress("box", new byte[] {1, 2, 3, 4}));
        try {
            InetAddress.getByName("no-such-host.invalid");
            p("resolved?");
        } catch (UnknownHostException e) {
            p("unknown host: " + name(e));
        }
        InetSocketAddress u = InetSocketAddress.createUnresolved("example.org", 80);
        p("unresolved=" + u.isUnresolved() + " " + u.getHostString() + " " + u.getPort());
        InetSocketAddress r = new InetSocketAddress("127.0.0.1", 99);
        p("resolved=" + r.isUnresolved() + " " + r.getHostString() + " " + r.getAddress().getHostAddress());
        try {
            new InetSocketAddress(70000);
        } catch (IllegalArgumentException e) {
            p("bad port: " + name(e));
        }
    }

    static void tcpEcho() throws Exception {
        p("-- tcp");
        final ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        p("server bound=" + ss.isBound() + " port>0=" + (ss.getLocalPort() > 0));
        Thread t = new Thread() {
            public void run() {
                try {
                    Socket c = ss.accept();
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    Writer w = new OutputStreamWriter(c.getOutputStream(), "UTF-8");
                    String line;
                    while ((line = r.readLine()) != null) {
                        w.write(line.toUpperCase() + "\n");
                        w.flush();
                    }
                    w.write("bye\n");
                    w.flush();
                    c.close();
                } catch (IOException e) {
                    System.out.println("server failed " + e);
                }
            }
        };
        t.start();
        Socket s = new Socket(InetAddress.getLoopbackAddress(), ss.getLocalPort());
        p("connected=" + s.isConnected() + " bound=" + s.isBound() + " samePort=" + (s.getPort() == ss.getLocalPort())
                + " local>0=" + (s.getLocalPort() > 0));
        p("remote loopback=" + s.getInetAddress().isLoopbackAddress());
        s.setTcpNoDelay(true);
        p("nodelay=" + s.getTcpNoDelay());
        s.setKeepAlive(true);
        p("keepalive=" + s.getKeepAlive());
        Writer w = new OutputStreamWriter(s.getOutputStream(), "UTF-8");
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        w.write("hello\nnet été\n");
        w.flush();
        p(r.readLine());
        p(r.readLine());
        byte[] big = new byte[200000];
        Arrays.fill(big, (byte) 'a');
        big[big.length - 1] = '\n';
        s.getOutputStream().write(big);
        String bigLine = r.readLine();
        p("big line " + bigLine.length() + " " + bigLine.charAt(0));
        s.shutdownOutput();
        p("outShut=" + s.isOutputShutdown());
        p(r.readLine());
        p("eof=" + r.readLine());
        s.close();
        p("closed=" + s.isClosed());
        try {
            s.getInputStream();
        } catch (SocketException e) {
            p("after close: " + name(e));
        }
        t.join();
        ss.close();
        p("server closed=" + ss.isClosed());
    }

    static void timeouts() throws Exception {
        p("-- timeouts");
        ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        ss.setSoTimeout(150);
        try {
            ss.accept();
        } catch (SocketTimeoutException e) {
            p("accept: " + name(e));
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), ss.getLocalPort()), 1000);
        Socket peer = ss.accept();
        s.setSoTimeout(150);
        p("soTimeout=" + s.getSoTimeout());
        long t0 = System.currentTimeMillis();
        try {
            s.getInputStream().read();
        } catch (SocketTimeoutException e) {
            p("read: " + name(e) + " waited=" + (System.currentTimeMillis() - t0 >= 100));
        }
        peer.getOutputStream().write(42);
        p("after timeout read=" + s.getInputStream().read());
        peer.close();
        p("peer closed read=" + s.getInputStream().read());
        s.close();
        ss.close();
    }

    static void refused() throws Exception {
        p("-- refused");
        ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        int port = ss.getLocalPort();
        ss.close();
        try {
            new Socket(InetAddress.getLoopbackAddress(), port);
            p("connected?");
        } catch (ConnectException e) {
            p("connect: " + name(e));
        }
        Socket s = new Socket();
        p("unconnected port=" + s.getPort() + " localPort=" + s.getLocalPort() + " addr=" + s.getInetAddress());
        try {
            s.getOutputStream();
        } catch (SocketException e) {
            p("not connected: " + name(e));
        }
        s.close();
    }

    static void closeWhileAccepting() throws Exception {
        p("-- close while accepting");
        final ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final String[] result = new String[1];
        Thread t = new Thread() {
            public void run() {
                try {
                    ss.accept();
                    result[0] = "accepted?";
                } catch (SocketException e) {
                    result[0] = "accept ended: " + name(e);
                } catch (IOException e) {
                    result[0] = "other: " + e;
                }
            }
        };
        t.start();
        Thread.sleep(150);
        ss.close();
        t.join(5000);
        p(result[0]);
    }

    static void udp() throws Exception {
        p("-- udp");
        DatagramSocket a = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket b = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        p("bound=" + a.isBound() + " port>0=" + (a.getLocalPort() > 0));
        byte[] msg = "ping-pong".getBytes(StandardCharsets.UTF_8);
        a.send(new DatagramPacket(msg, 5, InetAddress.getLoopbackAddress(), b.getLocalPort()));
        byte[] buf = new byte[64];
        DatagramPacket in = new DatagramPacket(buf, 2, 60);
        b.setSoTimeout(2000);
        b.receive(in);
        p("got '" + new String(in.getData(), in.getOffset(), in.getLength(), StandardCharsets.UTF_8) + "' offset="
                + in.getOffset() + " fromA=" + (in.getPort() == a.getLocalPort()) + " "
                + in.getAddress().isLoopbackAddress());
        b.connect(in.getSocketAddress());
        p("connected=" + b.isConnected());
        b.send(new DatagramPacket(msg, msg.length));
        DatagramPacket back = new DatagramPacket(new byte[4], 4);
        a.setSoTimeout(2000);
        a.receive(back);
        p("truncated '" + new String(back.getData(), 0, back.getLength(), StandardCharsets.UTF_8) + "'");
        a.setSoTimeout(100);
        try {
            a.receive(back);
        } catch (SocketTimeoutException e) {
            p("receive: " + name(e));
        }
        a.close();
        b.close();
        p("closed=" + a.isClosed());
    }

    // ---- HTTP -------------------------------------------------------------------------------------------

    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return c < 0 && sb.length() == 0 ? null : sb.toString();
    }

    static void serve(Socket c) throws IOException {
        InputStream in = new BufferedInputStream(c.getInputStream());
        String reqLine = readLine(in);
        if (reqLine == null) {
            c.close();
            return;
        }
        String[] parts = reqLine.split(" ");
        String method = parts[0], path = parts[1];
        Map<String, String> h = new HashMap<String, String>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int i = line.indexOf(':');
            h.put(line.substring(0, i).trim().toLowerCase(), line.substring(i + 1).trim());
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (h.containsKey("content-length")) {
            int n = Integer.parseInt(h.get("content-length"));
            for (int i = 0; i < n; i++) {
                body.write(in.read());
            }
        } else if ("chunked".equals(h.get("transfer-encoding"))) {
            for (;;) {
                int n = Integer.parseInt(readLine(in).trim(), 16);
                if (n == 0) {
                    readLine(in);
                    break;
                }
                for (int i = 0; i < n; i++) {
                    body.write(in.read());
                }
                readLine(in);
            }
        }
        OutputStream out = c.getOutputStream();
        String resp;
        if (path.equals("/plain")) {
            resp = "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nContent-Type: text/plain\r\nX-Test: a\r\nX-Test: b\r\n"
                    + "Date: Wed, 21 Oct 2015 07:28:00 GMT\r\nConnection: close\r\n\r\n"
                    + (method.equals("HEAD") ? "" : "hello");
        } else if (path.equals("/chunked")) {
            resp = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                    + "4\r\nchun\r\n6;ext=1\r\nked bo\r\n2\r\ndy\r\n0\r\nX-Trailer: t\r\n\r\n";
        } else if (path.equals("/close")) {
            resp = "HTTP/1.1 200 Fine\r\nConnection: close\r\n\r\nuntil the end";
        } else if (path.equals("/redirect")) {
            resp = "HTTP/1.1 302 Found\r\nLocation: /plain\r\nContent-Length: 3\r\nConnection: close\r\n\r\nxyz";
        } else if (path.equals("/loop")) {
            resp = "HTTP/1.1 302 Found\r\nLocation: /loop\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        } else if (path.equals("/r307")) {
            resp = "HTTP/1.1 307 Temporary Redirect\r\nLocation: /echo\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        } else if (path.startsWith("/echo")) {
            String b = method + " " + path + " ct=" + h.get("content-type") + " x=" + h.get("x-custom") + " body="
                    + new String(body.toByteArray(), StandardCharsets.UTF_8);
            byte[] bb = b.getBytes(StandardCharsets.UTF_8);
            resp = "HTTP/1.1 201 Created\r\nContent-Length: " + bb.length + "\r\nConnection: close\r\n\r\n" + b;
        } else if (path.equals("/continue")) {
            resp = "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok";
        } else {
            resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 4\r\nConnection: close\r\n\r\nnope";
        }
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
        c.close();
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[3];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
        }
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    static void http() throws Exception {
        p("-- http");
        final ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread server = new Thread() {
            public void run() {
                try {
                    for (;;) {
                        final Socket c = ss.accept();
                        Thread h = new Thread() {
                            public void run() {
                                try {
                                    serve(c);
                                } catch (IOException e) {
                                    // client went away
                                }
                            }
                        };
                        h.setDaemon(true);
                        h.start();
                    }
                } catch (IOException e) {
                    // closed
                }
            }
        };
        server.setDaemon(true);
        server.start();
        String base = "http://127.0.0.1:" + ss.getLocalPort();

        HttpURLConnection c = (HttpURLConnection) new URL(base + "/plain").openConnection();
        p("method=" + c.getRequestMethod());
        p(c.getResponseCode() + " " + c.getResponseMessage());
        p("type=" + c.getContentType() + " length=" + c.getContentLength() + " x=" + c.getHeaderField("x-test"));
        p("status=" + c.getHeaderField(0) + " key0=" + c.getHeaderFieldKey(0) + " key1=" + c.getHeaderFieldKey(1));
        p("null key=" + c.getHeaderFields().get(null));
        p("date=" + c.getDate() + " missing=" + c.getHeaderField("nope") + " int=" + c.getHeaderFieldInt("Content-Length", -1));
        p("body=" + readAll(c.getInputStream()));
        c.disconnect();

        c = (HttpURLConnection) new URL(base + "/chunked").openConnection();
        p("chunked=" + readAll(c.getInputStream()) + " length=" + c.getContentLength());

        c = (HttpURLConnection) new URL(base + "/close").openConnection();
        p("close=" + readAll(c.getInputStream()) + " msg=" + c.getResponseMessage());

        c = (HttpURLConnection) new URL(base + "/redirect").openConnection();
        p("redirect=" + readAll(c.getInputStream()) + " " + c.getResponseCode() + " path=" + c.getURL().getPath());

        c = (HttpURLConnection) new URL(base + "/redirect").openConnection();
        c.setInstanceFollowRedirects(false);
        p("no follow=" + c.getResponseCode() + " location=" + c.getHeaderField("Location"));

        c = (HttpURLConnection) new URL(base + "/loop").openConnection();
        try {
            c.getResponseCode();
            p("loop?");
        } catch (ProtocolException e) {
            p("loop: " + name(e));
        }

        c = (HttpURLConnection) new URL(base + "/missing").openConnection();
        p("missing=" + c.getResponseCode());
        try {
            c.getInputStream();
        } catch (FileNotFoundException e) {
            p("input: " + name(e));
        }
        p("error=" + readAll(c.getErrorStream()));

        c = (HttpURLConnection) new URL(base + "/plain").openConnection();
        c.setRequestMethod("HEAD");
        p("head=" + c.getResponseCode() + " length=" + c.getContentLength() + " body='" + readAll(c.getInputStream()) + "'");

        c = (HttpURLConnection) new URL(base + "/echo").openConnection();
        c.setDoOutput(true);
        c.setRequestProperty("X-Custom", "one");
        p("request prop=" + c.getRequestProperty("x-custom"));
        OutputStream o = c.getOutputStream();
        o.write("a=1&b=2".getBytes(StandardCharsets.UTF_8));
        p("post " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        c = (HttpURLConnection) new URL(base + "/echo/fixed").openConnection();
        c.setDoOutput(true);
        c.setRequestMethod("PUT");
        c.setRequestProperty("Content-Type", "text/plain");
        c.setFixedLengthStreamingMode(6);
        c.getOutputStream().write("fixed!".getBytes(StandardCharsets.UTF_8));
        p("put " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        c = (HttpURLConnection) new URL(base + "/echo/chunked").openConnection();
        c.setDoOutput(true);
        c.setChunkedStreamingMode(4);
        o = c.getOutputStream();
        o.write("streamed in chunks".getBytes(StandardCharsets.UTF_8));
        o.close();
        p("chunked post " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        c = (HttpURLConnection) new URL(base + "/r307").openConnection();
        c.setDoOutput(true);
        c.getOutputStream().write("kept".getBytes(StandardCharsets.UTF_8));
        p("307 " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        c = (HttpURLConnection) new URL(base + "/redirect").openConnection();
        c.setDoOutput(true);
        c.getOutputStream().write("dropped".getBytes(StandardCharsets.UTF_8));
        p("302 post " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        c = (HttpURLConnection) new URL(base + "/continue").openConnection();
        p("continue " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        p("openStream=" + readAll(new URL(base + "/plain").openStream()));

        c = (HttpURLConnection) new URL(base + "/plain").openConnection();
        try {
            c.setRequestMethod("FETCH");
        } catch (ProtocolException e) {
            p("bad method: " + name(e));
        }
        c.getResponseCode();
        try {
            c.setRequestProperty("a", "b");
        } catch (IllegalStateException e) {
            p("after connect: " + name(e));
        }

        c = (HttpURLConnection) new URL("http://127.0.0.1:" + closedPort() + "/").openConnection();
        c.setConnectTimeout(2000);
        try {
            c.getResponseCode();
        } catch (ConnectException e) {
            p("refused: " + name(e));
        }
        ss.close();
    }

    static int closedPort() throws IOException {
        ServerSocket s = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        int port = s.getLocalPort();
        s.close();
        return port;
    }
}
