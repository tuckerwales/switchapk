package javax.net.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import libcore.crypto.Services;

public class TrustManagerFactory {
    private final TrustManagerFactorySpi factorySpi;
    private final Provider provider;
    private final String algorithm;

    protected TrustManagerFactory(TrustManagerFactorySpi factorySpi, Provider provider, String algorithm) {
        this.factorySpi = factorySpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public static final String getDefaultAlgorithm() {
        String alg = Security.getProperty("ssl.TrustManagerFactory.algorithm");
        return alg != null ? alg : "PKIX";
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static final TrustManagerFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm == null) throw new NullPointerException("null algorithm name");
        Provider.Service s = Services.first("TrustManagerFactory", algorithm);
        return new TrustManagerFactory(Services.newSpi(s, TrustManagerFactorySpi.class, null), s.getProvider(),
                algorithm);
    }

    public static final TrustManagerFactory getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (provider == null || provider.isEmpty()) throw new IllegalArgumentException("missing provider");
        Provider.Service s = Services.in("TrustManagerFactory", algorithm, provider);
        return new TrustManagerFactory(Services.newSpi(s, TrustManagerFactorySpi.class, null), s.getProvider(),
                algorithm);
    }

    public static final TrustManagerFactory getInstance(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        if (provider == null) throw new IllegalArgumentException("missing provider");
        Provider.Service s = Services.in("TrustManagerFactory", algorithm, provider);
        return new TrustManagerFactory(Services.newSpi(s, TrustManagerFactorySpi.class, null), s.getProvider(),
                algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void init(KeyStore ks) throws KeyStoreException {
        factorySpi.engineInit(ks);
    }

    public final void init(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        factorySpi.engineInit(spec);
    }

    public final TrustManager[] getTrustManagers() {
        return factorySpi.engineGetTrustManagers();
    }
}
