package java.security;

import libcore.crypto.Services;

/**
 * Cryptographically strong random numbers. The default instance reads the
 * operating system's entropy for every call (getrandom(2) on the host,
 * libnx randomGet on the Switch); setSeed only adds to it, as with
 * Android's default NativePRNG/OpenSSL generator.
 */
public class SecureRandom extends java.util.Random {
    private SecureRandomSpi secureRandomSpi;
    private Provider provider;
    private String algorithm;

    public SecureRandom() {
        super(0);
        initDefault();
    }

    public SecureRandom(byte[] seed) {
        super(0);
        initDefault();
        secureRandomSpi.engineSetSeed(seed);
    }

    protected SecureRandom(SecureRandomSpi secureRandomSpi, Provider provider) {
        this(secureRandomSpi, provider, null);
    }

    private SecureRandom(SecureRandomSpi spi, Provider provider, String algorithm) {
        super(0);
        this.secureRandomSpi = spi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    private void initDefault() {
        for (Provider p : Security.getProviders()) {
            Provider.Service s = p.getDefaultSecureRandomService();
            if (s == null) continue;
            try {
                secureRandomSpi = Services.newSpi(s, SecureRandomSpi.class, null);
                provider = p;
                algorithm = s.getAlgorithm();
                return;
            } catch (NoSuchAlgorithmException ignored) {
            }
        }
        secureRandomSpi = new libcore.crypto.NativePrng();
        provider = null;
        algorithm = "NativePRNG";
    }

    public static SecureRandom getInstance(String algorithm) throws NoSuchAlgorithmException {
        NoSuchAlgorithmException failure = null;
        for (Provider.Service s : Services.all("SecureRandom", algorithm)) {
            try {
                return new SecureRandom(Services.newSpi(s, SecureRandomSpi.class, null), s.getProvider(), algorithm);
            } catch (NoSuchAlgorithmException e) {
                if (failure == null) failure = e;
            }
        }
        if (failure != null) throw failure;
        throw new NoSuchAlgorithmException(algorithm + " SecureRandom not available");
    }

    public static SecureRandom getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("SecureRandom", algorithm, provider);
        return new SecureRandom(Services.newSpi(s, SecureRandomSpi.class, null), s.getProvider(), algorithm);
    }

    public static SecureRandom getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("SecureRandom", algorithm, provider);
        return new SecureRandom(Services.newSpi(s, SecureRandomSpi.class, null), s.getProvider(), algorithm);
    }

    public static SecureRandom getInstance(String algorithm, SecureRandomParameters params) throws NoSuchAlgorithmException {
        if (params == null) throw new IllegalArgumentException("params cannot be null");
        throw new NoSuchAlgorithmException(algorithm + " SecureRandom with parameters not available");
    }

    public static SecureRandom getInstance(String algorithm, SecureRandomParameters params, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return getInstance(algorithm, params);
    }

    public static SecureRandom getInstance(String algorithm, SecureRandomParameters params, Provider provider)
            throws NoSuchAlgorithmException {
        return getInstance(algorithm, params);
    }

    public final Provider getProvider() {
        return provider;
    }

    public String getAlgorithm() {
        return algorithm != null ? algorithm : "unknown";
    }

    public String toString() {
        return secureRandomSpi.toString();
    }

    public SecureRandomParameters getParameters() {
        return secureRandomSpi.engineGetParameters();
    }

    public void setSeed(byte[] seed) {
        secureRandomSpi.engineSetSeed(seed);
    }

    public void setSeed(long seed) {
        // java.util.Random's constructor calls this before the SPI exists.
        if (seed != 0 && secureRandomSpi != null) {
            byte[] b = new byte[8];
            for (int i = 0; i < 8; i++) b[i] = (byte) (seed >>> (56 - 8 * i));
            secureRandomSpi.engineSetSeed(b);
        }
    }

    public void nextBytes(byte[] bytes) {
        secureRandomSpi.engineNextBytes(bytes);
    }

    public void nextBytes(byte[] bytes, SecureRandomParameters params) {
        if (params == null) throw new IllegalArgumentException("params cannot be null");
        secureRandomSpi.engineNextBytes(bytes, params);
    }

    protected final int next(int numBits) {
        int numBytes = (numBits + 7) / 8;
        byte[] b = new byte[numBytes];
        nextBytes(b);
        int next = 0;
        for (int i = 0; i < numBytes; i++) next = (next << 8) + (b[i] & 0xFF);
        return next >>> (numBytes * 8 - numBits);
    }

    public static byte[] getSeed(int numBytes) {
        return libcore.crypto.NativePrng.entropy(numBytes);
    }

    public byte[] generateSeed(int numBytes) {
        if (numBytes < 0) throw new IllegalArgumentException("numBytes cannot be negative");
        return secureRandomSpi.engineGenerateSeed(numBytes);
    }

    public static SecureRandom getInstanceStrong() throws NoSuchAlgorithmException {
        return new SecureRandom();
    }

    public void reseed() {
        secureRandomSpi.engineReseed(null);
    }

    public void reseed(SecureRandomParameters params) {
        if (params == null) throw new IllegalArgumentException("params cannot be null");
        secureRandomSpi.engineReseed(params);
    }
}
