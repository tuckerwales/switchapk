package javax.net.ssl;

import java.security.NoSuchAlgorithmException;

/**
 * There is no TLS provider yet (WS11), so no trust manager algorithm is available. getInstance
 * throws NoSuchAlgorithmException, which TLS clients (ktor, OkHttp) report as a failed
 * connection, instead of the class being missing and NoClassDefFoundError killing the
 * calling thread. KeyStore, Provider and the SPI are not here yet, so neither are the members
 * that take them.
 */
public class TrustManagerFactory {
    private final String algorithm;

    private TrustManagerFactory(String algorithm) {
        this.algorithm = algorithm;
    }

    public static final String getDefaultAlgorithm() {
        String alg = System.getProperty("ssl.TrustManagerFactory.algorithm");
        return alg != null ? alg : "PKIX";
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static final TrustManagerFactory getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm == null) throw new NullPointerException("algorithm == null");
        throw new NoSuchAlgorithmException(algorithm + " TrustManagerFactory not available");
    }

    public static final TrustManagerFactory getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, java.security.NoSuchProviderException {
        if (provider == null || provider.isEmpty()) throw new IllegalArgumentException("Provider is null or empty");
        throw new java.security.NoSuchProviderException(provider);
    }

    public final TrustManager[] getTrustManagers() {
        throw new IllegalStateException("TrustManagerFactory is not initialized");
    }
}
