package java.security;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import libcore.crypto.Services;

public class KeyFactory {
    private final KeyFactorySpi spi;
    private final Provider provider;
    private final String algorithm;

    protected KeyFactory(KeyFactorySpi keyFacSpi, Provider provider, String algorithm) {
        this.spi = keyFacSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public static KeyFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        Provider.Service s = Services.first("KeyFactory", algorithm);
        return new KeyFactory(Services.newSpi(s, KeyFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public static KeyFactory getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("KeyFactory", algorithm, provider);
        return new KeyFactory(Services.newSpi(s, KeyFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public static KeyFactory getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("KeyFactory", algorithm, provider);
        return new KeyFactory(Services.newSpi(s, KeyFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public final PublicKey generatePublic(KeySpec keySpec) throws InvalidKeySpecException {
        return spi.engineGeneratePublic(keySpec);
    }

    public final PrivateKey generatePrivate(KeySpec keySpec) throws InvalidKeySpecException {
        return spi.engineGeneratePrivate(keySpec);
    }

    public final <T extends KeySpec> T getKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
        return spi.engineGetKeySpec(key, keySpec);
    }

    public final Key translateKey(Key key) throws InvalidKeyException {
        return spi.engineTranslateKey(key);
    }
}
