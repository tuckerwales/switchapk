package libcore.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;

/** TrustManagerFactory "PKIX": a TrustManagerImpl over the given KeyStore, or the system CAs for null. */
public final class TrustManagerFactoryImpl extends TrustManagerFactorySpi {
    private TrustManagerImpl trustManager;

    public TrustManagerFactoryImpl() {
    }

    protected void engineInit(KeyStore ks) throws KeyStoreException {
        trustManager = TrustManagerImpl.forKeyStore(ks);
    }

    protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException("ManagerFactoryParameters not supported");
    }

    protected TrustManager[] engineGetTrustManagers() {
        if (trustManager == null) throw new IllegalStateException("TrustManagerFactory is not initialized");
        return new TrustManager[] {trustManager};
    }
}
