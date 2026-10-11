package javax.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import libcore.crypto.Services;

public class SecretKeyFactory {
    private final SecretKeyFactorySpi spi;
    private final Provider provider;
    private final String algorithm;

    protected SecretKeyFactory(SecretKeyFactorySpi keyFacSpi, Provider provider, String algorithm) {
        this.spi = keyFacSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public static final SecretKeyFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        Provider.Service s = Services.first("SecretKeyFactory", algorithm);
        return new SecretKeyFactory(Services.newSpi(s, SecretKeyFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public static final SecretKeyFactory getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("SecretKeyFactory", algorithm, provider);
        return new SecretKeyFactory(Services.newSpi(s, SecretKeyFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public static final SecretKeyFactory getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("SecretKeyFactory", algorithm, provider);
        return new SecretKeyFactory(Services.newSpi(s, SecretKeyFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public final SecretKey generateSecret(KeySpec keySpec) throws InvalidKeySpecException {
        return spi.engineGenerateSecret(keySpec);
    }

    public final KeySpec getKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
        return spi.engineGetKeySpec(key, keySpec);
    }

    public final SecretKey translateKey(SecretKey key) throws InvalidKeyException {
        return spi.engineTranslateKey(key);
    }
}
