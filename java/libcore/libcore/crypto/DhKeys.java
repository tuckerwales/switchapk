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
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPrivateKeySpec;
import javax.crypto.spec.DHPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Finite-field Diffie-Hellman: keys with X.509 and PKCS#8 encodings
 * (dhKeyAgreement, PKCS#3 parameters), KeyFactory "DH", KeyPairGenerator "DH"
 * (any DHParameterSpec, or the RFC 2409/3526 MODP group of the requested
 * size) and KeyAgreement "DH" (secret padded to the length of p).
 */
final class DhKeys {
    static final String DH_OID = "1.2.840.113549.1.3.1";

    private static final String[] MODP = {
        // 1024-bit MODP group (RFC 2409 group 2)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF",
        // 1536-bit MODP group (RFC 3526)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF",
        // 2048-bit MODP group (RFC 3526)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
                + "15728E5A8AACAA68FFFFFFFFFFFFFFFF",
        // 3072-bit MODP group (RFC 3526)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
                + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7"
                + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C"
                + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF",
        // 4096-bit MODP group (RFC 3526)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
                + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7"
                + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C"
                + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D7"
                + "88719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6"
                + "287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA9"
                + "93B4EA988D8FDDC186FFB7DC90A6C08F4DF435C934063199FFFFFFFFFFFFFFFF",
        // 6144-bit MODP group (RFC 3526)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
                + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7"
                + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C"
                + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D7"
                + "88719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6"
                + "287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA9"
                + "93B4EA988D8FDDC186FFB7DC90A6C08F4DF435C93402849236C3FAB4D27C7026C1D4DCB2602646DEC9751E763DBA37BD"
                + "F8FF9406AD9E530EE5DB382F413001AEB06A53ED9027D831179727B0865A8918DA3EDBEBCF9B14ED44CE6CBACED4BB1B"
                + "DB7F1447E6CC254B332051512BD7AF426FB8F401378CD2BF5983CA01C64B92ECF032EA15D1721D03F482D7CE6E74FEF6"
                + "D55E702F46980C82B5A84031900B1C9E59E7C97FBEC7E8F323A97A7E36CC88BE0F1D45B7FF585AC54BD407B22B4154AA"
                + "CC8F6D7EBF48E1D814CC5ED20F8037E0A79715EEF29BE32806A1D58BB7C5DA76F550AA3D8A1FBFF0EB19CCB1A313D55C"
                + "DA56C9EC2EF29632387FE8D76E3C0468043E8F663F4860EE12BF2D5B0B7474D6E694F91E6DCC4024FFFFFFFFFFFFFFFF",
        // 8192-bit MODP group (RFC 3526)
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
                + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
                + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
                + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
                + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
                + "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7"
                + "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C"
                + "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D7"
                + "88719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6"
                + "287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA9"
                + "93B4EA988D8FDDC186FFB7DC90A6C08F4DF435C93402849236C3FAB4D27C7026C1D4DCB2602646DEC9751E763DBA37BD"
                + "F8FF9406AD9E530EE5DB382F413001AEB06A53ED9027D831179727B0865A8918DA3EDBEBCF9B14ED44CE6CBACED4BB1B"
                + "DB7F1447E6CC254B332051512BD7AF426FB8F401378CD2BF5983CA01C64B92ECF032EA15D1721D03F482D7CE6E74FEF6"
                + "D55E702F46980C82B5A84031900B1C9E59E7C97FBEC7E8F323A97A7E36CC88BE0F1D45B7FF585AC54BD407B22B4154AA"
                + "CC8F6D7EBF48E1D814CC5ED20F8037E0A79715EEF29BE32806A1D58BB7C5DA76F550AA3D8A1FBFF0EB19CCB1A313D55C"
                + "DA56C9EC2EF29632387FE8D76E3C0468043E8F663F4860EE12BF2D5B0B7474D6E694F91E6DBE115974A3926F12FEE5E4"
                + "38777CB6A932DF8CD8BEC4D073B931BA3BC832B68D9DD300741FA7BF8AFC47ED2576F6936BA424663AAB639C5AE4F568"
                + "3423B4742BF1C978238F16CBE39D652DE3FDB8BEFC848AD922222E04A4037C0713EB57A81A23F0C73473FC646CEA306B"
                + "4BCBC8862F8385DDFA9D4B7FA2C087E879683303ED5BDD3A062B3CF5B3A278A66D2A13F83F44F82DDF310EE074AB6A36"
                + "4597E899A0255DC164F31CC50846851DF9AB48195DED7EA1B1D510BD7EE74D73FAF36BC31ECFA268359046F4EB879F92"
                + "4009438B481C6CD7889A002ED5EE382BC9190DA6FC026E479558E4475677E9AA9E3050E2765694DFC81F56E880B96E71"
                + "60C980DD98EDD3DFFFFFFFFFFFFFFFFF",
    };

    private DhKeys() {
    }

    /** The well-known group of this size, or null. */
    static DHParameterSpec group(int bits) {
        for (String hex : MODP) {
            if (hex.length() * 4 == bits) return new DHParameterSpec(new BigInteger(hex, 16), BigInteger.TWO);
        }
        return null;
    }

    static byte[] algId(DHParameterSpec ps) {
        byte[] params = ps.getL() > 0 ? Der.seq(Der.integer(ps.getP()), Der.integer(ps.getG()), Der.integer(ps.getL()))
                : Der.seq(Der.integer(ps.getP()), Der.integer(ps.getG()));
        return Der.seq(Der.oid(DH_OID), params);
    }

    static DHParameterSpec readParams(Der.Reader alg) throws IOException, InvalidKeySpecException {
        if (!DH_OID.equals(alg.oid())) throw new InvalidKeySpecException("Not a DH key");
        Der.Reader ps = alg.sequence();
        BigInteger p = ps.integer(), g = ps.integer();
        int l = ps.more() ? ps.integer().intValue() : 0;
        return new DHParameterSpec(p, g, l);
    }

    static final class Public implements DHPublicKey {
        private static final long serialVersionUID = 1L;
        final BigInteger y;
        final DHParameterSpec params;

        Public(BigInteger y, DHParameterSpec params) {
            this.y = y;
            this.params = params;
        }

        public BigInteger getY() {
            return y;
        }

        public DHParameterSpec getParams() {
            return params;
        }

        public String getAlgorithm() {
            return "DH";
        }

        public String getFormat() {
            return "X.509";
        }

        public byte[] getEncoded() {
            return Der.seq(algId(params), Der.bits(Der.integer(y)));
        }

        public boolean equals(Object o) {
            return o instanceof DHPublicKey && y.equals(((DHPublicKey) o).getY())
                    && params.getP().equals(((DHPublicKey) o).getParams().getP());
        }

        public int hashCode() {
            return y.hashCode();
        }
    }

    static final class Private implements DHPrivateKey {
        private static final long serialVersionUID = 1L;
        final BigInteger x;
        final DHParameterSpec params;

        Private(BigInteger x, DHParameterSpec params) {
            this.x = x;
            this.params = params;
        }

        public BigInteger getX() {
            return x;
        }

        public DHParameterSpec getParams() {
            return params;
        }

        public String getAlgorithm() {
            return "DH";
        }

        public String getFormat() {
            return "PKCS#8";
        }

        public byte[] getEncoded() {
            return Der.seq(Der.integer(0), algId(params), Der.octets(Der.integer(x)));
        }

        public boolean equals(Object o) {
            return o instanceof DHPrivateKey && x.equals(((DHPrivateKey) o).getX())
                    && params.getP().equals(((DHPrivateKey) o).getParams().getP());
        }

        public int hashCode() {
            return x.hashCode();
        }
    }

    static Public parsePublic(byte[] enc) throws InvalidKeySpecException {
        try {
            Der.Reader spki = new Der.Reader(enc).sequence();
            DHParameterSpec ps = readParams(spki.sequence());
            BigInteger y = new Der.Reader(spki.bits()).integer();
            return new Public(y, ps);
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad X.509 DH key: " + e.getMessage(), e);
        }
    }

    static Private parsePrivate(byte[] enc) throws InvalidKeySpecException {
        try {
            Der.Reader pk = new Der.Reader(enc).sequence();
            pk.integer();
            DHParameterSpec ps = readParams(pk.sequence());
            BigInteger x = new Der.Reader(pk.octets()).integer();
            return new Private(x, ps);
        } catch (IOException e) {
            throw new InvalidKeySpecException("Bad PKCS#8 DH key: " + e.getMessage(), e);
        }
    }

    static DHPublicKey toPublic(Key key) throws InvalidKeyException {
        if (key instanceof DHPublicKey) return (DHPublicKey) key;
        if (key instanceof PublicKey && "X.509".equals(key.getFormat()) && key.getEncoded() != null) {
            try {
                return parsePublic(key.getEncoded());
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e.getMessage(), e);
            }
        }
        throw new InvalidKeyException("Not a DH public key: " + (key == null ? null : key.getClass().getName()));
    }

    static DHPrivateKey toPrivate(Key key) throws InvalidKeyException {
        if (key instanceof DHPrivateKey) return (DHPrivateKey) key;
        if (key instanceof PrivateKey && "PKCS#8".equals(key.getFormat()) && key.getEncoded() != null) {
            try {
                return parsePrivate(key.getEncoded());
            } catch (InvalidKeySpecException e) {
                throw new InvalidKeyException(e.getMessage(), e);
            }
        }
        throw new InvalidKeyException("Not a DH private key: " + (key == null ? null : key.getClass().getName()));
    }

    static final class Factory extends KeyFactorySpi {
        protected PublicKey engineGeneratePublic(KeySpec spec) throws InvalidKeySpecException {
            if (spec instanceof DHPublicKeySpec) {
                DHPublicKeySpec s = (DHPublicKeySpec) spec;
                return new Public(s.getY(), new DHParameterSpec(s.getP(), s.getG()));
            }
            if (spec instanceof X509EncodedKeySpec) return parsePublic(((X509EncodedKeySpec) spec).getEncoded());
            throw new InvalidKeySpecException("Unsupported key spec: " + (spec == null ? null : spec.getClass().getName()));
        }

        protected PrivateKey engineGeneratePrivate(KeySpec spec) throws InvalidKeySpecException {
            if (spec instanceof DHPrivateKeySpec) {
                DHPrivateKeySpec s = (DHPrivateKeySpec) spec;
                return new Private(s.getX(), new DHParameterSpec(s.getP(), s.getG()));
            }
            if (spec instanceof PKCS8EncodedKeySpec) return parsePrivate(((PKCS8EncodedKeySpec) spec).getEncoded());
            throw new InvalidKeySpecException("Unsupported key spec: " + (spec == null ? null : spec.getClass().getName()));
        }

        @SuppressWarnings("unchecked")
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> spec) throws InvalidKeySpecException {
            try {
                if (key instanceof PublicKey) {
                    DHPublicKey k = toPublic(key);
                    if (spec.isAssignableFrom(DHPublicKeySpec.class)) {
                        return (T) new DHPublicKeySpec(k.getY(), k.getParams().getP(), k.getParams().getG());
                    }
                    if (spec.isAssignableFrom(X509EncodedKeySpec.class)) return (T) new X509EncodedKeySpec(k.getEncoded());
                } else if (key instanceof PrivateKey) {
                    DHPrivateKey k = toPrivate(key);
                    if (spec.isAssignableFrom(DHPrivateKeySpec.class)) {
                        return (T) new DHPrivateKeySpec(k.getX(), k.getParams().getP(), k.getParams().getG());
                    }
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

    static final class Generator extends KeyPairGeneratorSpi {
        private DHParameterSpec params = group(2048);
        private SecureRandom random;

        public void initialize(int keysize, SecureRandom random) {
            DHParameterSpec g = group(keysize);
            if (g == null) {
                throw new java.security.InvalidParameterException("DH key size must be 1024, 1536, 2048, 3072, 4096, 6144 or 8192");
            }
            this.params = g;
            this.random = random;
        }

        public void initialize(AlgorithmParameterSpec spec, SecureRandom random) throws InvalidAlgorithmParameterException {
            if (!(spec instanceof DHParameterSpec)) throw new InvalidAlgorithmParameterException("DHParameterSpec required");
            DHParameterSpec ps = (DHParameterSpec) spec;
            if (ps.getP() == null || ps.getG() == null || ps.getP().bitLength() < 512) {
                throw new InvalidAlgorithmParameterException("DH prime must be at least 512 bits");
            }
            if (ps.getL() != 0 && (ps.getL() < 2 || ps.getL() >= ps.getP().bitLength())) {
                throw new InvalidAlgorithmParameterException("Bad private value length " + ps.getL());
            }
            this.params = ps;
            this.random = random;
        }

        public KeyPair generateKeyPair() {
            SecureRandom rnd = random != null ? random : new SecureRandom();
            BigInteger p = params.getP();
            // Private exponent: l bits when given, else twice the group's security level or so.
            int bits = params.getL() > 0 ? params.getL() : Math.min(p.bitLength() - 1, Math.max(384, p.bitLength() / 4));
            BigInteger x;
            do {
                x = new BigInteger(bits, rnd);
                if (params.getL() > 0) x = x.setBit(bits - 1);
            } while (x.compareTo(BigInteger.ONE) <= 0 || x.compareTo(p.subtract(BigInteger.TWO)) > 0);
            BigInteger y = params.getG().modPow(x, p);
            return new KeyPair(new Public(y, params), new Private(x, params));
        }
    }

    static final class Agreement extends KeyAgreementSpi {
        private DHPrivateKey priv;
        private byte[] secret;

        protected void engineInit(Key key, SecureRandom random) throws InvalidKeyException {
            if (!(key instanceof PrivateKey)) throw new InvalidKeyException("DH needs a DH private key");
            priv = toPrivate(key);
            secret = null;
        }

        protected void engineInit(Key key, AlgorithmParameterSpec params, SecureRandom random)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (params != null && !(params instanceof DHParameterSpec)) throw new InvalidAlgorithmParameterException("DHParameterSpec expected");
            engineInit(key, random);
            if (params != null && !((DHParameterSpec) params).getP().equals(priv.getParams().getP())) {
                throw new InvalidAlgorithmParameterException("Parameters do not match the key");
            }
        }

        protected Key engineDoPhase(Key key, boolean lastPhase) throws InvalidKeyException {
            if (priv == null) throw new IllegalStateException("Not initialized");
            if (!lastPhase) throw new IllegalStateException("Only two party agreement supported, lastPhase must be true");
            DHPublicKey pub = toPublic(key);
            BigInteger p = priv.getParams().getP();
            if (!pub.getParams().getP().equals(p) || !pub.getParams().getG().equals(priv.getParams().getG())) {
                throw new InvalidKeyException("Incompatible parameters");
            }
            BigInteger y = pub.getY();
            if (y.compareTo(BigInteger.ONE) <= 0 || y.compareTo(p.subtract(BigInteger.ONE)) >= 0) {
                throw new InvalidKeyException("Public value out of range");
            }
            secret = Der.unsigned(y.modPow(priv.getX(), p), (p.bitLength() + 7) / 8);
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
            if (algorithm == null) throw new java.security.NoSuchAlgorithmException("null algorithm");
            byte[] s = engineGenerateSecret();
            if (algorithm.equalsIgnoreCase("TlsPremasterSecret")) return new SecretKeySpec(s, "TlsPremasterSecret");
            if (algorithm.equalsIgnoreCase("AES")) return new SecretKeySpec(s, 0, Math.min(32, s.length), "AES");
            throw new java.security.NoSuchAlgorithmException("Unsupported secret key algorithm: " + algorithm);
        }
    }
}
