package libcore.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** KeyGenerator for AES and the HMAC algorithms: random bytes of the chosen size. */
final class SecretKeyGen extends KeyGeneratorSpi {
    private final String algorithm;
    private final boolean aes;
    private int bits;
    private SecureRandom random;

    SecretKeyGen(String algorithm, int defaultBits, boolean aes) {
        this.algorithm = algorithm;
        this.bits = defaultBits;
        this.aes = aes;
    }

    protected void engineInit(SecureRandom random) {
        this.random = random;
    }

    protected void engineInit(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException(algorithm + " key generation does not take any parameters");
    }

    protected void engineInit(int keysize, SecureRandom random) {
        if (aes ? (keysize != 128 && keysize != 192 && keysize != 256) : (keysize < 40 || (keysize & 7) != 0)) {
            throw new InvalidParameterException("Wrong keysize: must be " + (aes ? "equal to 128, 192 or 256" : "a multiple of 8 and at least 40"));
        }
        this.bits = keysize;
        this.random = random;
    }

    protected SecretKey engineGenerateKey() {
        byte[] k = new byte[bits / 8];
        (random != null ? random : new SecureRandom()).nextBytes(k);
        return new SecretKeySpec(k, algorithm);
    }
}
