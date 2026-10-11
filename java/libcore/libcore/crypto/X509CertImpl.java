package libcore.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.security.auth.x500.X500Principal;

/**
 * The built-in X.509 certificate (RFC 5280), parsed from DER by CertificateFactory "X.509".
 * getEncoded() is the exact input DER. Getters follow OpenJDK's X509CertImpl (empty
 * extension sets, -1 and MAX_VALUE from getBasicConstraints, alternative-name lists);
 * getIssuerDN/getSubjectDN return the X500Principal as Android's do. The public key
 * and extensions are decoded lazily so that loading a trust store stays cheap.
 * Framework-internal: the static helpers back java.security.cert.X509Certificate's
 * non-abstract methods.
 */
public final class X509CertImpl extends X509Certificate {
    private static final long serialVersionUID = 1L;

    public static final String SUBJECT_ALT_NAME_OID = "2.5.29.17", ISSUER_ALT_NAME_OID = "2.5.29.18";
    static final String KEY_USAGE_OID = "2.5.29.15", BASIC_CONSTRAINTS_OID = "2.5.29.19", EXT_KEY_USAGE_OID = "2.5.29.37";

    /** Extensions OpenJDK decodes; any other critical extension makes hasUnsupportedCriticalExtension() true. */
    private static final Set<String> SUPPORTED = new HashSet<String>(Arrays.asList(
            "2.5.29.14", "2.5.29.15", "2.5.29.16", "2.5.29.17", "2.5.29.18", "2.5.29.19", "2.5.29.20", "2.5.29.21",
            "2.5.29.30", "2.5.29.33", "2.5.29.35", "2.5.29.36", "2.16.840.1.113730.1.1", "2.5.29.32", "2.5.29.37",
            "2.5.29.54", "2.5.29.31", "2.5.29.29", "1.3.6.1.5.5.7.1.11", "1.3.6.1.5.5.7.1.1", "2.5.29.28", "2.5.29.27",
            "2.5.29.46", "1.3.6.1.5.5.7.48.1.5"));

    private static final Map<String, String> SIG_ALGS = new HashMap<String, String>();

    static {
        String[] m = {
            "1.2.840.113549.1.1.2", "MD2withRSA",
            "1.2.840.113549.1.1.4", "MD5withRSA",
            "1.2.840.113549.1.1.5", "SHA1withRSA",
            "1.2.840.113549.1.1.14", "SHA224withRSA",
            "1.2.840.113549.1.1.11", "SHA256withRSA",
            "1.2.840.113549.1.1.12", "SHA384withRSA",
            "1.2.840.113549.1.1.13", "SHA512withRSA",
            "1.2.840.113549.1.1.15", "SHA512/224withRSA",
            "1.2.840.113549.1.1.16", "SHA512/256withRSA",
            "1.2.840.113549.1.1.10", "RSASSA-PSS",
            "1.2.840.10045.4.1", "SHA1withECDSA",
            "1.2.840.10045.4.3.1", "SHA224withECDSA",
            "1.2.840.10045.4.3.2", "SHA256withECDSA",
            "1.2.840.10045.4.3.3", "SHA384withECDSA",
            "1.2.840.10045.4.3.4", "SHA512withECDSA",
            "1.2.840.10040.4.3", "SHA1withDSA",
            "2.16.840.1.101.3.4.3.1", "SHA224withDSA",
            "2.16.840.1.101.3.4.3.2", "SHA256withDSA",
            "1.3.101.112", "Ed25519",
            "1.3.101.113", "Ed448",
        };
        for (int i = 0; i < m.length; i += 2) SIG_ALGS.put(m[i], m[i + 1]);
    }

    private final byte[] encoded;
    private final byte[] tbs;
    private final int version;
    private final BigInteger serial;
    private final String sigAlgOid;
    private final byte[] sigAlgParams;
    private final byte[] signature;
    private final byte[] issuerDer, subjectDer;
    private final long notBefore, notAfter;
    private final byte[] spki;
    private final boolean[] issuerUid, subjectUid;
    /** Extension OID to {critical (Boolean), extnValue content (byte[])}, in certificate order; null for none. */
    private final LinkedHashMap<String, Object[]> extensions;

    private transient PublicKey publicKey;
    private transient X500Principal issuer, subject;

    /** Parses one certificate; der must hold exactly the certificate. */
    public X509CertImpl(byte[] der) throws CertificateException {
        try {
            encoded = der.clone();
            Der.Reader top = new Der.Reader(encoded);
            Der.Reader cert = top.sequence();
            top.end();
            tbs = cert.element();
            Der.Reader alg = cert.sequence();
            sigAlgOid = alg.oid();
            sigAlgParams = alg.more() ? alg.element() : null;
            signature = cert.bits();
            cert.end();

            Der.Reader t = new Der.Reader(tbs).sequence();
            int v = 1;
            if (t.peekTag() == Der.CONTEXT0) {
                Der.Reader vr = t.constructed(Der.CONTEXT0);
                v = vr.integer().intValue() + 1;
                vr.end();
                if (v < 1 || v > 3) throw new IOException("unsupported version " + v);
            }
            version = v;
            serial = t.integer();
            t.sequence();
            issuerDer = t.element();
            Der.Reader validity = t.sequence();
            notBefore = validity.time();
            notAfter = validity.time();
            validity.end();
            subjectDer = t.element();
            spki = t.element();
            if (new Der.Reader(spki).peekTag() != Der.SEQUENCE) throw new IOException("bad SubjectPublicKeyInfo");
            boolean[] iu = null, su = null;
            LinkedHashMap<String, Object[]> exts = null;
            if (t.more() && t.peekTag() == 0x81) iu = t.bitArray(0x81);
            if (t.more() && t.peekTag() == 0x82) su = t.bitArray(0x82);
            if (t.more() && t.peekTag() == Der.CONTEXT3) {
                Der.Reader er = t.constructed(Der.CONTEXT3);
                Der.Reader list = er.sequence();
                er.end();
                exts = new LinkedHashMap<String, Object[]>();
                while (list.more()) {
                    Der.Reader e = list.sequence();
                    String oid = e.oid();
                    boolean critical = e.peekTag() == Der.BOOLEAN && e.bool();
                    byte[] value = e.octets();
                    e.end();
                    if (exts.put(oid, new Object[] {critical, value}) != null) throw new IOException("Duplicate extensions not allowed");
                }
            }
            t.end();
            issuerUid = iu;
            subjectUid = su;
            extensions = exts;
            // Validate the names now, as OpenJDK does, so a bad certificate fails in generateCertificate.
            issuer = new X500Principal(issuerDer);
            subject = new X500Principal(subjectDer);
        } catch (IOException | IllegalArgumentException e) {
            throw new CertificateException("Unable to initialize, " + e, e);
        }
    }

    public byte[] getEncoded() {
        return encoded.clone();
    }

    /** The encoding without a copy; framework-internal callers must not modify it. */
    public byte[] encodedNoCopy() {
        return encoded;
    }

    public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
        checkValidity(new Date());
    }

    public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
        if (date.getTime() > notAfter) throw new CertificateExpiredException("NotAfter: " + new Date(notAfter));
        if (date.getTime() < notBefore) throw new CertificateNotYetValidException("NotBefore: " + new Date(notBefore));
    }

    public int getVersion() {
        return version;
    }

    public BigInteger getSerialNumber() {
        return serial;
    }

    public Principal getIssuerDN() {
        return getIssuerX500Principal();
    }

    public X500Principal getIssuerX500Principal() {
        if (issuer == null) issuer = new X500Principal(issuerDer);
        return issuer;
    }

    public Principal getSubjectDN() {
        return getSubjectX500Principal();
    }

    public X500Principal getSubjectX500Principal() {
        if (subject == null) subject = new X500Principal(subjectDer);
        return subject;
    }

    public Date getNotBefore() {
        return new Date(notBefore);
    }

    public Date getNotAfter() {
        return new Date(notAfter);
    }

    public byte[] getTBSCertificate() {
        return tbs.clone();
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    public String getSigAlgName() {
        String n = SIG_ALGS.get(sigAlgOid);
        return n != null ? n : sigAlgOid;
    }

    public String getSigAlgOID() {
        return sigAlgOid;
    }

    public byte[] getSigAlgParams() {
        if (sigAlgParams == null || (sigAlgParams.length == 2 && sigAlgParams[0] == Der.NULL)) return null;
        return sigAlgParams.clone();
    }

    public boolean[] getIssuerUniqueID() {
        return issuerUid == null ? null : issuerUid.clone();
    }

    public boolean[] getSubjectUniqueID() {
        return subjectUid == null ? null : subjectUid.clone();
    }

    private byte[] extension(String oid) {
        if (extensions == null) return null;
        Object[] e = extensions.get(oid);
        return e == null ? null : (byte[]) e[1];
    }

    public boolean[] getKeyUsage() {
        byte[] v = extension(KEY_USAGE_OID);
        if (v == null) return null;
        try {
            boolean[] bits = new Der.Reader(v).bitArray(Der.BIT_STRING);
            return bits.length >= 9 ? bits : Arrays.copyOf(bits, 9);
        } catch (IOException e) {
            return null;
        }
    }

    public List<String> getExtendedKeyUsage() throws CertificateParsingException {
        return extendedKeyUsage(extension(EXT_KEY_USAGE_OID));
    }

    public int getBasicConstraints() {
        byte[] v = extension(BASIC_CONSTRAINTS_OID);
        if (v == null) return -1;
        try {
            Der.Reader r = new Der.Reader(v).sequence();
            boolean ca = r.more() && r.peekTag() == Der.BOOLEAN && r.bool();
            if (!ca) return -1;
            if (r.more() && r.peekTag() == Der.INTEGER) {
                BigInteger len = r.integer();
                return len.bitLength() < 31 ? len.intValue() : Integer.MAX_VALUE;
            }
            return Integer.MAX_VALUE;
        } catch (IOException e) {
            return -1;
        }
    }

    public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
        return alternativeNames(extension(SUBJECT_ALT_NAME_OID));
    }

    public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
        return alternativeNames(extension(ISSUER_ALT_NAME_OID));
    }

    public boolean hasUnsupportedCriticalExtension() {
        if (extensions == null) return false;
        for (Map.Entry<String, Object[]> e : extensions.entrySet()) {
            if ((Boolean) e.getValue()[0] && !SUPPORTED.contains(e.getKey())) return true;
        }
        return false;
    }

    private Set<String> extensionOids(boolean critical) {
        if (extensions == null) return null;
        TreeSet<String> out = new TreeSet<String>();
        for (Map.Entry<String, Object[]> e : extensions.entrySet()) {
            if ((Boolean) e.getValue()[0] == critical) out.add(e.getKey());
        }
        return out;
    }

    public Set<String> getCriticalExtensionOIDs() {
        return extensionOids(true);
    }

    public Set<String> getNonCriticalExtensionOIDs() {
        return extensionOids(false);
    }

    public byte[] getExtensionValue(String oid) {
        byte[] v = extension(oid);
        return v == null ? null : Der.octets(v);
    }

    public PublicKey getPublicKey() {
        if (publicKey == null) publicKey = decodeKey(spki);
        return publicKey;
    }

    /** The SubjectPublicKeyInfo bytes without a copy; framework-internal callers must not modify them. */
    public byte[] subjectPublicKeyInfo() {
        return spki;
    }

    public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchProviderException, SignatureException {
        verify(key, (String) null);
    }

    public void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException,
            InvalidKeyException, NoSuchProviderException, SignatureException {
        String alg = getSigAlgName();
        Signature s = sigProvider == null || sigProvider.isEmpty() ? Signature.getInstance(alg) : Signature.getInstance(alg, sigProvider);
        check(s, key, tbs, signature);
    }

    public void verify(PublicKey key, Provider sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String alg = getSigAlgName();
        Signature s = sigProvider == null ? Signature.getInstance(alg) : Signature.getInstance(alg, sigProvider);
        check(s, key, tbs, signature);
    }

    private static void check(Signature s, PublicKey key, byte[] tbs, byte[] sig) throws InvalidKeyException, SignatureException {
        s.initVerify(key);
        s.update(tbs);
        if (!s.verify(sig)) throw new SignatureException("Signature does not match.");
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n[\n  Version: V").append(version);
        sb.append("\n  Subject: ").append(getSubjectX500Principal());
        sb.append("\n  Signature Algorithm: ").append(getSigAlgName()).append(", OID = ").append(sigAlgOid);
        sb.append("\n\n  Key:  ").append(getPublicKey());
        sb.append("\n  Validity: [From: ").append(new Date(notBefore)).append(",\n               To: ").append(new Date(notAfter)).append(']');
        sb.append("\n  Issuer: ").append(getIssuerX500Principal());
        sb.append("\n  SerialNumber: ").append(serial.toString(16));
        if (extensions != null) {
            sb.append("\n\nCertificate Extensions: ").append(extensions.size());
            for (Map.Entry<String, Object[]> e : extensions.entrySet()) {
                sb.append("\n  ObjectId: ").append(e.getKey()).append((Boolean) e.getValue()[0] ? " Criticality=true" : " Criticality=false");
            }
        }
        sb.append("\n]\n  Algorithm: [").append(getSigAlgName()).append("]\n  Signature: ").append(signature.length).append(" bytes\n]");
        return sb.toString();
    }

    // ------------------------------------------------------------------ shared decoding

    /** Public key from a SubjectPublicKeyInfo: RSA and EC through KeyFactory, anything else as an opaque X.509 key. */
    static PublicKey decodeKey(byte[] spki) {
        String oid = null;
        try {
            Der.Reader r = new Der.Reader(spki).sequence();
            oid = r.sequence().oid();
            String alg = RsaKeys.RSA_OID.equals(oid) ? "RSA" : EcKeys.EC_OID.equals(oid) ? "EC" : null;
            if (alg != null) return KeyFactory.getInstance(alg).generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            // Unsupported curve or malformed key: fall back to the opaque key, as OpenJDK's X509Key does.
        }
        return new OpaqueKey(keyAlgorithm(oid), spki);
    }

    private static String keyAlgorithm(String oid) {
        if (oid == null) return "unknown";
        if (oid.equals(RsaKeys.RSA_OID)) return "RSA";
        if (oid.equals(EcKeys.EC_OID)) return "EC";
        if (oid.equals("1.2.840.113549.1.1.10")) return "RSASSA-PSS";
        if (oid.equals("1.2.840.10040.4.1")) return "DSA";
        if (oid.equals("1.3.101.110")) return "X25519";
        if (oid.equals("1.3.101.111")) return "X448";
        if (oid.equals("1.3.101.112")) return "Ed25519";
        if (oid.equals("1.3.101.113")) return "Ed448";
        return oid;
    }

    /** A public key we cannot decode: algorithm name and its X.509 encoding. */
    static final class OpaqueKey implements PublicKey {
        private static final long serialVersionUID = 1L;
        private final String algorithm;
        private final byte[] encoded;

        OpaqueKey(String algorithm, byte[] encoded) {
            this.algorithm = algorithm;
            this.encoded = encoded.clone();
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getFormat() {
            return "X.509";
        }

        public byte[] getEncoded() {
            return encoded.clone();
        }

        public boolean equals(Object o) {
            return o instanceof PublicKey && Arrays.equals(encoded, ((PublicKey) o).getEncoded());
        }

        public int hashCode() {
            return Arrays.hashCode(encoded);
        }

        public String toString() {
            return "algorithm = " + algorithm + ", unparsed keybits = " + encoded.length + " bytes";
        }
    }

    static List<String> extendedKeyUsage(byte[] v) throws CertificateParsingException {
        if (v == null) return null;
        try {
            Der.Reader r = new Der.Reader(v).sequence();
            ArrayList<String> out = new ArrayList<String>();
            while (r.more()) out.add(r.oid());
            return Collections.unmodifiableList(out);
        } catch (IOException e) {
            throw new CertificateParsingException(e);
        }
    }

    static Collection<List<?>> alternativeNames(byte[] v) throws CertificateParsingException {
        if (v == null) return null;
        try {
            Der.Reader r = new Der.Reader(v).sequence();
            ArrayList<List<?>> out = new ArrayList<List<?>>();
            while (r.more()) {
                int tag = r.peekTag();
                Object value;
                int type = tag & 0x1f;
                switch (tag) {
                    case 0x81:
                    case 0x82:
                    case 0x86:
                        value = new String(r.read(tag), java.nio.charset.StandardCharsets.ISO_8859_1);
                        break;
                    case 0xa4: {
                        Der.Reader dn = r.constructed(tag);
                        value = new X500Principal(dn.element()).getName();
                        dn.end();
                        break;
                    }
                    case 0x87:
                        value = ipAddress(r.read(tag));
                        break;
                    case 0x88:
                        value = Der.oidString(r.read(tag));
                        break;
                    case 0xa0:
                    case 0xa3:
                    case 0xa5:
                        value = r.element();
                        break;
                    default:
                        throw new IOException("Unknown GeneralName tag 0x" + Integer.toHexString(tag));
                }
                out.add(Collections.unmodifiableList(Arrays.asList(Integer.valueOf(type), value)));
            }
            return Collections.unmodifiableCollection(out);
        } catch (IOException | IllegalArgumentException e) {
            throw new CertificateParsingException(e);
        }
    }

    private static String ipAddress(byte[] a) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (a.length == 4) {
            for (int i = 0; i < 4; i++) {
                if (i > 0) sb.append('.');
                sb.append(a[i] & 0xff);
            }
        } else if (a.length == 16) {
            for (int i = 0; i < 16; i += 2) {
                if (i > 0) sb.append(':');
                sb.append(Integer.toHexString(((a[i] & 0xff) << 8) | (a[i + 1] & 0xff)));
            }
        } else {
            throw new IOException("Invalid IP address length " + a.length);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers for other X509Certificate subclasses

    private static Der.Reader tbsAfterSignature(X509Certificate c) throws CertificateEncodingException, IOException {
        Der.Reader t = new Der.Reader(c.getTBSCertificate()).sequence();
        if (t.peekTag() == Der.CONTEXT0) t.element();
        t.integer();
        t.sequence();
        return t;
    }

    public static X500Principal issuerOf(X509Certificate c) {
        if (c instanceof X509CertImpl) return ((X509CertImpl) c).getIssuerX500Principal();
        try {
            return new X500Principal(tbsAfterSignature(c).element());
        } catch (CertificateEncodingException | IOException e) {
            return null;
        }
    }

    public static X500Principal subjectOf(X509Certificate c) {
        if (c instanceof X509CertImpl) return ((X509CertImpl) c).getSubjectX500Principal();
        try {
            Der.Reader t = tbsAfterSignature(c);
            t.element();
            t.sequence();
            return new X500Principal(t.element());
        } catch (CertificateEncodingException | IOException e) {
            return null;
        }
    }

    /** extnValue content of an extension of any X509Certificate, from its OCTET STRING wrapping. */
    private static byte[] extensionOf(X509Certificate c, String oid) throws CertificateParsingException {
        byte[] v = c.getExtensionValue(oid);
        if (v == null) return null;
        try {
            return new Der.Reader(v).octets();
        } catch (IOException e) {
            throw new CertificateParsingException(e);
        }
    }

    public static List<String> extendedKeyUsageOf(X509Certificate c) throws CertificateParsingException {
        return extendedKeyUsage(extensionOf(c, EXT_KEY_USAGE_OID));
    }

    public static Collection<List<?>> alternativeNamesOf(X509Certificate c, String oid) throws CertificateParsingException {
        return alternativeNames(extensionOf(c, oid));
    }

    public static void verify(X509Certificate c, PublicKey key, Provider sigProvider)
            throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        String alg = c.getSigAlgName();
        Signature s = sigProvider == null ? Signature.getInstance(alg) : Signature.getInstance(alg, sigProvider);
        check(s, key, c.getTBSCertificate(), c.getSignature());
    }
}
