package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * RSA/ECB with PKCS1Padding (RSAES-PKCS1-v1_5; type 1 when encrypting with a
 * private key, as the JDK does), NoPadding, and OAEP with SHA-1 or SHA-2 and
 * MGF1 (RFC 8017). "RSA" alone is RSA/ECB/PKCS1Padding.
 */
final class RsaCipher extends CipherSpi {
    private static final int PKCS1 = 0, NONE = 1, OAEP = 2;

    private int padding = PKCS1;
    private String oaepDigest = "SHA-1";
    private String mgfDigest = "SHA-1";
    private byte[] label = new byte[0];
    private boolean fixedOaepDigest;

    private boolean encrypt;
    private RSAPublicKey pub;
    private RSAPrivateKey priv;
    private SecureRandom random;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    RsaCipher() {
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (!mode.equalsIgnoreCase("ECB") && !mode.equalsIgnoreCase("NONE")) throw new NoSuchAlgorithmException("Unsupported mode " + mode);
    }

    protected void engineSetPadding(String p) throws NoSuchPaddingException {
        String u = p.toUpperCase(Locale.ENGLISH);
        if (u.equals("PKCS1PADDING")) {
            padding = PKCS1;
        } else if (u.equals("NOPADDING")) {
            padding = NONE;
        } else if (u.equals("OAEPPADDING")) {
            padding = OAEP;
        } else if (u.startsWith("OAEPWITH") && u.endsWith("ANDMGF1PADDING")) {
            String d = canonicalDigest(u.substring(8, u.length() - 14));
            if (d == null) throw new NoSuchPaddingException("Unsupported padding " + p);
            padding = OAEP;
            oaepDigest = d;
            mgfDigest = "SHA-1";
            fixedOaepDigest = true;
        } else {
            throw new NoSuchPaddingException("Unsupported padding " + p);
        }
    }

    static String canonicalDigest(String d) {
        String u = d.toUpperCase(Locale.ENGLISH).replace("-", "");
        switch (u) {
            case "MD5": return "MD5";
            case "SHA1": case "SHA": return "SHA-1";
            case "SHA224": return "SHA-224";
            case "SHA256": return "SHA-256";
            case "SHA384": return "SHA-384";
            case "SHA512": return "SHA-512";
            default: return null;
        }
    }

    protected int engineGetBlockSize() {
        return 0;
    }

    private int modulusBytes() {
        return pub != null ? RsaKeys.byteLength(pub) : priv != null ? RsaKeys.byteLength(priv) : 0;
    }

    protected int engineGetOutputSize(int inputLen) {
        return modulusBytes();
    }

    protected byte[] engineGetIV() {
        return null;
    }

    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        }
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec spec = null;
        if (params != null) {
            try {
                spec = params.getParameterSpec(OAEPParameterSpec.class);
            } catch (Exception e) {
                throw new InvalidAlgorithmParameterException("Wrong parameters for RSA", e);
            }
        }
        engineInit(opmode, key, spec, random);
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            if (padding != OAEP || !(params instanceof OAEPParameterSpec)) {
                throw new InvalidAlgorithmParameterException("Wrong parameters for " + (padding == OAEP ? "OAEP" : "RSA"));
            }
            OAEPParameterSpec o = (OAEPParameterSpec) params;
            String d = canonicalDigest(o.getDigestAlgorithm());
            if (d == null || !"MGF1".equalsIgnoreCase(o.getMGFAlgorithm()) || !(o.getMGFParameters() instanceof MGF1ParameterSpec)) {
                throw new InvalidAlgorithmParameterException("Unsupported OAEP parameters");
            }
            String m = canonicalDigest(((MGF1ParameterSpec) o.getMGFParameters()).getDigestAlgorithm());
            if (m == null) throw new InvalidAlgorithmParameterException("Unsupported MGF1 digest");
            PSource src = o.getPSource();
            oaepDigest = d;
            mgfDigest = m;
            label = src instanceof PSource.PSpecified ? ((PSource.PSpecified) src).getValue() : new byte[0];
        } else if (padding == OAEP && !fixedOaepDigest) {
            oaepDigest = "SHA-1";
            mgfDigest = "SHA-1";
            label = new byte[0];
        } else if (padding == OAEP) {
            mgfDigest = "SHA-1";
            label = new byte[0];
        }
        encrypt = opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE;
        pub = null;
        priv = null;
        if (key instanceof PublicKey) pub = RsaKeys.toPublic(key);
        else if (key instanceof PrivateKey) priv = RsaKeys.toPrivate(key);
        else throw new InvalidKeyException("Unsupported key: " + (key == null ? null : key.getClass().getName()));
        if (padding == OAEP && (encrypt ? priv != null : pub != null)) {
            throw new InvalidKeyException("OAEP cannot be used to " + (encrypt ? "sign" : "verify") + " data");
        }
        this.random = random != null ? random : new SecureRandom();
        buffer.reset();
    }

    protected byte[] engineUpdate(byte[] input, int off, int len) {
        buffer.write(input, off, len);
        return new byte[0];
    }

    protected int engineUpdate(byte[] input, int off, int len, byte[] output, int outOff) {
        buffer.write(input, off, len);
        return 0;
    }

    protected byte[] engineDoFinal(byte[] input, int off, int len) throws IllegalBlockSizeException, BadPaddingException {
        if (input != null && len > 0) buffer.write(input, off, len);
        byte[] in = buffer.toByteArray();
        buffer.reset();
        int k = modulusBytes();
        if (k == 0) throw new IllegalStateException("Cipher not initialized");
        if (encrypt) {
            byte[] em;
            if (padding == NONE) {
                if (in.length > k) throw new IllegalBlockSizeException("Data must not be longer than " + k + " bytes");
                em = new byte[k];
                System.arraycopy(in, 0, em, k - in.length, in.length);
            } else if (padding == PKCS1) {
                if (in.length > k - 11) throw new IllegalBlockSizeException("Data must not be longer than " + (k - 11) + " bytes");
                em = new byte[k];
                em[1] = (byte) (pub != null ? 2 : 1);
                int psEnd = k - in.length - 1;
                if (pub != null) {
                    byte[] one = new byte[1];
                    for (int i = 2; i < psEnd; i++) {
                        do {
                            random.nextBytes(one);
                        } while (one[0] == 0);
                        em[i] = one[0];
                    }
                } else {
                    Arrays.fill(em, 2, psEnd, (byte) 0xff);
                }
                System.arraycopy(in, 0, em, k - in.length, in.length);
            } else {
                em = oaepEncode(in, k);
            }
            return pub != null ? RsaKeys.publicOp(pub, em) : RsaKeys.privateOp(priv, em, random);
        }
        if (in.length > k) throw new IllegalBlockSizeException("Data must not be longer than " + k + " bytes");
        byte[] em = pub != null ? RsaKeys.publicOp(pub, in) : RsaKeys.privateOp(priv, in, random);
        if (padding == NONE) return em;
        if (padding == OAEP) return oaepDecode(em, k);
        // PKCS1 v1.5: 00 || type || PS || 00 || M
        int type = pub != null ? 1 : 2;
        boolean bad = em[0] != 0 || em[1] != type;
        int sep = -1;
        for (int i = 2; i < k; i++) {
            if (em[i] == 0) {
                sep = i;
                break;
            }
            if (type == 1 && em[i] != (byte) 0xff) bad = true;
        }
        if (bad || sep < 10) throw new BadPaddingException("Decryption error");
        return Arrays.copyOfRange(em, sep + 1, k);
    }

    protected int engineDoFinal(byte[] input, int off, int len, byte[] output, int outOff)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        byte[] out = engineDoFinal(input, off, len);
        if (output.length - outOff < out.length) throw new ShortBufferException("Output buffer too short");
        System.arraycopy(out, 0, output, outOff, out.length);
        return out.length;
    }

    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        if (key instanceof PublicKey) return RsaKeys.toPublic(key).getModulus().bitLength();
        return RsaKeys.toPrivate(key).getModulus().bitLength();
    }

    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
        byte[] encoded = key.getEncoded();
        if (encoded == null) throw new InvalidKeyException("Cannot get an encoding of the key to be wrapped");
        try {
            return engineDoFinal(encoded, 0, encoded.length);
        } catch (BadPaddingException e) {
            throw new InvalidKeyException("Wrapping failed", e);
        }
    }

    protected Key engineUnwrap(byte[] wrapped, String algorithm, int type) throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] encoded;
        try {
            encoded = engineDoFinal(wrapped, 0, wrapped.length);
        } catch (Exception e) {
            throw new InvalidKeyException("Unwrapping failed", e);
        }
        if (type == Cipher.SECRET_KEY) return new javax.crypto.spec.SecretKeySpec(encoded, algorithm);
        try {
            java.security.KeyFactory f = java.security.KeyFactory.getInstance(algorithm);
            if (type == Cipher.PUBLIC_KEY) return f.generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
            return f.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
        } catch (java.security.spec.InvalidKeySpecException e) {
            throw new InvalidKeyException(e);
        }
    }

    // ---------------------------------------------------------------- OAEP

    private static byte[] mgf1(String digestName, byte[] seed, int len) {
        MessageDigest md = BuiltinProvider.newDigest(digestName);
        byte[] out = new byte[len];
        byte[] c = new byte[4];
        for (int counter = 0, pos = 0; pos < len; counter++) {
            BlockDigest.putBeInt(counter, c, 0);
            md.update(seed);
            md.update(c);
            byte[] h = md.digest();
            int n = Math.min(h.length, len - pos);
            System.arraycopy(h, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    private byte[] oaepEncode(byte[] m, int k) throws IllegalBlockSizeException {
        byte[] lHash = BuiltinProvider.newDigest(oaepDigest).digest(label);
        int hLen = lHash.length;
        if (m.length > k - 2 * hLen - 2) throw new IllegalBlockSizeException("Data must not be longer than " + (k - 2 * hLen - 2) + " bytes");
        byte[] db = new byte[k - hLen - 1];
        System.arraycopy(lHash, 0, db, 0, hLen);
        db[db.length - m.length - 1] = 1;
        System.arraycopy(m, 0, db, db.length - m.length, m.length);
        byte[] seed = new byte[hLen];
        random.nextBytes(seed);
        byte[] dbMask = mgf1(mgfDigest, seed, db.length);
        for (int i = 0; i < db.length; i++) db[i] ^= dbMask[i];
        byte[] seedMask = mgf1(mgfDigest, db, hLen);
        for (int i = 0; i < hLen; i++) seed[i] ^= seedMask[i];
        byte[] em = new byte[k];
        System.arraycopy(seed, 0, em, 1, hLen);
        System.arraycopy(db, 0, em, 1 + hLen, db.length);
        return em;
    }

    private byte[] oaepDecode(byte[] em, int k) throws BadPaddingException {
        byte[] lHash = BuiltinProvider.newDigest(oaepDigest).digest(label);
        int hLen = lHash.length;
        if (k < 2 * hLen + 2) throw new BadPaddingException("Decryption error");
        byte[] seed = Arrays.copyOfRange(em, 1, 1 + hLen);
        byte[] db = Arrays.copyOfRange(em, 1 + hLen, k);
        byte[] seedMask = mgf1(mgfDigest, db, hLen);
        for (int i = 0; i < hLen; i++) seed[i] ^= seedMask[i];
        byte[] dbMask = mgf1(mgfDigest, seed, db.length);
        for (int i = 0; i < db.length; i++) db[i] ^= dbMask[i];
        boolean bad = em[0] != 0 || !MessageDigest.isEqual(Arrays.copyOf(db, hLen), lHash);
        int one = -1;
        for (int i = hLen; i < db.length; i++) {
            if (db[i] == 1) {
                one = i;
                break;
            }
            if (db[i] != 0) {
                bad = true;
                break;
            }
        }
        if (bad || one < 0) throw new BadPaddingException("Decryption error");
        return Arrays.copyOfRange(db, one + 1, db.length);
    }
}
