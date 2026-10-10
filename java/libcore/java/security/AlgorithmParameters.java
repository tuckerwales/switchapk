package java.security;

import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import libcore.crypto.Services;

public class AlgorithmParameters {
    private final AlgorithmParametersSpi spi;
    private final Provider provider;
    private final String algorithm;
    private boolean initialized;

    protected AlgorithmParameters(AlgorithmParametersSpi paramSpi, Provider provider, String algorithm) {
        this.spi = paramSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static AlgorithmParameters getInstance(String algorithm) throws NoSuchAlgorithmException {
        Provider.Service s = Services.first("AlgorithmParameters", algorithm);
        return new AlgorithmParameters(Services.newSpi(s, AlgorithmParametersSpi.class, null), s.getProvider(), algorithm);
    }

    public static AlgorithmParameters getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("AlgorithmParameters", algorithm, provider);
        return new AlgorithmParameters(Services.newSpi(s, AlgorithmParametersSpi.class, null), s.getProvider(), algorithm);
    }

    public static AlgorithmParameters getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("AlgorithmParameters", algorithm, provider);
        return new AlgorithmParameters(Services.newSpi(s, AlgorithmParametersSpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void init(AlgorithmParameterSpec paramSpec) throws InvalidParameterSpecException {
        if (initialized) throw new InvalidParameterSpecException("already initialized");
        spi.engineInit(paramSpec);
        initialized = true;
    }

    public final void init(byte[] params) throws IOException {
        if (initialized) throw new IOException("already initialized");
        spi.engineInit(params);
        initialized = true;
    }

    public final void init(byte[] params, String format) throws IOException {
        if (initialized) throw new IOException("already initialized");
        spi.engineInit(params, format);
        initialized = true;
    }

    public final <T extends AlgorithmParameterSpec> T getParameterSpec(Class<T> paramSpec)
            throws InvalidParameterSpecException {
        if (!initialized) throw new InvalidParameterSpecException("not initialized");
        return spi.engineGetParameterSpec(paramSpec);
    }

    public final byte[] getEncoded() throws IOException {
        if (!initialized) throw new IOException("not initialized");
        return spi.engineGetEncoded();
    }

    public final byte[] getEncoded(String format) throws IOException {
        if (!initialized) throw new IOException("not initialized");
        return spi.engineGetEncoded(format);
    }

    public final String toString() {
        return initialized ? spi.engineToString() : null;
    }
}
