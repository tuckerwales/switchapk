// javac-release: 17
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.zip.CRC32;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS client checks against tests/tls/server.py (run through tests/tls/run_tls_test.sh): handshakes with a private
 * CA, ALPN, endpoint identification, HttpsURLConnection and its host name check, bulk data both ways, timeouts, and
 * the failures (expired, wrong host, untrusted, default trust store). Only outcomes and exception types are printed:
 * the messages differ between the JDK and Android by design.
 */
public class TlsTest {
    static final String HOST = "localhost";
    static final int GOOD = 47441, EXPIRED = 47442, WRONGHOST = 47443, SELFSIGNED = 47444;
    static final String ROOT_PEM = "-----BEGIN CERTIFICATE-----\n"
            + "MIIDSTCCAjGgAwIBAgIBATANBgkqhkiG9w0BAQsFADBFMQswCQYDVQQGEwJHQjEY\n"
            + "MBYGA1UECgwPc3dpdGNoYXBrIHRlc3RzMRwwGgYDVQQDDBNzd2l0Y2hhcGsgdGVz\n"
            + "dCByb290MCAXDTI2MTAxMDAwMDAwMFoYDzIwODYwOTI2MDAwMDAwWjBFMQswCQYD\n"
            + "VQQGEwJHQjEYMBYGA1UECgwPc3dpdGNoYXBrIHRlc3RzMRwwGgYDVQQDDBNzd2l0\n"
            + "Y2hhcGsgdGVzdCByb290MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA\n"
            + "pJLhEVQp2lKLlZ9w7p5D+HGNnoXonvNKieJk5kwcLi0NaEYahnn1IHMVs3OdjMgj\n"
            + "hvSQ5gbrh/msN6t3+5g39eArQMed5IlJhukTTPi0y3ezaP7EM8IVNxlXN6SNG6aA\n"
            + "c0f3brt2F6BxrLhUHQL4xHpk/9RAL0sBxrB/coSczbLlTYnhcyBDPE6bHBsd+4XB\n"
            + "SQDv4vDF7/B1OPZNUFXAM3F267EeL16mIU4nAZ3bHGmlLTRR3UZPdbCSUr3loH+K\n"
            + "/cEvQRST1MDFpLpCWkm6PzidZLhV9nEKhyaNdXPTXJ2sEePaEFQLO1Cz5nmmrqSZ\n"
            + "c9Hdm+d1viTLTkG2QGzzzwIDAQABo0IwQDAPBgNVHRMBAf8EBTADAQH/MB0GA1Ud\n"
            + "DgQWBBRNCz1WvNVhacXdPRq50VtfAr+X3jAOBgNVHQ8BAf8EBAMCAYYwDQYJKoZI\n"
            + "hvcNAQELBQADggEBAH45krFUiaDoe400eSa8JcqQSfdLFdkW/CzFmnTMJOu5eXi2\n"
            + "kc1AZSII4JP51jduPRsPu5bRJzX8yXgCGJdTMf7ARzzgocZ0noI8ny3h2Ju/uMzQ\n"
            + "bxI83I3TEbP/ZNBt3aVq/TGNtB6nCAUSrF8kN+IKRe0jkLJesWnSRVKVAHcjO6XE\n"
            + "VDlBiY73ymJwipaSP6cUTQ4d21RZvBo6+COkEJvJjYoLJSPiVAN9Bk7UNHUiqpRS\n"
            + "CUiN6vxxxv1X4GIlDLcqfstLioFLrBACKsqFvosk6wE/szAHzarllOpuQoEAl6A7\n"
            + "CA7xf91M4m0o0GWb+0rVohgUxQAV7LAeQ0gCtfw=\n"
            + "-----END CERTIFICATE-----\n";

    public static void main(String[] args) throws Exception {
        X509Certificate root = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(ROOT_PEM.getBytes(StandardCharsets.US_ASCII)));
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("test-root", root);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        SSLSocketFactory f = ctx.getSocketFactory();
        X509TrustManager tm = (X509TrustManager) tmf.getTrustManagers()[0];
        System.out.println("accepted issuers: " + tm.getAcceptedIssuers().length + " "
                + tm.getAcceptedIssuers()[0].getSubjectX500Principal().getName());

        socketHandshake(f);
        engine(ctx, GOOD, true);
        expectFailure("wrong host, SSLEngine with HTTPS identification", () -> engine(ctx, WRONGHOST, false));
        bulkEcho(f);
        readTimeout(f);
        https(f, "https://" + HOST + ":" + GOOD + "/hello");
        https(f, "https://127.0.0.1:" + GOOD + "/hello");
        expectFailure("expired, socket", () -> handshake(f, EXPIRED, true));
        expectFailure("wrong host, socket with HTTPS identification", () -> handshake(f, WRONGHOST, true));
        System.out.println("wrong host, socket without identification: " + handshake(f, WRONGHOST, false));
        // the JDK fails the handshake (endpoint identification); Android's HostnameVerifier fails after it
        try {
            https(f, "https://" + HOST + ":" + WRONGHOST + "/");
            System.out.println("wrong host, HttpsURLConnection: unexpectedly succeeded");
        } catch (javax.net.ssl.SSLException e) {
            System.out.println("wrong host, HttpsURLConnection: SSLException");
        }
        expectFailure("self-signed", () -> handshake(f, SELFSIGNED, false));
        SSLSocketFactory def = (SSLSocketFactory) SSLSocketFactory.getDefault();
        expectFailure("private CA, default trust store", () -> handshake(def, GOOD, false));

        SSLContext trustAll = SSLContext.getInstance("TLS");
        trustAll.init(null, new TrustManager[] {new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                System.out.println("app trust manager saw " + chain.length + " certificate(s), " + authType);
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        System.out.println("self-signed, app trust manager: " + handshake(trustAll.getSocketFactory(), SELFSIGNED, false));
    }

    interface Action {
        void run() throws Exception;
    }

    static void expectFailure(String what, Action a) {
        try {
            a.run();
            System.out.println(what + ": unexpectedly succeeded");
        } catch (javax.net.ssl.SSLException e) {
            System.out.println(what + ": SSLException (" + kind(e) + ")");
        } catch (Exception e) {
            System.out.println(what + ": " + e.getClass().getName());
        }
    }

    static String kind(Exception e) {
        return e instanceof javax.net.ssl.SSLHandshakeException ? "handshake" : "peer unverified or other";
    }

    static String handshake(SSLSocketFactory f, int port, boolean identify) throws IOException {
        SSLSocket s = (SSLSocket) f.createSocket(HOST, port);
        try {
            if (identify) {
                SSLParameters p = s.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS");
                s.setSSLParameters(p);
            }
            s.startHandshake();
            return "ok " + s.getSession().getProtocol();
        } finally {
            s.close();
        }
    }

    /** An HTTP request through an SSLEngine over a blocking SocketChannel, the way NIO clients drive one. */
    static void engine(SSLContext ctx, int port, boolean print) throws Exception {
        java.nio.channels.SocketChannel ch = java.nio.channels.SocketChannel.open(
                new java.net.InetSocketAddress("127.0.0.1", port));
        try {
            javax.net.ssl.SSLEngine e = ctx.createSSLEngine(HOST, port);
            e.setUseClientMode(true);
            SSLParameters p = e.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            p.setApplicationProtocols(new String[] {"http/1.1"});
            e.setSSLParameters(p);
            java.nio.ByteBuffer net = java.nio.ByteBuffer.allocate(e.getSession().getPacketBufferSize());
            java.nio.ByteBuffer in = java.nio.ByteBuffer.allocate(e.getSession().getPacketBufferSize());
            java.nio.ByteBuffer app = java.nio.ByteBuffer.allocate(e.getSession().getApplicationBufferSize() + 64);
            java.nio.ByteBuffer empty = java.nio.ByteBuffer.allocate(0);
            e.beginHandshake();
            javax.net.ssl.SSLEngineResult.HandshakeStatus hs = e.getHandshakeStatus();
            boolean finished = false;
            while (hs != javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                    && hs != javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED) {
                switch (hs) {
                    case NEED_WRAP: {
                        net.clear();
                        javax.net.ssl.SSLEngineResult r = e.wrap(empty, net);
                        net.flip();
                        while (net.hasRemaining()) ch.write(net);
                        hs = r.getHandshakeStatus();
                        break;
                    }
                    case NEED_UNWRAP: {
                        javax.net.ssl.SSLEngineResult r;
                        in.flip();
                        r = e.unwrap(in, app);
                        in.compact();
                        if (r.getStatus() == javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                            if (ch.read(in) < 0) throw new IOException("closed during handshake");
                        }
                        hs = r.getHandshakeStatus();
                        break;
                    }
                    case NEED_TASK: {
                        Runnable t;
                        while ((t = e.getDelegatedTask()) != null) t.run();
                        hs = e.getHandshakeStatus();
                        break;
                    }
                    default:
                        throw new IllegalStateException(hs.toString());
                }
                if (hs == javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED) finished = true;
            }
            if (print) {
                System.out.println("engine: handshake finished " + finished + ", " + e.getSession().getProtocol() + " "
                        + e.getSession().getCipherSuite() + ", ALPN " + e.getApplicationProtocol()
                        + ", peer " + ((X509Certificate) e.getSession().getPeerCertificates()[0])
                        .getSubjectX500Principal().getName());
            }
            byte[] req = ("GET /hello HTTP/1.1\r\nHost: " + HOST + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            net.clear();
            javax.net.ssl.SSLEngineResult w = e.wrap(java.nio.ByteBuffer.wrap(req), net);
            net.flip();
            while (net.hasRemaining()) ch.write(net);
            ByteArrayOutputStream resp = new ByteArrayOutputStream();
            boolean closed = false;
            while (!closed) {
                in.flip();
                app.clear();
                javax.net.ssl.SSLEngineResult r = e.unwrap(in, app);
                in.compact();
                app.flip();
                byte[] got = new byte[app.remaining()];
                app.get(got);
                resp.write(got);
                if (r.getStatus() == javax.net.ssl.SSLEngineResult.Status.CLOSED) {
                    closed = true;
                } else if (r.getStatus() == javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    if (ch.read(in) < 0) closed = true;
                }
            }
            String text = resp.toString("US-ASCII");
            if (print) {
                System.out.println("engine: wrapped " + w.bytesConsumed() + " bytes; response "
                        + text.substring(0, text.indexOf('\r')) + " / " + text.substring(text.indexOf("\r\n\r\n") + 4).trim()
                        + "; inbound done " + e.isInboundDone());
            }
            e.closeOutbound();
            net.clear();
            javax.net.ssl.SSLEngineResult c = e.wrap(empty, net);
            System.out.println("engine: close " + (print ? c.getStatus() + ", outbound done " + e.isOutboundDone() : ""));
        } finally {
            ch.close();
        }
    }

    static void socketHandshake(SSLSocketFactory f) throws Exception {
        SSLSocket s = (SSLSocket) f.createSocket(HOST, GOOD);
        SSLParameters p = s.getSSLParameters();
        p.setApplicationProtocols(new String[] {"h2", "http/1.1"});
        p.setEndpointIdentificationAlgorithm("HTTPS");
        s.setSSLParameters(p);
        final String[] event = new String[1];
        s.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            public void handshakeCompleted(HandshakeCompletedEvent e) {
                event[0] = e.getCipherSuite();
            }
        });
        System.out.println("application protocol before handshake: " + s.getApplicationProtocol());
        s.startHandshake();
        SSLSession ss = s.getSession();
        System.out.println("protocol " + ss.getProtocol() + ", suite " + ss.getCipherSuite());
        System.out.println("application protocol: " + s.getApplicationProtocol());
        System.out.println("peer host " + ss.getPeerHost() + ", port " + ss.getPeerPort());
        Certificate[] chain = ss.getPeerCertificates();
        System.out.println("peer chain: " + chain.length);
        for (Certificate c : chain) {
            X509Certificate x = (X509Certificate) c;
            System.out.println("  " + x.getSubjectX500Principal().getName() + " <- " + x.getIssuerX500Principal().getName());
        }
        System.out.println("peer principal: " + ss.getPeerPrincipal().getName());
        System.out.println("listener: " + event[0]);
        System.out.println("session valid: " + ss.isValid() + ", buffers ok: "
                + (ss.getApplicationBufferSize() >= 16384 && ss.getPacketBufferSize() > ss.getApplicationBufferSize()));
        s.close();
        System.out.println("closed: " + s.isClosed());
    }

    static void bulkEcho(SSLSocketFactory f) throws Exception {
        final SSLSocket s = (SSLSocket) f.createSocket(HOST, GOOD);
        final byte[] data = new byte[1 << 20];
        long seed = 12345;
        for (int i = 0; i < data.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            data[i] = (byte) (seed >>> 56);
        }
        final OutputStream out = s.getOutputStream();
        out.write("ECHO\n".getBytes(StandardCharsets.US_ASCII));
        Thread writer = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int off = 0; off < data.length; off += 7001) {
                        out.write(data, off, Math.min(7001, data.length - off));
                    }
                    out.flush();
                } catch (IOException e) {
                    System.out.println("writer: " + e);
                }
            }
        });
        writer.start();
        InputStream in = s.getInputStream();
        ByteArrayOutputStream got = new ByteArrayOutputStream();
        byte[] buf = new byte[5000];
        while (got.size() < data.length) {
            int n = in.read(buf, 0, Math.min(buf.length, data.length - got.size()));
            if (n < 0) break;
            got.write(buf, 0, n);
        }
        writer.join();
        CRC32 a = new CRC32();
        a.update(data);
        CRC32 b = new CRC32();
        b.update(got.toByteArray());
        System.out.println("echo: sent " + data.length + ", received " + got.size() + ", same: " + (a.getValue() == b.getValue()));
        s.close();
    }

    static void readTimeout(SSLSocketFactory f) throws Exception {
        SSLSocket s = (SSLSocket) f.createSocket(HOST, GOOD);
        s.setSoTimeout(300);
        s.getOutputStream().write("ECHO\n".getBytes(StandardCharsets.US_ASCII));
        try {
            s.getInputStream().read();
            System.out.println("timeout: read returned");
        } catch (SocketTimeoutException e) {
            System.out.println("timeout: SocketTimeoutException");
        }
        s.getOutputStream().write("still usable".getBytes(StandardCharsets.US_ASCII));
        byte[] b = new byte[12];
        int n = 0;
        while (n < b.length) {
            int r = s.getInputStream().read(b, n, b.length - n);
            if (r < 0) break;
            n += r;
        }
        System.out.println("after timeout: " + new String(b, 0, n, StandardCharsets.US_ASCII));
        s.close();
    }

    static void https(SSLSocketFactory f, String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setSSLSocketFactory(f);
        int code = c.getResponseCode();
        InputStream in = c.getInputStream();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) > 0) body.write(buf, 0, n);
        System.out.println(url + ": " + code + " " + c.getHeaderField("Content-Type") + " "
                + body.toString("US-ASCII").trim() + ", " + c.getCipherSuite() + ", " + c.getServerCertificates().length
                + " server certificate(s)");
        c.disconnect();
    }
}
