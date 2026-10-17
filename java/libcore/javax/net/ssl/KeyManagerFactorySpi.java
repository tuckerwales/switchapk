package javax.net.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

public abstract class KeyManagerFactorySpi {
    public KeyManagerFactorySpi() {
    }

    protected abstract void engineInit(KeyStore ks, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException;

    protected abstract void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException;

    protected abstract KeyManager[] engineGetKeyManagers();
}
