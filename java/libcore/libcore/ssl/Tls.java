package libcore.ssl;

/**
 * Natives over an mbedtls TLS 1.2 client session (src/native/java_ssl.c). A session reads and writes memory
 * buffers: callers feed it what they read from the transport and send what it produced. All calls on one session
 * must be serialized by the caller.
 */
final class Tls {
    static final int EOF = -1;
    static final int WANT_READ = -2;
    static final int NEED_VERIFY = -3;

    static final int ALERT_BAD_CERTIFICATE = 42;
    static final int ALERT_CERTIFICATE_UNKNOWN = 46;

    // mbedtls X509 BADCERT_* verification flags
    static final int BADCERT_EXPIRED = 0x01;
    static final int BADCERT_NOT_TRUSTED = 0x08;
    static final int BADCERT_FUTURE = 0x0200;

    private Tls() {
    }

    static native long nNew(String sniHost, String[] alpn, String[] suites);

    static native int nHandshake(long h);

    static native void nVerified(long h);

    static native void nFeed(long h, byte[] b, int off, int len);

    static native int nPending(long h);

    static native int nDrain(long h, byte[] b, int off, int len);

    static native int nRead(long h, byte[] b, int off, int len);

    static native int nWrite(long h, byte[] b, int off, int len);

    static native int nAvailable(long h);

    static native byte[] nSessionId(long h);

    static native void nCloseNotify(long h);

    static native void nAlert(long h, int description);

    static native Object[] nPeerCerts(long h);

    static native String nCipherSuite(long h);

    static native String nProtocol(long h);

    static native String nAlpn(long h);

    static native void nFree(long h);

    static native long nAnchors(Object[] ders);

    static native int nVerifyChain(long anchors, Object[] chain, boolean serverAuth, Object[] pathOut);

    static native String nVerifyText(int flags);
}
