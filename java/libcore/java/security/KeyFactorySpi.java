package java.security;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

public abstract class KeyFactorySpi {
    public KeyFactorySpi() {
    }

    protected abstract PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException;

    protected abstract PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException;

    protected abstract <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException;

    protected abstract Key engineTranslateKey(Key key) throws InvalidKeyException;
}
