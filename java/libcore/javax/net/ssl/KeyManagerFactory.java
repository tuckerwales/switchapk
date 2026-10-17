package javax.net.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import libcore.crypto.Services;

public class KeyManagerFactory {
    private final KeyManagerFactorySpi factorySpi;
    private final Provider provider;
    private final String algorithm;

    protected KeyManagerFactory(KeyManagerFactorySpi factorySpi, Provider provider, String algorithm) {
        this.factorySpi = factorySpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public static final String getDefaultAlgorithm() {
        String alg = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        return alg != null ? alg : "PKIX";
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static final KeyManagerFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm == null) throw new NullPointerException("null algorithm name");
        Provider.Service s = Services.first("KeyManagerFactory", algorithm);
        return new KeyManagerFactory(Services.newSpi(s, KeyManagerFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public static final KeyManagerFactory getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (provider == null || provider.isEmpty()) throw new IllegalArgumentException("missing provider");
        Provider.Service s = Services.in("KeyManagerFactory", algorithm, provider);
        return new KeyManagerFactory(Services.newSpi(s, KeyManagerFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public static final KeyManagerFactory getInstance(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        if (provider == null) throw new IllegalArgumentException("missing provider");
        Provider.Service s = Services.in("KeyManagerFactory", algorithm, provider);
        return new KeyManagerFactory(Services.newSpi(s, KeyManagerFactorySpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void init(KeyStore ks, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        factorySpi.engineInit(ks, password);
    }

    public final void init(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        factorySpi.engineInit(spec);
    }

    public final KeyManager[] getKeyManagers() {
        return factorySpi.engineGetKeyManagers();
    }
}
