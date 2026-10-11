package libcore.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactorySpi;
import java.security.KeyPair;
import java.security.KeyPairGeneratorSpi;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * RSA keys (X.509 SubjectPublicKeyInfo and PKCS#8 encodings, PKCS#1 inside),
 * KeyFactory "RSA" and KeyPairGenerator "RSA", and the raw RSA operations the
 * signature and cipher code share. The private operation uses CRT when the
 * key has it, with base blinding.
 */
final class RsaKeys {
    static final String RSA_OID = "1.2.840.113549.1.1.1";
    private static final byte[] ALG_ID = Der.seq(Der.oid(RSA_OID), Der.nul());

    private RsaKeys() {
    }

    static final class Public implements RSAPublicKey {
        private static final long serialVersionUID = 1L;
        final BigInteger n, e;

        Public(BigInteger n, BigInteger e) throws InvalidKeySpecException {
            if (n == null || e == null || n.signum() <= 0 || e.signum() <= 0) throw new InvalidKeySpecException("Invalid RSA public key");
            this.n = n;
            this.e = e;
        }

        public BigInteger getModulus() {
            return n;
        }

        public BigInteger getPublicExponent() {
            return e;
        }

        public String getAlgorithm() {
            return "RSA";
        }

        public String getFormat() {
            return "X.509";
        }

        public byte[] getEncoded() {
            return Der.seq(ALG_ID, Der.bits(Der.seq(Der.integer(n), Der.integer(e))));
        }

        public boolean equals(Object o) {
            return o instanceof RSAPublicKey && n.equals(((RSAPublicKey) o).getModulus())
                    && e.equals(((RSAPublicKey) o).getPublicExponent());
        }

        public int hashCode() {
            return n.hashCode() ^ e.hashCode();
        }

        public String toString() {
            return "RSA public key, " + n.bitLength() + " bits\n  modulus: " + n + "\n  public exponent: " + e;
        }
    }

    static class Private implements RSAPrivateKey {
        private static final long serialVersionUID = 1L;
        final BigInteger n, d;

        Private(BigInteger n, BigInteger d) throws InvalidKeySpecException {
            if (n == null || d == null || n.signum() <= 0 || d.signum() <= 0) throw new InvalidKeySpecException("Invalid RSA private key");
            this.n = n;
            this.d = d;
        }

        public BigInteger getModulus() {
            return n;
        }

        public BigInteger getPrivateExponent() {
            return d;
        }

        public String getAlgorithm() {
            return "RSA";
        }

        public String getFormat() {
            return "PKCS#8";
        }

        public byte[] getEncoded() {
            // RSAPrivateKey with the CRT fields set to zero, as the JDK writes a non-CRT key.
            BigInteger z = BigInteger.ZERO;
            byte[] pkcs1 = Der.seq(Der.integer(0), Der.integer(n), Der.integer(z), Der.integer(d), Der.integer(z),
                    Der.integer(z), Der.integer(z), Der.integer(z), Der.integer(z));
            return Der.seq(Der.integer(0), ALG_ID, Der.octets(pkcs1));
        }

        public boolean equals(Object o) {
            return o instanceof RSAPrivateKey && n.equals(((RSAPrivateKey) o).getModulus())
                    && d.equals(((RSAPrivateKey) o).getPrivateExponent());
        }

        public int hashCode() {
            return n.hashCode() ^ d.hashCode();
        }

        public String toString() {
            return "RSA private key, " + n.bitLength() + " bits";
        }
    }

    static final class PrivateCrt extends Private implements RSAPrivateCrtKey {
        private static final long serialVersionUID = 1L;
        final BigInteger e, p, q, dp, dq, qinv;

        PrivateCrt(BigInteger n, BigInteger e, BigInteger d, BigInteger p, BigInteger q, BigInteger dp, BigInteger dq,
                BigInteger qinv) throws InvalidKeySpecException {
            super(n, d);
            if (e == null || p == null || q == null || dp == null || dq == null || qinv == null) {
                throw new InvalidKeySpecException("Invalid RSA CRT key");
            }
            this.e = e;
            this.p = p;
            this.q = q;
            this.dp = dp;
            this.dq = dq;
            this.qinv = qinv;
        }

        public BigInteger getPublicExponent() {
            return e;
        }

        public BigInteger getPrimeP() {
            return p;
        }

        public BigInteger getPrimeQ() {
            return q;
        }

        public BigInteger getPrimeExponentP() {
            return dp;
        }

        public BigInteger getPrimeExponentQ() {
            return dq;
        }

        public BigInteger getCrtCoefficient() {
            return qinv;
        }

        public byte[] getEncoded() {
            byte[] pkcs1 = Der.seq(Der.integer(0), Der.integer(n), Der.integer(e), Der.integer(d), Der.integer(p),
                    Der.integer(q), Der.integer(dp), Der.integer(dq), Der.integer(qinv));
            return Der.seq(Der.integer(0), ALG_ID, Der.octets(pkcs1));
        }
    }

    static RSAPublicKey toPublic(Key key) throws InvalidKeyException {
        if (key instanceof RSAPublicKey) return (RSAPublicKey) key;
        if (key instanceof PublicKey && "X.509".equals(key.getFormat()) && key.getEncoded() != null) {
            try {
                return parsePublic(key.getEncoded());
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e.getMessage(), e);
            }
        }
        throw new InvalidKeyException("Not an RSA public key: " + (key == null ? null : key.getClass().getName()));
    }

    static RSAPrivateKey toPrivate(Key key) throws InvalidKeyException {
        if (key instanceof RSAPrivateKey) return (RSAPrivateKey) key;
        if (key instanceof PrivateKey && "PKCS#8".equals(key.getFormat()) && key.getEncoded() != null) {
            try {
                return parsePrivate(key.getEncoded());
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e.getMessage(), e);
            }
        }
        throw new InvalidKeyException("Not an RSA private key: " + (key == null ? null : key.getClass().getName()));
    }

    static RSAPublicKey parsePublic(byte[] encoded) throws InvalidKeySpecException {
        try {
            Der.Reader spki = new Der.Reader(encoded).sequence();
            Der.Reader alg = spki.sequence();
            if (!RSA_OID.equals(alg.oid())) throw new InvalidKeySpecException("Not an RSA key");
            byte[] key = spki.bits();
            Der.Reader r = new Der.Reader(key).sequence();
            BigInteger n = r.integer(), e = r.integer();
            r.end();
            return new Public(n, e);
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad X.509 RSA key: " + e.getMessage(), e);
        }
    }

    static RSAPrivateKey parsePrivate(byte[] encoded) throws InvalidKeySpecException {
        try {
            Der.Reader pk = new Der.Reader(encoded).sequence();
            pk.integer();
            Der.Reader alg = pk.sequence();
            if (!RSA_OID.equals(alg.oid())) throw new InvalidKeySpecException("Not an RSA key");
            return parsePkcs1Private(pk.octets());
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad PKCS#8 RSA key: " + e.getMessage(), e);
        }
    }

    static RSAPrivateKey parsePkcs1Private(byte[] pkcs1) throws InvalidKeySpecException {
        try {
            Der.Reader r = new Der.Reader(pkcs1).sequence();
            r.integer();
            BigInteger n = r.integer(), e = r.integer(), d = r.integer(), p = r.integer(), q = r.integer(),
                    dp = r.integer(), dq = r.integer(), qinv = r.integer();
            if (p.signum() == 0) return new Private(n, d);
            return new PrivateCrt(n, e, d, p, q, dp, dq, qinv);
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad PKCS#1 RSA key: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- raw operations

    static int byteLength(RSAKey k) {
        return (k.getModulus().bitLength() + 7) / 8;
    }

    static byte[] publicOp(RSAPublicKey k, byte[] in) throws javax.crypto.BadPaddingException {
        BigInteger m = new BigInteger(1, in);
        if (m.compareTo(k.getModulus()) >= 0) throw new javax.crypto.BadPaddingException("Message is larger than modulus");
        return Der.unsigned(m.modPow(k.getPublicExponent(), k.getModulus()), byteLength(k));
    }

    static byte[] privateOp(RSAPrivateKey k, byte[] in, SecureRandom random) throws javax.crypto.BadPaddingException {
        BigInteger n = k.getModulus();
        BigInteger c = new BigInteger(1, in);
        if (c.compareTo(n) >= 0) throw new javax.crypto.BadPaddingException("Message is larger than modulus");
        BigInteger m;
        if (k instanceof RSAPrivateCrtKey && ((RSAPrivateCrtKey) k).getPrimeP().signum() > 0) {
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) k;
            BigInteger e = crt.getPublicExponent();
            // Blinding: c' = c * r^e, m = m' / r.
            BigInteger r;
            do {
                r = new BigInteger(n.bitLength() - 1, random != null ? random : new SecureRandom());
            } while (r.signum() == 0 || !r.gcd(n).equals(BigInteger.ONE));
            BigInteger blinded = c.multiply(r.modPow(e, n)).mod(n);
            BigInteger p = crt.getPrimeP(), q = crt.getPrimeQ();
            BigInteger m1 = blinded.mod(p).modPow(crt.getPrimeExponentP(), p);
            BigInteger m2 = blinded.mod(q).modPow(crt.getPrimeExponentQ(), q);
            BigInteger h = m1.subtract(m2).multiply(crt.getCrtCoefficient()).mod(p);
            m = m2.add(h.multiply(q)).multiply(r.modInverse(n)).mod(n);
            // Check the result against the public operation (fault attacks, bad CRT values).
            if (!m.modPow(e, n).equals(c)) m = c.modPow(k.getPrivateExponent(), n);
        } else {
            m = c.modPow(k.getPrivateExponent(), n);
        }
        return Der.unsigned(m, byteLength(k));
    }

    // ---------------------------------------------------------------- KeyFactory "RSA"

    static final class Factory extends KeyFactorySpi {
        protected PublicKey engineGeneratePublic(KeySpec spec) throws InvalidKeySpecException {
            if (spec instanceof RSAPublicKeySpec) {
                RSAPublicKeySpec s = (RSAPublicKeySpec) spec;
                return new Public(s.getModulus(), s.getPublicExponent());
            }
            if (spec instanceof X509EncodedKeySpec) return (PublicKey) parsePublic(((X509EncodedKeySpec) spec).getEncoded());
            throw new InvalidKeySpecException("Unsupported key spec: " + (spec == null ? null : spec.getClass().getName()));
        }

        protected PrivateKey engineGeneratePrivate(KeySpec spec) throws InvalidKeySpecException {
            if (spec instanceof RSAPrivateCrtKeySpec) {
                RSAPrivateCrtKeySpec s = (RSAPrivateCrtKeySpec) spec;
                return new PrivateCrt(s.getModulus(), s.getPublicExponent(), s.getPrivateExponent(), s.getPrimeP(),
                        s.getPrimeQ(), s.getPrimeExponentP(), s.getPrimeExponentQ(), s.getCrtCoefficient());
            }
            if (spec instanceof RSAPrivateKeySpec) {
                RSAPrivateKeySpec s = (RSAPrivateKeySpec) spec;
                return new Private(s.getModulus(), s.getPrivateExponent());
            }
            if (spec instanceof PKCS8EncodedKeySpec) return (PrivateKey) parsePrivate(((PKCS8EncodedKeySpec) spec).getEncoded());
            throw new InvalidKeySpecException("Unsupported key spec: " + (spec == null ? null : spec.getClass().getName()));
        }

        @SuppressWarnings("unchecked")
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> spec) throws InvalidKeySpecException {
            try {
                if (key instanceof PublicKey) {
                    RSAPublicKey k = toPublic(key);
                    if (spec.isAssignableFrom(RSAPublicKeySpec.class)) return (T) new RSAPublicKeySpec(k.getModulus(), k.getPublicExponent());
                    if (spec.isAssignableFrom(X509EncodedKeySpec.class)) return (T) new X509EncodedKeySpec(k.getEncoded());
                } else if (key instanceof PrivateKey) {
                    RSAPrivateKey k = toPrivate(key);
                    if (k instanceof RSAPrivateCrtKey && spec.isAssignableFrom(RSAPrivateCrtKeySpec.class)) {
                        RSAPrivateCrtKey c = (RSAPrivateCrtKey) k;
                        return (T) new RSAPrivateCrtKeySpec(c.getModulus(), c.getPublicExponent(), c.getPrivateExponent(),
                                c.getPrimeP(), c.getPrimeQ(), c.getPrimeExponentP(), c.getPrimeExponentQ(), c.getCrtCoefficient());
                    }
                    if (spec.isAssignableFrom(RSAPrivateKeySpec.class)) return (T) new RSAPrivateKeySpec(k.getModulus(), k.getPrivateExponent());
                    if (spec.isAssignableFrom(PKCS8EncodedKeySpec.class)) return (T) new PKCS8EncodedKeySpec(k.getEncoded());
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e.getMessage(), e);
            }
            throw new InvalidKeySpecException("Unsupported key spec " + spec.getName() + " for " + key.getClass().getName());
        }

        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            if (key instanceof Public || key instanceof Private) return key;
            try {
                if (key instanceof RSAPublicKey) {
                    RSAPublicKey k = (RSAPublicKey) key;
                    return new Public(k.getModulus(), k.getPublicExponent());
                }
                if (key instanceof RSAPrivateCrtKey) {
                    RSAPrivateCrtKey c = (RSAPrivateCrtKey) key;
                    return new PrivateCrt(c.getModulus(), c.getPublicExponent(), c.getPrivateExponent(), c.getPrimeP(),
                            c.getPrimeQ(), c.getPrimeExponentP(), c.getPrimeExponentQ(), c.getCrtCoefficient());
                }
                if (key instanceof RSAPrivateKey) {
                    RSAPrivateKey k = (RSAPrivateKey) key;
                    return new Private(k.getModulus(), k.getPrivateExponent());
                }
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e.getMessage(), e);
            }
            if (key instanceof PublicKey) return (Key) toPublic(key);
            if (key instanceof PrivateKey) return (Key) toPrivate(key);
            throw new InvalidKeyException("Unsupported key type");
        }
    }

    // ---------------------------------------------------------------- KeyPairGenerator "RSA"

    static final class Generator extends KeyPairGeneratorSpi {
        private int bits = 2048;
        private BigInteger e = RSAKeyGenParameterSpec.F4;
        private SecureRandom random;

        public void initialize(int keysize, SecureRandom random) {
            if (keysize < 512 || keysize > 16384) throw new java.security.InvalidParameterException("RSA keys must be 512 to 16384 bits long");
            this.bits = keysize;
            this.random = random;
        }

        public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            if (!(params instanceof RSAKeyGenParameterSpec)) throw new InvalidAlgorithmParameterException("Params must be RSAKeyGenParameterSpec");
            RSAKeyGenParameterSpec s = (RSAKeyGenParameterSpec) params;
            if (s.getKeysize() < 512 || s.getKeysize() > 16384) throw new InvalidAlgorithmParameterException("RSA keys must be 512 to 16384 bits long");
            BigInteger pe = s.getPublicExponent() != null ? s.getPublicExponent() : RSAKeyGenParameterSpec.F4;
            if (pe.compareTo(BigInteger.valueOf(3)) < 0 || !pe.testBit(0)) throw new InvalidAlgorithmParameterException("Public exponent must be odd and at least 3");
            this.bits = s.getKeysize();
            this.e = pe;
            this.random = random;
        }

        public KeyPair generateKeyPair() {
            SecureRandom rnd = random != null ? random : new SecureRandom();
            int pBits = (bits + 1) / 2, qBits = bits - pBits;
            while (true) {
                BigInteger p = prime(pBits, rnd), q = prime(qBits, rnd);
                if (p.equals(q)) continue;
                if (p.compareTo(q) < 0) {
                    BigInteger t = p;
                    p = q;
                    q = t;
                }
                BigInteger n = p.multiply(q);
                if (n.bitLength() != bits) continue;
                BigInteger p1 = p.subtract(BigInteger.ONE), q1 = q.subtract(BigInteger.ONE);
                BigInteger phi = p1.multiply(q1);
                if (!e.gcd(phi).equals(BigInteger.ONE)) continue;
                BigInteger lambda = phi.divide(p1.gcd(q1));
                BigInteger d = e.modInverse(lambda);
                try {
                    return new KeyPair(new Public(n, e),
                            new PrivateCrt(n, e, d, p, q, d.mod(p1), d.mod(q1), q.modInverse(p)));
                } catch (InvalidKeySpecException ex) {
                    throw new java.security.ProviderException(ex);
                }
            }
        }

        /** A prime with the top two bits set, so p * q has exactly the requested size. */
        private static BigInteger prime(int bits, SecureRandom rnd) {
            while (true) {
                BigInteger c = new BigInteger(bits, rnd).setBit(bits - 1).setBit(bits - 2).setBit(0);
                if (c.isProbablePrime(64)) return c;
            }
        }
    }

    static byte[] copy(byte[] b, int off, int len) {
        return Arrays.copyOfRange(b, off, off + len);
    }
}
