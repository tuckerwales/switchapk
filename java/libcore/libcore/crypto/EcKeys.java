package libcore.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.security.AlgorithmParametersSpi;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactorySpi;
import java.security.KeyPair;
import java.security.KeyPairGeneratorSpi;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * EC keys on the named NIST curves: X.509 and PKCS#8 encodings (the curve as a
 * named-curve OID; the private key as SEC 1 ECPrivateKey with version 1 and a
 * fixed-length scalar, as the JDK writes it; parameters and public key inside
 * are accepted when parsing), KeyFactory "EC", KeyPairGenerator "EC" and
 * AlgorithmParameters "EC".
 */
final class EcKeys {
    static final String EC_OID = "1.2.840.10045.2.1";

    private EcKeys() {
    }

    static byte[] algId(EcCurve c) {
        return Der.seq(Der.oid(EC_OID), Der.oid(c.oid));
    }

    static final class Public implements ECPublicKey {
        private static final long serialVersionUID = 1L;
        final EcCurve curve;
        final ECPoint w;

        Public(EcCurve curve, ECPoint w) {
            this.curve = curve;
            this.w = w;
        }

        public ECPoint getW() {
            return w;
        }

        public ECParameterSpec getParams() {
            return curve.spec;
        }

        public String getAlgorithm() {
            return "EC";
        }

        public String getFormat() {
            return "X.509";
        }

        public byte[] getEncoded() {
            return Der.seq(algId(curve), Der.bits(curve.encodePoint(w)));
        }

        public boolean equals(Object o) {
            if (!(o instanceof ECPublicKey)) return false;
            ECPublicKey k = (ECPublicKey) o;
            return w.equals(k.getW()) && EcCurve.of(k.getParams()) == curve;
        }

        public int hashCode() {
            return w.hashCode() ^ curve.hashCode();
        }

        public String toString() {
            return "EC public key, " + curve.p.bitLength() + " bits\n  public x coord: " + w.getAffineX()
                    + "\n  public y coord: " + w.getAffineY() + "\n  parameters: " + curve.spec;
        }
    }

    static final class Private implements ECPrivateKey {
        private static final long serialVersionUID = 1L;
        final EcCurve curve;
        final BigInteger s;

        Private(EcCurve curve, BigInteger s) throws InvalidKeySpecException {
            if (s.signum() <= 0 || s.compareTo(curve.n) >= 0) throw new InvalidKeySpecException("Private value out of range");
            this.curve = curve;
            this.s = s;
        }

        public BigInteger getS() {
            return s;
        }

        public ECParameterSpec getParams() {
            return curve.spec;
        }

        public String getAlgorithm() {
            return "EC";
        }

        public String getFormat() {
            return "PKCS#8";
        }

        public byte[] getEncoded() {
            int len = (curve.n.bitLength() + 7) / 8;
            byte[] sec1 = Der.seq(Der.integer(1), Der.octets(Der.unsigned(s, len)));
            return Der.seq(Der.integer(0), algId(curve), Der.octets(sec1));
        }

        public boolean equals(Object o) {
            if (!(o instanceof ECPrivateKey)) return false;
            ECPrivateKey k = (ECPrivateKey) o;
            return s.equals(k.getS()) && EcCurve.of(k.getParams()) == curve;
        }

        public int hashCode() {
            return s.hashCode() ^ curve.hashCode();
        }

        public String toString() {
            return "EC private key, " + curve.p.bitLength() + " bits\n  parameters: " + curve.spec;
        }
    }

    static EcCurve curveOf(ECParameterSpec params) throws InvalidKeyException {
        EcCurve c = EcCurve.of(params);
        if (c == null) throw new InvalidKeyException("Unsupported curve: " + params);
        return c;
    }

    static Public toPublic(Key key) throws InvalidKeyException {
        if (key instanceof Public) return (Public) key;
        if (key instanceof ECPublicKey) {
            ECPublicKey k = (ECPublicKey) key;
            EcCurve c = curveOf(k.getParams());
            c.validate(k.getW());
            return new Public(c, k.getW());
        }
        if (key instanceof PublicKey && "X.509".equals(key.getFormat()) && key.getEncoded() != null) {
            try {
                return parsePublic(key.getEncoded());
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e.getMessage(), e);
            }
        }
        throw new InvalidKeyException("Not an EC public key: " + (key == null ? null : key.getClass().getName()));
    }

    static Private toPrivate(Key key) throws InvalidKeyException {
        try {
            if (key instanceof Private) return (Private) key;
            if (key instanceof ECPrivateKey) {
                ECPrivateKey k = (ECPrivateKey) key;
                return new Private(curveOf(k.getParams()), k.getS());
            }
            if (key instanceof PrivateKey && "PKCS#8".equals(key.getFormat()) && key.getEncoded() != null) {
                return parsePrivate(key.getEncoded());
            }
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        }
        throw new InvalidKeyException("Not an EC private key: " + (key == null ? null : key.getClass().getName()));
    }

    private static EcCurve curveByOid(String oid) throws InvalidKeySpecException {
        EcCurve c = EcCurve.byName(oid);
        if (c == null) throw new InvalidKeySpecException("Unsupported curve " + oid);
        return c;
    }

    static Public parsePublic(byte[] encoded) throws InvalidKeySpecException {
        try {
            Der.Reader spki = new Der.Reader(encoded).sequence();
            Der.Reader alg = spki.sequence();
            if (!EC_OID.equals(alg.oid())) throw new InvalidKeySpecException("Not an EC key");
            EcCurve c = curveByOid(alg.oid());
            return new Public(c, c.decodePoint(spki.bits()));
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad X.509 EC key: " + e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException(e.getMessage(), e);
        }
    }

    static Private parsePrivate(byte[] encoded) throws InvalidKeySpecException {
        try {
            Der.Reader pk = new Der.Reader(encoded).sequence();
            pk.integer();
            Der.Reader alg = pk.sequence();
            if (!EC_OID.equals(alg.oid())) throw new InvalidKeySpecException("Not an EC key");
            EcCurve c = curveByOid(alg.oid());
            Der.Reader sec1 = new Der.Reader(pk.octets()).sequence();
            sec1.integer();
            BigInteger s = new BigInteger(1, sec1.octets());
            return new Private(c, s);
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad PKCS#8 EC key: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- KeyFactory "EC"

    static final class Factory extends KeyFactorySpi {
        protected PublicKey engineGeneratePublic(KeySpec spec) throws InvalidKeySpecException {
            if (spec instanceof ECPublicKeySpec) {
                ECPublicKeySpec s = (ECPublicKeySpec) spec;
                EcCurve c = EcCurve.of(s.getParams());
                if (c == null) throw new InvalidKeySpecException("Unsupported curve");
                try {
                    c.validate(s.getW());
                } catch (InvalidKeyException e) {
                    throw new InvalidKeySpecException(e.getMessage(), e);
                }
                return new Public(c, s.getW());
            }
            if (spec instanceof X509EncodedKeySpec) return parsePublic(((X509EncodedKeySpec) spec).getEncoded());
            throw new InvalidKeySpecException("Unsupported key spec: " + (spec == null ? null : spec.getClass().getName()));
        }

        protected PrivateKey engineGeneratePrivate(KeySpec spec) throws InvalidKeySpecException {
            if (spec instanceof ECPrivateKeySpec) {
                ECPrivateKeySpec s = (ECPrivateKeySpec) spec;
                EcCurve c = EcCurve.of(s.getParams());
                if (c == null) throw new InvalidKeySpecException("Unsupported curve");
                return new Private(c, s.getS());
            }
            if (spec instanceof PKCS8EncodedKeySpec) return parsePrivate(((PKCS8EncodedKeySpec) spec).getEncoded());
            throw new InvalidKeySpecException("Unsupported key spec: " + (spec == null ? null : spec.getClass().getName()));
        }

        @SuppressWarnings("unchecked")
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> spec) throws InvalidKeySpecException {
            try {
                if (key instanceof PublicKey) {
                    Public k = toPublic(key);
                    if (spec.isAssignableFrom(ECPublicKeySpec.class)) return (T) new ECPublicKeySpec(k.w, k.curve.spec);
                    if (spec.isAssignableFrom(X509EncodedKeySpec.class)) return (T) new X509EncodedKeySpec(k.getEncoded());
                } else if (key instanceof PrivateKey) {
                    Private k = toPrivate(key);
                    if (spec.isAssignableFrom(ECPrivateKeySpec.class)) return (T) new ECPrivateKeySpec(k.s, k.curve.spec);
                    if (spec.isAssignableFrom(PKCS8EncodedKeySpec.class)) return (T) new PKCS8EncodedKeySpec(k.getEncoded());
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e.getMessage(), e);
            }
            throw new InvalidKeySpecException("Unsupported key spec " + spec.getName());
        }

        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            if (key instanceof PublicKey) return toPublic(key);
            if (key instanceof PrivateKey) return toPrivate(key);
            throw new InvalidKeyException("Unsupported key type");
        }
    }

    // ---------------------------------------------------------------- KeyPairGenerator "EC"

    static BigInteger randomScalar(EcCurve c, SecureRandom rnd) {
        BigInteger k;
        do {
            k = new BigInteger(c.n.bitLength(), rnd);
        } while (k.signum() == 0 || k.compareTo(c.n) >= 0);
        return k;
    }

    static final class Generator extends KeyPairGeneratorSpi {
        private EcCurve curve = EcCurve.P256;
        private SecureRandom random;

        public void initialize(int keysize, SecureRandom random) {
            EcCurve c = EcCurve.bySize(keysize);
            if (c == null) throw new java.security.InvalidParameterException("Unsupported EC key size: " + keysize + " (256, 384 or 521)");
            this.curve = c;
            this.random = random;
        }

        public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            EcCurve c = null;
            if (params instanceof ECGenParameterSpec) c = EcCurve.byName(((ECGenParameterSpec) params).getName());
            else if (params instanceof ECParameterSpec) c = EcCurve.of((ECParameterSpec) params);
            else throw new InvalidAlgorithmParameterException("ECParameterSpec or ECGenParameterSpec required");
            if (c == null) throw new InvalidAlgorithmParameterException("Unsupported curve: " + params);
            this.curve = c;
            this.random = random;
        }

        public KeyPair generateKeyPair() {
            BigInteger s = randomScalar(curve, random != null ? random : new SecureRandom());
            try {
                return new KeyPair(new Public(curve, curve.multiply(curve.g, s)), new Private(curve, s));
            } catch (InvalidKeySpecException e) {
                throw new java.security.ProviderException(e);
            }
        }
    }

    // ---------------------------------------------------------------- AlgorithmParameters "EC"

    static final class Parameters extends AlgorithmParametersSpi {
        private EcCurve curve;

        protected void engineInit(AlgorithmParameterSpec spec) throws InvalidParameterSpecException {
            if (spec instanceof ECGenParameterSpec) curve = EcCurve.byName(((ECGenParameterSpec) spec).getName());
            else if (spec instanceof ECParameterSpec) curve = EcCurve.of((ECParameterSpec) spec);
            else throw new InvalidParameterSpecException("Only ECParameterSpec and ECGenParameterSpec supported");
            if (curve == null) throw new InvalidParameterSpecException("Unknown curve: " + spec);
        }

        protected void engineInit(byte[] params) throws IOException {
            String oid = new Der.Reader(params).oid();
            curve = EcCurve.byName(oid);
            if (curve == null) throw new IOException("Unknown named curve: " + oid);
        }

        protected void engineInit(byte[] params, String format) throws IOException {
            engineInit(params);
        }

        @SuppressWarnings("unchecked")
        protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(Class<T> spec) throws InvalidParameterSpecException {
            if (spec.isAssignableFrom(ECParameterSpec.class)) return (T) curve.spec;
            if (spec.isAssignableFrom(ECGenParameterSpec.class)) return (T) new ECGenParameterSpec(curve.oid);
            throw new InvalidParameterSpecException("Only ECParameterSpec and ECGenParameterSpec supported");
        }

        protected byte[] engineGetEncoded() {
            return Der.oid(curve.oid);
        }

        protected byte[] engineGetEncoded(String format) {
            return engineGetEncoded();
        }

        protected String engineToString() {
            return curve.spec.toString();
        }
    }
}
