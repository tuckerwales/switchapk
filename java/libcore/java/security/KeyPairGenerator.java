package java.security;

import java.security.spec.AlgorithmParameterSpec;
import libcore.crypto.Services;

public abstract class KeyPairGenerator extends KeyPairGeneratorSpi {
    private final String algorithm;
    Provider provider;

    protected KeyPairGenerator(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public static KeyPairGenerator getInstance(String algorithm) throws NoSuchAlgorithmException {
        return wrap(Services.first("KeyPairGenerator", algorithm), algorithm);
    }

    public static KeyPairGenerator getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return wrap(Services.in("KeyPairGenerator", algorithm, provider), algorithm);
    }

    public static KeyPairGenerator getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        return wrap(Services.in("KeyPairGenerator", algorithm, provider), algorithm);
    }

    private static KeyPairGenerator wrap(Provider.Service s, String algorithm) throws NoSuchAlgorithmException {
        KeyPairGeneratorSpi spi = Services.newSpi(s, KeyPairGeneratorSpi.class, null);
        KeyPairGenerator g = spi instanceof KeyPairGenerator ? (KeyPairGenerator) spi : new Delegate(spi, algorithm);
        g.provider = s.getProvider();
        return g;
    }

    public final Provider getProvider() {
        return provider;
    }

    public void initialize(int keysize) {
        initialize(keysize, new SecureRandom());
    }

    public void initialize(int keysize, SecureRandom random) {
    }

    public void initialize(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
        initialize(params, new SecureRandom());
    }

    public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
    }

    public final KeyPair genKeyPair() {
        return generateKeyPair();
    }

    public KeyPair generateKeyPair() {
        return null;
    }

    static final class Delegate extends KeyPairGenerator {
        private final KeyPairGeneratorSpi spi;

        Delegate(KeyPairGeneratorSpi spi, String algorithm) {
            super(algorithm);
            this.spi = spi;
        }

        public void initialize(int keysize, SecureRandom random) {
            spi.initialize(keysize, random);
        }

        public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            spi.initialize(params, random);
        }

        public KeyPair generateKeyPair() {
            return spi.generateKeyPair();
        }
    }
}
