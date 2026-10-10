package libcore.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 (RFC 8018) with an HMAC PRF. The password is encoded as UTF-8, as by
 * the JDK and Android; the "And8bit" variant keeps the low 8 bits of each char.
 */
final class Pbkdf2 extends SecretKeyFactorySpi {
    private final String algorithm;
    private final Provider prf;
    private final boolean eightBit;

    interface Provider {
        MessageDigest digest();

        int blockSize();
    }

    Pbkdf2(String algorithm, Provider prf, boolean eightBit) {
        this.algorithm = algorithm;
        this.prf = prf;
        this.eightBit = eightBit;
    }

    protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
        if (!(keySpec instanceof PBEKeySpec)) throw new InvalidKeySpecException("Invalid key spec");
        PBEKeySpec spec = (PBEKeySpec) keySpec;
        char[] password = spec.getPassword();
        byte[] salt = spec.getSalt();
        int iterations = spec.getIterationCount();
        int keyLength = spec.getKeyLength();
        if (salt == null) throw new InvalidKeySpecException("Salt not found");
        if (iterations <= 0) throw new InvalidKeySpecException("Iteration count not found");
        if (keyLength <= 0) throw new InvalidKeySpecException("Key length not found");
        if ((keyLength & 7) != 0) throw new InvalidKeySpecException("Key length must be a multiple of 8");
        byte[] pw = encode(password);
        try {
            byte[] key = derive(pw, salt, iterations, keyLength / 8);
            return new Pbkdf2Key(algorithm, password, salt, iterations, key);
        } finally {
            Arrays.fill(pw, (byte) 0);
            Arrays.fill(password, ' ');
        }
    }

    private byte[] encode(char[] password) {
        if (eightBit) {
            byte[] b = new byte[password.length];
            for (int i = 0; i < b.length; i++) b[i] = (byte) password[i];
            return b;
        }
        ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] b = new byte[bb.remaining()];
        bb.get(b);
        return b;
    }

    byte[] derive(byte[] password, byte[] salt, int iterations, int dkLen) {
        Hmac mac = new Hmac(prf.digest(), prf.blockSize());
        mac.initRaw(password.clone());
        int hLen = mac.engineGetMacLength();
        byte[] out = new byte[dkLen];
        byte[] counter = new byte[4];
        for (int block = 1, pos = 0; pos < dkLen; block++) {
            BlockDigest.putBeInt(block, counter, 0);
            mac.engineUpdate(salt, 0, salt.length);
            mac.engineUpdate(counter, 0, 4);
            byte[] u = mac.engineDoFinal();
            byte[] t = u.clone();
            for (int i = 1; i < iterations; i++) {
                mac.engineUpdate(u, 0, u.length);
                u = mac.engineDoFinal();
                for (int j = 0; j < hLen; j++) t[j] ^= u[j];
            }
            int n = Math.min(hLen, dkLen - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
        if (key instanceof PBEKey && keySpec != null && keySpec.isAssignableFrom(PBEKeySpec.class)) {
            PBEKey k = (PBEKey) key;
            return new PBEKeySpec(k.getPassword(), k.getSalt(), k.getIterationCount(), k.getEncoded().length * 8);
        }
        throw new InvalidKeySpecException("Invalid key spec");
    }

    protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
        if (key != null && key.getAlgorithm().equalsIgnoreCase(algorithm) && "RAW".equalsIgnoreCase(key.getFormat())) {
            return key;
        }
        throw new InvalidKeyException("Invalid key format/algorithm");
    }

    static final class Pbkdf2Key implements PBEKey {
        private final String algorithm;
        private char[] password;
        private final byte[] salt;
        private final int iterations;
        private byte[] key;

        Pbkdf2Key(String algorithm, char[] password, byte[] salt, int iterations, byte[] key) {
            this.algorithm = algorithm;
            this.password = password.clone();
            this.salt = salt.clone();
            this.iterations = iterations;
            this.key = key;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getFormat() {
            return "RAW";
        }

        public byte[] getEncoded() {
            if (key == null) throw new IllegalStateException("key has been destroyed");
            return key.clone();
        }

        public char[] getPassword() {
            if (password == null) throw new IllegalStateException("password has been cleared");
            return password.clone();
        }

        public byte[] getSalt() {
            return salt.clone();
        }

        public int getIterationCount() {
            return iterations;
        }

        public void destroy() {
            if (key != null) Arrays.fill(key, (byte) 0);
            if (password != null) Arrays.fill(password, ' ');
            key = null;
            password = null;
        }

        public boolean isDestroyed() {
            return key == null;
        }

        public int hashCode() {
            return Arrays.hashCode(key) ^ algorithm.toLowerCase(java.util.Locale.ENGLISH).hashCode();
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof SecretKey)) return false;
            SecretKey k = (SecretKey) o;
            return k.getAlgorithm().equalsIgnoreCase(algorithm) && "RAW".equalsIgnoreCase(k.getFormat())
                    && MessageDigest.isEqual(key, k.getEncoded());
        }
    }
}
