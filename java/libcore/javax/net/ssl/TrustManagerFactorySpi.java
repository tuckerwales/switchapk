package javax.net.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;

public abstract class TrustManagerFactorySpi {
    public TrustManagerFactorySpi() {
    }

    protected abstract void engineInit(KeyStore ks) throws KeyStoreException;

    protected abstract void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException;

    protected abstract TrustManager[] engineGetTrustManagers();
}
