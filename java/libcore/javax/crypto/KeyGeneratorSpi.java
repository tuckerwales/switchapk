package javax.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

public abstract class KeyGeneratorSpi {
    public KeyGeneratorSpi() {
    }

    protected abstract void engineInit(SecureRandom random);

    protected abstract void engineInit(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException;

    protected abstract void engineInit(int keysize, SecureRandom random);

    protected abstract SecretKey engineGenerateKey();
}
