package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.ECPoint;
import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

/** ECDSA (FIPS 186-4) with DER signatures, and ECDH key agreement (the shared X coordinate). */
final class Ecdsa extends SignatureSpi {
    private final MessageDigest digest;
    private final ByteArrayOutputStream raw;
    private EcKeys.Public pub;
    private EcKeys.Private priv;

    /** digestName null means NONEwithECDSA (the caller passes the hash). */
    Ecdsa(String digestName) {
        digest = digestName != null ? BuiltinProvider.newDigest(digestName) : null;
        raw = digestName == null ? new ByteArrayOutputStream() : null;
    }

    protected void engineInitVerify(PublicKey key) throws InvalidKeyException {
        pub = EcKeys.toPublic(key);
        priv = null;
        reset();
    }

    protected void engineInitSign(PrivateKey key) throws InvalidKeyException {
        priv = EcKeys.toPrivate(key);
        pub = null;
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

    private BigInteger hash(EcCurve c) {
        byte[] h;
        if (digest != null) {
            h = digest.digest();
        } else {
            h = raw.toByteArray();
            raw.reset();
        }
        BigInteger e = new BigInteger(1, h);
        int excess = h.length * 8 - c.n.bitLength();
        return excess > 0 ? e.shiftRight(excess) : e;
    }

    protected byte[] engineSign() throws SignatureException {
        if (priv == null) throw new SignatureException("Not initialized for signing");
        EcCurve c = priv.curve;
        BigInteger e = hash(c);
        SecureRandom rnd = appRandom != null ? appRandom : new SecureRandom();
        while (true) {
            BigInteger k = EcKeys.randomScalar(c, rnd);
            BigInteger r = c.multiply(c.g, k).getAffineX().mod(c.n);
            if (r.signum() == 0) continue;
            BigInteger s = k.modInverse(c.n).multiply(e.add(r.multiply(priv.s))).mod(c.n);
            if (s.signum() == 0) continue;
            return Der.seq(Der.integer(r), Der.integer(s));
        }
    }

    protected boolean engineVerify(byte[] sig) throws SignatureException {
        if (pub == null) throw new SignatureException("Not initialized for verification");
        EcCurve c = pub.curve;
        BigInteger e = hash(c);
        BigInteger r, s;
        try {
            Der.Reader rd = new Der.Reader(sig).sequence();
            r = rd.integer();
            s = rd.integer();
            rd.end();
        } catch (IOException ex) {
            throw new SignatureException("Could not verify signature", ex);
        }
        if (r.signum() <= 0 || r.compareTo(c.n) >= 0 || s.signum() <= 0 || s.compareTo(c.n) >= 0) return false;
        BigInteger w = s.modInverse(c.n);
        ECPoint pt = c.multiplyAdd(e.multiply(w).mod(c.n), pub.w, r.multiply(w).mod(c.n));
        if (pt == ECPoint.POINT_INFINITY) return false;
        return pt.getAffineX().mod(c.n).equals(r);
    }

    @Deprecated
    protected void engineSetParameter(String param, Object value) {
        throw new InvalidParameterException("No parameters");
    }

    @Deprecated
    protected Object engineGetParameter(String param) {
        throw new InvalidParameterException("No parameters");
    }

    /** KeyAgreement "ECDH". */
    static final class Ecdh extends KeyAgreementSpi {
        private EcKeys.Private priv;
        private byte[] secret;

        protected void engineInit(java.security.Key key, SecureRandom random) throws InvalidKeyException {
            if (!(key instanceof PrivateKey)) throw new InvalidKeyException("ECDH needs an EC private key");
            priv = EcKeys.toPrivate(key);
            secret = null;
        }

        protected void engineInit(java.security.Key key, java.security.spec.AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidKeyException, java.security.InvalidAlgorithmParameterException {
            if (params != null) throw new java.security.InvalidAlgorithmParameterException("Parameters not supported");
            engineInit(key, random);
        }

        protected java.security.Key engineDoPhase(java.security.Key key, boolean lastPhase) throws InvalidKeyException {
            if (priv == null) throw new IllegalStateException("Not initialized");
            if (secret != null) throw new IllegalStateException("Phase already executed");
            if (!lastPhase) throw new IllegalStateException("Only two party agreement supported, lastPhase must be true");
            if (!(key instanceof PublicKey)) throw new InvalidKeyException("ECDH needs an EC public key");
            EcKeys.Public pub = EcKeys.toPublic(key);
            if (pub.curve != priv.curve) throw new InvalidKeyException("Keys are on different curves");
            pub.curve.validate(pub.w);
            ECPoint shared = priv.curve.multiply(pub.w, priv.s);
            if (shared == ECPoint.POINT_INFINITY) throw new InvalidKeyException("Invalid shared point");
            secret = Der.unsigned(shared.getAffineX(), priv.curve.fieldBytes);
            return null;
        }

        protected byte[] engineGenerateSecret() {
            if (secret == null) throw new IllegalStateException("Key agreement has not been completed yet");
            byte[] s = secret;
            secret = null;
            return s;
        }

        protected int engineGenerateSecret(byte[] out, int offset) throws ShortBufferException {
            if (secret == null) throw new IllegalStateException("Key agreement has not been completed yet");
            if (out.length - offset < secret.length) throw new ShortBufferException("Need " + secret.length + " bytes");
            System.arraycopy(secret, 0, out, offset, secret.length);
            int n = secret.length;
            secret = null;
            return n;
        }

        protected SecretKey engineGenerateSecret(String algorithm) throws java.security.NoSuchAlgorithmException {
            if (algorithm == null) throw new java.security.NoSuchAlgorithmException("Algorithm must not be null");
            if (!algorithm.equals("TlsPremasterSecret")) throw new java.security.NoSuchAlgorithmException("Only supported for algorithm TlsPremasterSecret");
            return new SecretKeySpec(engineGenerateSecret(), algorithm);
        }
    }
}
