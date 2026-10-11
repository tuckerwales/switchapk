package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactorySpi;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * CertificateFactory "X.509": DER or PEM certificates, read one block at a time
 * as OpenJDK's X509Factory does (text before a PEM header is skipped, the stream
 * is left just after the block), PKCS#7 SignedData bundles in generateCertificates,
 * and PkiPath/PKCS7 cert paths. CRLs are not supported.
 */
final class X509Factory extends CertificateFactorySpi {
    private static final String PKCS7_SIGNED_DATA = "1.2.840.113549.1.7.2", PKCS7_DATA = "1.2.840.113549.1.7.1";
    private static final List<String> ENCODINGS = Collections.unmodifiableList(Arrays.asList("PkiPath", "PKCS7"));

    public Certificate engineGenerateCertificate(InputStream is) throws CertificateException {
        if (is == null) throw new CertificateException("Missing input stream");
        try {
            byte[] der = readBlock(is);
            if (der == null) throw new IOException("Empty input");
            return new X509CertImpl(der);
        } catch (IOException e) {
            throw new CertificateException("Could not parse certificate: " + e.toString(), e);
        }
    }

    public Collection<? extends Certificate> engineGenerateCertificates(InputStream is) throws CertificateException {
        if (is == null) throw new CertificateException("Missing input stream");
        try {
            PushbackInputStream in = new PushbackInputStream(is);
            ArrayList<X509Certificate> out = new ArrayList<X509Certificate>();
            int first = in.read();
            if (first == -1) return out;
            in.unread(first);
            byte[] data = readBlock(in);
            if (data == null) throw new CertificateException("No certificate data found");
            List<X509Certificate> pkcs7 = pkcs7Certificates(data);
            if (pkcs7 != null) return pkcs7;
            while (data != null) {
                out.add(new X509CertImpl(data));
                data = readBlock(in);
            }
            return out;
        } catch (IOException e) {
            throw new CertificateException(e);
        }
    }

    public CRL engineGenerateCRL(InputStream is) throws CRLException {
        throw new CRLException("X.509 CRLs are not supported");
    }

    public Collection<? extends CRL> engineGenerateCRLs(InputStream is) throws CRLException {
        throw new CRLException("X.509 CRLs are not supported");
    }

    public CertPath engineGenerateCertPath(InputStream is) throws CertificateException {
        return engineGenerateCertPath(is, "PkiPath");
    }

    public CertPath engineGenerateCertPath(InputStream is, String encoding) throws CertificateException {
        if (is == null) throw new CertificateException("input stream is null");
        try {
            byte[] data = readBlock(is);
            if (data == null) throw new CertificateException("Empty input");
            if ("PkiPath".equals(encoding)) {
                Der.Reader r = new Der.Reader(data).sequence();
                ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>();
                while (r.more()) certs.add(new X509CertImpl(r.element()));
                Collections.reverse(certs);
                return new X509CertPath(certs);
            }
            if ("PKCS7".equals(encoding)) {
                List<X509Certificate> certs = pkcs7Certificates(data);
                if (certs == null) throw new CertificateException("not a PKCS#7 SignedData");
                return new X509CertPath(certs);
            }
            throw new CertificateException("unsupported encoding");
        } catch (IOException e) {
            throw new CertificateException("IOException parsing " + encoding + " encoded cert path: " + e, e);
        }
    }

    public CertPath engineGenerateCertPath(List<? extends Certificate> certificates) throws CertificateException {
        ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (Certificate c : certificates) {
            if (!(c instanceof X509Certificate)) {
                throw new CertificateException("List is not all X509Certificates: " + c.getClass().getName());
            }
            certs.add((X509Certificate) c);
        }
        return new X509CertPath(certs);
    }

    public Iterator<String> engineGetCertPathEncodings() {
        return ENCODINGS.iterator();
    }

    /** The certificates of a PKCS#7 ContentInfo holding SignedData, or null if data is not one. */
    private static List<X509Certificate> pkcs7Certificates(byte[] data) throws CertificateException {
        Der.Reader sd;
        try {
            Der.Reader ci = new Der.Reader(data).sequence();
            if (!PKCS7_SIGNED_DATA.equals(ci.oid())) return null;
            sd = ci.constructed(Der.CONTEXT0).sequence();
            sd.integer();
            sd.set();
            sd.sequence();
        } catch (IOException e) {
            return null;
        }
        ArrayList<X509Certificate> out = new ArrayList<X509Certificate>();
        try {
            if (sd.more() && sd.peekTag() == Der.CONTEXT0) {
                Der.Reader certs = sd.constructed(Der.CONTEXT0);
                while (certs.more()) out.add(new X509CertImpl(certs.element()));
            }
        } catch (IOException e) {
            throw new CertificateException(e);
        }
        return out;
    }

    static byte[] pkcs7Encode(List<? extends X509Certificate> certs) throws CertificateEncodingException {
        byte[][] enc = new byte[certs.size()][];
        for (int i = 0; i < enc.length; i++) enc[i] = certs.get(i).getEncoded();
        byte[] signedData = Der.seq(Der.integer(1), Der.tlv(Der.SET, new byte[0]), Der.seq(Der.oid(PKCS7_DATA)),
                Der.tlv(Der.CONTEXT0, setContent(Der.setOf(enc))),
                Der.tlv(Der.SET, new byte[0]));
        return Der.seq(Der.oid(PKCS7_SIGNED_DATA), Der.tlv(Der.CONTEXT0, signedData));
    }

    /** The content of a TLV (the SET OF certificates becomes the content of the [0] IMPLICIT field). */
    private static byte[] setContent(byte[] tlv) {
        try {
            return new Der.Reader(tlv).read(Der.SET);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads one DER element or one PEM block (skipping text before its header) and
     * returns the DER, or null at the end of the input. Reads byte by byte so the
     * stream is left just after the block.
     */
    static byte[] readBlock(InputStream is) throws IOException {
        int c = is.read();
        if (c == -1) return null;
        if (c == Der.SEQUENCE) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(c);
            int len = is.read();
            if (len < 0) throw new IOException("Incomplete data");
            out.write(len);
            if (len >= 0x80) {
                int n = len & 0x7f;
                if (n == 0 || n > 4) throw new IOException("Invalid length bytes");
                len = 0;
                for (int i = 0; i < n; i++) {
                    int b = is.read();
                    if (b < 0) throw new IOException("Incomplete data");
                    out.write(b);
                    len = (len << 8) | b;
                }
                if (len < 0) throw new IOException("Invalid length");
            }
            byte[] content = new byte[len];
            int off = 0;
            while (off < len) {
                int r = is.read(content, off, len - off);
                if (r < 0) throw new IOException("Incomplete data");
                off += r;
            }
            out.write(content, 0, len);
            return out.toByteArray();
        }
        // PEM. Step 1: find five hyphens at the start of a line.
        int hyphens = c == '-' ? 1 : 0;
        int last = c == '-' ? -1 : c;
        while (true) {
            int next = is.read();
            if (next == -1) return null;
            if (next == '-') {
                hyphens++;
            } else {
                hyphens = 0;
                last = next;
            }
            if (hyphens == 5 && (last == -1 || last == '\r' || last == '\n')) break;
        }
        // Step 2: the rest of the header line.
        StringBuilder header = new StringBuilder("-----");
        int end;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (true) {
            int next = is.read();
            if (next == -1) throw new IOException("Incomplete data");
            if (next == '\n') {
                end = '\n';
                break;
            }
            if (next == '\r') {
                next = is.read();
                if (next == -1) throw new IOException("Incomplete data");
                if (next == '\n') {
                    end = '\n';
                } else {
                    end = '\r';
                    data.write(next);
                }
                break;
            }
            header.append((char) next);
        }
        // Step 3: base64 up to the footer.
        while (true) {
            int next = is.read();
            if (next == -1) throw new IOException("Incomplete data");
            if (next == '-') break;
            data.write(next);
        }
        // Step 4: the footer line.
        StringBuilder footer = new StringBuilder("-");
        while (true) {
            int next = is.read();
            if (next == -1 || next == end || next == '\n') break;
            if (next != '\r') footer.append((char) next);
        }
        checkHeaderFooter(header.toString().trim(), footer.toString().trim());
        String b64 = new String(data.toByteArray(), StandardCharsets.ISO_8859_1).replaceAll("\\s+", "");
        try {
            return java.util.Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static void checkHeaderFooter(String header, String footer) throws IOException {
        if (header.length() < 16 || !header.startsWith("-----BEGIN ") || !header.endsWith("-----")) {
            throw new IOException("Illegal header: " + header);
        }
        if (footer.length() < 14 || !footer.startsWith("-----END ") || !footer.endsWith("-----")) {
            throw new IOException("Illegal footer: " + footer);
        }
        String headerType = header.substring(11, header.length() - 5);
        String footerType = footer.substring(9, footer.length() - 5);
        if (!headerType.equals(footerType)) throw new IOException("Header and footer do not match: " + header + " " + footer);
    }

    /** A CertPath of X.509 certificates, encoded as PkiPath (the default) or PKCS7. */
    static final class X509CertPath extends CertPath {
        private static final long serialVersionUID = 1L;
        private final List<X509Certificate> certs;

        X509CertPath(List<X509Certificate> certs) {
            super("X.509");
            this.certs = Collections.unmodifiableList(new ArrayList<X509Certificate>(certs));
        }

        public Iterator<String> getEncodings() {
            return ENCODINGS.iterator();
        }

        public byte[] getEncoded() throws CertificateEncodingException {
            return getEncoded("PkiPath");
        }

        public byte[] getEncoded(String encoding) throws CertificateEncodingException {
            if ("PkiPath".equals(encoding)) {
                byte[][] enc = new byte[certs.size()][];
                for (int i = 0; i < enc.length; i++) enc[i] = certs.get(enc.length - 1 - i).getEncoded();
                return Der.tlv(Der.SEQUENCE, Der.concat(enc));
            }
            if ("PKCS7".equals(encoding)) return pkcs7Encode(certs);
            throw new CertificateEncodingException("unsupported encoding");
        }

        public List<? extends Certificate> getCertificates() {
            return certs;
        }
    }
}
