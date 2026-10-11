package javax.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

public abstract class KeyAgreementSpi {
    public KeyAgreementSpi() {
    }

    protected abstract void engineInit(Key key, SecureRandom random) throws InvalidKeyException;

    protected abstract void engineInit(Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException;

    protected abstract Key engineDoPhase(Key key, boolean lastPhase) throws InvalidKeyException, IllegalStateException;

    protected abstract byte[] engineGenerateSecret() throws IllegalStateException;

    protected abstract int engineGenerateSecret(byte[] sharedSecret, int offset)
            throws IllegalStateException, ShortBufferException;

    protected abstract SecretKey engineGenerateSecret(String algorithm)
            throws IllegalStateException, NoSuchAlgorithmException, InvalidKeyException;
}
