package javax.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import libcore.crypto.Services;

public class KeyAgreement {
    private final KeyAgreementSpi spi;
    private final Provider provider;
    private final String algorithm;

    protected KeyAgreement(KeyAgreementSpi keyAgreeSpi, Provider provider, String algorithm) {
        this.spi = keyAgreeSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static final KeyAgreement getInstance(String algorithm) throws NoSuchAlgorithmException {
        Provider.Service s = Services.first("KeyAgreement", algorithm);
        return new KeyAgreement(Services.newSpi(s, KeyAgreementSpi.class, null), s.getProvider(), algorithm);
    }

    public static final KeyAgreement getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("KeyAgreement", algorithm, provider);
        return new KeyAgreement(Services.newSpi(s, KeyAgreementSpi.class, null), s.getProvider(), algorithm);
    }

    public static final KeyAgreement getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("KeyAgreement", algorithm, provider);
        return new KeyAgreement(Services.newSpi(s, KeyAgreementSpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void init(Key key) throws InvalidKeyException {
        init(key, new SecureRandom());
    }

    public final void init(Key key, SecureRandom random) throws InvalidKeyException {
        spi.engineInit(key, random);
    }

    public final void init(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        init(key, params, new SecureRandom());
    }

    public final void init(Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        spi.engineInit(key, params, random);
    }

    public final Key doPhase(Key key, boolean lastPhase) throws InvalidKeyException, IllegalStateException {
        return spi.engineDoPhase(key, lastPhase);
    }

    public final byte[] generateSecret() throws IllegalStateException {
        return spi.engineGenerateSecret();
    }

    public final int generateSecret(byte[] sharedSecret, int offset) throws IllegalStateException, ShortBufferException {
        return spi.engineGenerateSecret(sharedSecret, offset);
    }

    public final SecretKey generateSecret(String algorithm)
            throws IllegalStateException, NoSuchAlgorithmException, InvalidKeyException {
        return spi.engineGenerateSecret(algorithm);
    }
}
