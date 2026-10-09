package android.security.keystore;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

/** KeyGenerator.getInstance("AES" or "HmacSHA…", "AndroidKeyStore"): needs a KeyGenParameterSpec. */
public class AndroidKeyStoreKeyGeneratorSpi extends KeyGeneratorSpi {
    private final String algorithm;
    private final String hmacDigest;
    private final int defaultKeySize;
    private KeyGenParameterSpec spec;
    private SecureRandom random;
    private int keySize;

    AndroidKeyStoreKeyGeneratorSpi(String algorithm, String hmacDigest, int defaultKeySize) {
        this.algorithm = algorithm;
        this.hmacDigest = hmacDigest;
        this.defaultKeySize = defaultKeySize;
    }

    protected void engineInit(SecureRandom random) {
        throw new UnsupportedOperationException("Cannot initialize without a " + KeyGenParameterSpec.class.getName() + " parameter");
    }

    protected void engineInit(int keySize, SecureRandom random) {
        throw new UnsupportedOperationException("Cannot initialize without a " + KeyGenParameterSpec.class.getName() + " parameter");
    }

    protected void engineInit(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
        if (!(params instanceof KeyGenParameterSpec)) {
            throw new InvalidAlgorithmParameterException("Cannot initialize without a " + KeyGenParameterSpec.class.getName() + " parameter");
        }
        KeyGenParameterSpec s = (KeyGenParameterSpec) params;
        if (s.getKeystoreAlias() == null) throw new InvalidAlgorithmParameterException("KeyStore entry alias not provided");
        int size = s.getKeySize() == -1 ? defaultKeySize : s.getKeySize();
        if (algorithm.equals("AES")) {
            if (size != 128 && size != 192 && size != 256) {
                throw new InvalidAlgorithmParameterException("Unsupported key size: " + size + ". Supported: 128, 192, 256.");
            }
        } else {
            if (size < 64 || size > 512 || (size & 7) != 0) {
                throw new InvalidAlgorithmParameterException("HMAC key sizes must be multiples of 8 in the range 64 to 512 bits");
            }
            if (s.isDigestsSpecified()) {
                String[] d = s.getDigests();
                if (d.length != 1 || !d[0].equalsIgnoreCase(hmacDigest)) {
                    throw new InvalidAlgorithmParameterException("Unsupported digests specification: " + Arrays.asList(d)
                            + ". Only " + hmacDigest + " supported for this HMAC key algorithm");
                }
            }
        }
        if ((s.getPurposes() & ~(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_SIGN
                | KeyProperties.PURPOSE_VERIFY | KeyProperties.PURPOSE_WRAP_KEY)) != 0) {
            throw new InvalidAlgorithmParameterException("Unsupported purposes for " + algorithm + ": " + s.getPurposes());
        }
        this.spec = s;
        this.random = random;
        this.keySize = size;
    }

    protected SecretKey engineGenerateKey() {
        KeyGenParameterSpec s = spec;
        if (s == null) throw new IllegalStateException("Not initialized");
        KeyStoreEntry e = new KeyStoreEntry();
        e.alias = s.getKeystoreAlias();
        e.algorithm = algorithm;
        e.keySize = keySize;
        e.origin = KeyProperties.ORIGIN_GENERATED;
        e.created = System.currentTimeMillis();
        e.purposes = s.getPurposes();
        e.blockModes = s.getBlockModes();
        e.encryptionPaddings = s.getEncryptionPaddings();
        e.signaturePaddings = s.getSignaturePaddings();
        e.digests = hmacDigest != null ? new String[] {hmacDigest} : (s.isDigestsSpecified() ? s.getDigests() : new String[0]);
        e.randomizedEncryptionRequired = s.isRandomizedEncryptionRequired();
        e.userAuthenticationRequired = s.isUserAuthenticationRequired();
        e.userAuthenticationValidityDurationSeconds = s.getUserAuthenticationValidityDurationSeconds();
        e.userAuthenticationType = s.getUserAuthenticationType();
        e.maxUsageCount = s.getMaxUsageCount();
        e.validityStart = KeyStoreEntry.time(s.getKeyValidityStart());
        e.originationEnd = KeyStoreEntry.time(s.getKeyValidityForOriginationEnd());
        e.consumptionEnd = KeyStoreEntry.time(s.getKeyValidityForConsumptionEnd());
        e.key = new byte[keySize / 8];
        (random != null ? random : new SecureRandom()).nextBytes(e.key);
        try {
            e.save();
        } catch (IOException ex) {
            throw new ProviderException("Keystore key generation failed", ex);
        }
        return new AndroidKeyStoreSecretKey(e);
    }
}
