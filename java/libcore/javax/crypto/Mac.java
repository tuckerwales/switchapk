package javax.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.spec.AlgorithmParameterSpec;
import libcore.crypto.Services;

public class Mac implements Cloneable {
    private MacSpi spi;
    private final Provider provider;
    private final String algorithm;
    private boolean initialized;

    protected Mac(MacSpi macSpi, Provider provider, String algorithm) {
        this.spi = macSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public static final Mac getInstance(String algorithm) throws NoSuchAlgorithmException {
        Provider.Service s = Services.first("Mac", algorithm);
        return new Mac(Services.newSpi(s, MacSpi.class, null), s.getProvider(), algorithm);
    }

    public static final Mac getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Provider.Service s = Services.in("Mac", algorithm, provider);
        return new Mac(Services.newSpi(s, MacSpi.class, null), s.getProvider(), algorithm);
    }

    public static final Mac getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        Provider.Service s = Services.in("Mac", algorithm, provider);
        return new Mac(Services.newSpi(s, MacSpi.class, null), s.getProvider(), algorithm);
    }

    public final Provider getProvider() {
        return provider;
    }

    public final int getMacLength() {
        return spi.engineGetMacLength();
    }

    public final void init(Key key) throws InvalidKeyException {
        try {
            spi.engineInit(key, null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("init() failed", e);
        }
        initialized = true;
    }

    public final void init(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        spi.engineInit(key, params);
        initialized = true;
    }

    private void check() {
        if (!initialized) throw new IllegalStateException("MAC not initialized");
    }

    public final void update(byte input) throws IllegalStateException {
        check();
        spi.engineUpdate(input);
    }

    public final void update(byte[] input) throws IllegalStateException {
        check();
        if (input != null) spi.engineUpdate(input, 0, input.length);
    }

    public final void update(byte[] input, int offset, int len) throws IllegalStateException {
        check();
        if (input != null) {
            if (offset < 0 || len > input.length - offset || len < 0) throw new IllegalArgumentException("Bad arguments");
            spi.engineUpdate(input, offset, len);
        }
    }

    public final void update(ByteBuffer input) {
        check();
        if (input == null) throw new IllegalArgumentException("Buffer must not be null");
        spi.engineUpdate(input);
    }

    public final byte[] doFinal() throws IllegalStateException {
        check();
        byte[] mac = spi.engineDoFinal();
        spi.engineReset();
        return mac;
    }

    public final void doFinal(byte[] output, int outOffset) throws ShortBufferException, IllegalStateException {
        check();
        int macLen = getMacLength();
        if (output == null || output.length - outOffset < macLen) throw new ShortBufferException("Cannot store MAC in output buffer");
        byte[] mac = doFinal();
        System.arraycopy(mac, 0, output, outOffset, macLen);
    }

    public final byte[] doFinal(byte[] input) throws IllegalStateException {
        check();
        update(input);
        return doFinal();
    }

    public final void reset() {
        spi.engineReset();
    }

    public final Object clone() throws CloneNotSupportedException {
        Mac that = (Mac) super.clone();
        that.spi = (MacSpi) spi.clone();
        return that;
    }
}
