package javax.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import libcore.crypto.Services;

public class KeyGenerator {
    private final KeyGeneratorSpi spi;
    private final Provider provider;
    private final String algorithm;

    protected KeyGenerator(KeyGeneratorSpi keyGenSpi, Provider provider, String algorithm) {
        this.spi = keyGenSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static final KeyGenerator getInstance(String algorithm) throws NoSuchAlgorithmException {
        Provider.Service s = Services.first("KeyGenerator", algorithm);
        return new KeyGenerator(Services.newSpi(s, KeyGeneratorSpi.class, null), s.getProvider(), algorithm);
    }

    public static final KeyGenerator getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("KeyGenerator", algorithm, provider);
        return new KeyGenerator(Services.newSpi(s, KeyGeneratorSpi.class, null), s.getProvider(), algorithm);
    }

    public static final KeyGenerator getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("KeyGenerator", algorithm, provider);
        return new KeyGenerator(Services.newSpi(s, KeyGeneratorSpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void init(SecureRandom random) {
        spi.engineInit(random);
    }

    public final void init(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
        spi.engineInit(params, new SecureRandom());
    }

    public final void init(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
        spi.engineInit(params, random);
    }

    public final void init(int keysize) {
        spi.engineInit(keysize, new SecureRandom());
    }

    public final void init(int keysize, SecureRandom random) {
        spi.engineInit(keysize, random);
    }

    public final SecretKey generateKey() {
        return spi.engineGenerateKey();
    }
}
