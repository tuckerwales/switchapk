package android.security.keystore;

import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;

/** SecretKeyFactory.getInstance(alg, "AndroidKeyStore"): only getKeySpec(key, KeyInfo.class). */
public class AndroidKeyStoreSecretKeyFactorySpi extends SecretKeyFactorySpi {
    protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpecClass) throws InvalidKeySpecException {
        if (keySpecClass == null) throw new InvalidKeySpecException("keySpecClass == null");
        if (!(key instanceof AndroidKeyStoreSecretKey)) {
            throw new InvalidKeySpecException("Only Android KeyStore secret keys supported: " + (key == null ? "null" : key.getClass().getName()));
        }
        if (!KeyInfo.class.equals(keySpecClass)) throw new InvalidKeySpecException("Unsupported key spec: " + keySpecClass.getName());
        return new KeyInfo(((AndroidKeyStoreSecretKey) key).entry());
    }

    protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
        throw new InvalidKeySpecException("To generate secret key in Android Keystore, use KeyGenerator initialized with "
                + KeyGenParameterSpec.class.getName());
    }

    protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
        if (key == null) throw new InvalidKeyException("key == null");
        if (!(key instanceof AndroidKeyStoreSecretKey)) {
            throw new InvalidKeyException("To import a secret key into Android Keystore, use KeyStore.setEntry");
        }
        return key;
    }
}
