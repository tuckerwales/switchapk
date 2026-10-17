package libcore.ssl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * The platform X509TrustManager, Conscrypt's TrustManagerImpl in spirit: chains are verified by mbedtls
 * (Tls.nVerifyChain: path building, signatures, validity dates, basic constraints, key usage and serverAuth) against
 * the anchors of a KeyStore, or of Android's system CA directory when the KeyStore is null. Endpoint identification
 * ("HTTPS" in the socket's SSLParameters) checks the host name as well.
 */
public final class TrustManagerImpl extends X509ExtendedTrustManager {
    static final String SYSTEM_CA_DIR = "/system/etc/security/cacerts";

    private static TrustManagerImpl system;

    private final List<byte[]> anchorDers;
    private long anchors;
    private X509Certificate[] acceptedIssuers;

    private TrustManagerImpl(List<byte[]> anchorDers) {
        this.anchorDers = anchorDers;
    }

    /** The system trust manager (Android's CA set), built once per process. */
    static synchronized TrustManagerImpl system() {
        if (system == null) system = new TrustManagerImpl(readSystemAnchors());
        return system;
    }

    /** A trust manager over a KeyStore's certificates; null means the system CAs. */
    public static TrustManagerImpl forKeyStore(KeyStore ks) throws KeyStoreException {
        if (ks == null) return system();
        List<byte[]> ders = new ArrayList<byte[]>();
        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
            String alias = e.nextElement();
            Certificate c = null;
            if (ks.isCertificateEntry(alias)) {
                c = ks.getCertificate(alias);
            } else if (ks.getType().equals("AndroidCAStore")) {
                c = ks.getCertificate(alias);
            }
            if (c instanceof X509Certificate) {
                try {
                    ders.add(c.getEncoded());
                } catch (CertificateException ignored) {
                    // an unencodable certificate cannot be an anchor
                }
            }
        }
        return new TrustManagerImpl(ders);
    }

    private static List<byte[]> readSystemAnchors() {
        List<byte[]> out = new ArrayList<byte[]>();
        String[] names = new File(SYSTEM_CA_DIR).list();
        if (names == null) return out;
        Arrays.sort(names);
        for (String n : names) {
            try {
                byte[] der = pemToDer(readAll(new File(SYSTEM_CA_DIR, n)));
                if (der != null) out.add(der);
            } catch (IOException ignored) {
                // skip an unreadable file, as Android's TrustedCertificateStore does
            }
        }
        return out;
    }

    private static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            return n == buf.length ? buf : Arrays.copyOf(buf, n);
        } finally {
            in.close();
        }
    }

    /** The first certificate of a PEM file (Android's CA files are PEM followed by a text dump), as DER. */
    static byte[] pemToDer(byte[] file) {
        String s = new String(file, StandardCharsets.ISO_8859_1);
        int b = s.indexOf("-----BEGIN CERTIFICATE-----");
        int e = s.indexOf("-----END CERTIFICATE-----", b);
        if (b < 0 || e < 0) return null;
        try {
            return Base64.getMimeDecoder().decode(s.substring(b + 27, e));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private synchronized long anchors() {
        if (anchors == 0) anchors = Tls.nAnchors(anchorDers.toArray());
        return anchors;
    }

    /** Verifies the chain and returns the path to the anchor, leaf first (X509TrustManagerExtensions). */
    public List<X509Certificate> verify(X509Certificate[] chain, String authType, boolean server)
            throws CertificateException {
        if (chain == null || chain.length == 0) throw new IllegalArgumentException("null or zero-length certificate chain");
        if (authType == null || authType.isEmpty()) throw new IllegalArgumentException("null or zero-length authentication type");
        Object[] ders = new Object[chain.length];
        for (int i = 0; i < chain.length; i++) ders[i] = chain[i].getEncoded();
        Object[] path = new Object[1];
        int flags = Tls.nVerifyChain(anchors(), ders, server, path);
        if (flags == -1) throw new CertificateException("Could not parse certificate chain");
        if (flags != 0) {
            String reason;
            if ((flags & Tls.BADCERT_NOT_TRUSTED) != 0) {
                reason = "java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.";
            } else if ((flags & (Tls.BADCERT_EXPIRED | Tls.BADCERT_FUTURE)) != 0) {
                reason = "java.security.cert.CertPathValidatorException: timestamp check failed";
            } else {
                reason = "java.security.cert.CertPathValidatorException: " + Tls.nVerifyText(flags);
            }
            throw new CertificateException(reason);
        }
        List<X509Certificate> out = new ArrayList<X509Certificate>();
        Object[] p = (Object[]) path[0];
        if (p != null) {
            for (Object der : p) {
                if (der != null) out.add(certificate((byte[]) der, chain));
            }
        }
        return out;
    }

    private static X509Certificate certificate(byte[] der, X509Certificate[] known) throws CertificateException {
        for (X509Certificate c : known) {
            if (Arrays.equals(c.getEncoded(), der)) return c;
        }
        return parse(der);
    }

    static X509Certificate parse(byte[] der) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        verify(chain, authType, false);
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        verify(chain, authType, true);
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        verify(chain, authType, false);
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        verify(chain, authType, true);
        if (socket instanceof SSLSocket) {
            SSLSocket s = (SSLSocket) socket;
            checkEndpoint(chain[0], s.getSSLParameters(), s.getHandshakeSession());
        }
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        verify(chain, authType, false);
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        verify(chain, authType, true);
        if (engine != null) checkEndpoint(chain[0], engine.getSSLParameters(), engine.getHandshakeSession());
    }

    static void checkEndpoint(X509Certificate leaf, SSLParameters params, SSLSession session)
            throws CertificateException {
        if (params == null || !"HTTPS".equalsIgnoreCase(params.getEndpointIdentificationAlgorithm())) return;
        String host = session != null ? session.getPeerHost() : null;
        if (host == null || !HostnameVerifierImpl.INSTANCE.verify(host, leaf)) {
            throw new CertificateException("No subjectAltNames on the certificate match");
        }
    }

    public synchronized X509Certificate[] getAcceptedIssuers() {
        if (acceptedIssuers == null) {
            List<X509Certificate> out = new ArrayList<X509Certificate>();
            for (byte[] der : anchorDers) {
                try {
                    out.add(parse(der));
                } catch (CertificateException ignored) {
                    // not an anchor anyone can use
                }
            }
            acceptedIssuers = out.toArray(new X509Certificate[0]);
        }
        return acceptedIssuers.clone();
    }

    int anchorCount() {
        return anchorDers.size();
    }
}
