package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/**
 * RSASSA-PKCS1-v1_5 (RFC 8017 section 8.2) over a built-in digest, and
 * NONEwithRSA, which signs the caller's bytes (usually a DigestInfo) as is.
 */
final class RsaSignature extends SignatureSpi {
    private final MessageDigest digest;
    private final byte[] digestInfoPrefix;
    private final ByteArrayOutputStream raw;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    /** digestName null means NONEwithRSA. */
    RsaSignature(String digestName) {
        if (digestName == null) {
            digest = null;
            digestInfoPrefix = new byte[0];
            raw = new ByteArrayOutputStream();
        } else {
            digest = BuiltinProvider.newDigest(digestName);
            digestInfoPrefix = prefix(digestName);
            raw = null;
        }
    }

    /** The DER of DigestInfo up to the digest bytes. */
    static byte[] prefix(String digestName) {
        String oid;
        switch (digestName) {
            case "MD5": oid = "1.2.840.113549.2.5"; break;
            case "SHA-1": oid = "1.3.14.3.2.26"; break;
            case "SHA-224": oid = "2.16.840.1.101.3.4.2.4"; break;
            case "SHA-256": oid = "2.16.840.1.101.3.4.2.1"; break;
            case "SHA-384": oid = "2.16.840.1.101.3.4.2.2"; break;
            case "SHA-512": oid = "2.16.840.1.101.3.4.2.3"; break;
            default: throw new IllegalArgumentException(digestName);
        }
        int len = BuiltinProvider.newDigest(digestName).getDigestLength();
        byte[] full = Der.seq(Der.seq(Der.oid(oid), Der.nul()), Der.octets(new byte[len]));
        return Arrays.copyOf(full, full.length - len);
    }

    protected void engineInitVerify(PublicKey key) throws InvalidKeyException {
        publicKey = RsaKeys.toPublic(key);
        privateKey = null;
        reset();
    }

    protected void engineInitSign(PrivateKey key) throws InvalidKeyException {
        privateKey = RsaKeys.toPrivate(key);
        publicKey = null;
        reset();
    }

    private void reset() {
        if (digest != null) digest.reset();
        else raw.reset();
    }

    protected void engineUpdate(byte b) {
        if (digest != null) digest.update(b);
        else raw.write(b);
    }

    protected void engineUpdate(byte[] b, int off, int len) {
        if (digest != null) digest.update(b, off, len);
        else raw.write(b, off, len);
    }

    private byte[] encoded(int k) throws SignatureException {
        byte[] t;
        if (digest != null) {
            byte[] h = digest.digest();
            t = new byte[digestInfoPrefix.length + h.length];
            System.arraycopy(digestInfoPrefix, 0, t, 0, digestInfoPrefix.length);
            System.arraycopy(h, 0, t, digestInfoPrefix.length, h.length);
        } else {
            t = raw.toByteArray();
            raw.reset();
        }
        if (t.length > k - 11) throw new SignatureException("Key is too short for this signature algorithm");
        byte[] em = new byte[k];
        em[1] = 1;
        Arrays.fill(em, 2, k - t.length - 1, (byte) 0xff);
        System.arraycopy(t, 0, em, k - t.length, t.length);
        return em;
    }

    protected byte[] engineSign() throws SignatureException {
        if (privateKey == null) throw new SignatureException("Not initialized for signing");
        try {
            return RsaKeys.privateOp(privateKey, encoded(RsaKeys.byteLength(privateKey)), appRandom);
        } catch (javax.crypto.BadPaddingException e) {
            throw new SignatureException(e);
        }
    }

    protected boolean engineVerify(byte[] sig) throws SignatureException {
        if (publicKey == null) throw new SignatureException("Not initialized for verification");
        int k = RsaKeys.byteLength(publicKey);
        byte[] expected = encoded(k);
        if (sig.length != k) {
            throw new SignatureException("Bad signature length: got " + sig.length + " but was expecting " + k);
        }
        byte[] em;
        try {
            em = RsaKeys.publicOp(publicKey, sig);
        } catch (javax.crypto.BadPaddingException e) {
            return false;
        }
        return MessageDigest.isEqual(em, expected);
    }

    @Deprecated
    protected void engineSetParameter(String param, Object value) {
        throw new InvalidParameterException("No parameters");
    }

    @Deprecated
    protected Object engineGetParameter(String param) {
        throw new InvalidParameterException("No parameters");
    }
}
