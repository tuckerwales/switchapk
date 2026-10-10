package javax.crypto;

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import libcore.crypto.Services;

public class Cipher {
    public static final int ENCRYPT_MODE = 1;
    public static final int DECRYPT_MODE = 2;
    public static final int WRAP_MODE = 3;
    public static final int UNWRAP_MODE = 4;
    public static final int PUBLIC_KEY = 1;
    public static final int PRIVATE_KEY = 2;
    public static final int SECRET_KEY = 3;

    private static final int UNINITIALIZED = 0;

    private CipherSpi spi;
    private Provider provider;
    private final String transformation;
    private int opmode = UNINITIALIZED;

    // Candidate services and how each must be configured, for getInstance without a provider:
    // the first one whose init() accepts the key wins, as in the JDK's delayed provider selection.
    private List<Candidate> candidates;

    protected Cipher(CipherSpi cipherSpi, Provider provider, String transformation) {
        if (cipherSpi == null) throw new NullPointerException("cipherSpi == null");
        this.spi = cipherSpi;
        this.provider = provider;
        this.transformation = transformation;
    }

    private Cipher(List<Candidate> candidates, String transformation) {
        this.candidates = candidates;
        this.transformation = transformation;
    }

    private static final class Candidate {
        final Provider.Service service;
        final String mode;
        final String padding;

        Candidate(Provider.Service service, String mode, String padding) {
            this.service = service;
            this.mode = mode;
            this.padding = padding;
        }

        CipherSpi create() throws NoSuchAlgorithmException, NoSuchPaddingException {
            CipherSpi spi = Services.newSpi(service, CipherSpi.class, null);
            if (mode != null) spi.engineSetMode(mode);
            if (padding != null) spi.engineSetPadding(padding);
            return spi;
        }
    }

    /** Splits "AES/GCM/NoPadding" into its parts; mode and padding are null when absent. */
    private static String[] parse(String transformation) throws NoSuchAlgorithmException {
        if (transformation == null || transformation.isEmpty()) {
            throw new NoSuchAlgorithmException("Invalid transformation format:" + transformation);
        }
        String[] parts = transformation.split("/", -1);
        if (parts.length != 1 && parts.length != 3) {
            throw new NoSuchAlgorithmException("Invalid transformation format:" + transformation);
        }
        String alg = parts[0].trim();
        if (alg.isEmpty()) throw new NoSuchAlgorithmException("Invalid transformation format:" + transformation);
        if (parts.length == 1) return new String[] { alg, null, null };
        String mode = parts[1].trim();
        String pad = parts[2].trim();
        return new String[] { alg, mode.isEmpty() ? null : mode, pad.isEmpty() ? null : pad };
    }

    private static List<Candidate> candidates(String transformation, Provider only)
            throws NoSuchAlgorithmException {
        String[] p = parse(transformation);
        String alg = p[0], mode = p[1], pad = p[2];
        ArrayList<Candidate> out = new ArrayList<Candidate>();
        Provider[] providers = only != null ? new Provider[] { only } : Security.getProviders();
        for (Provider prov : providers) {
            Provider.Service s;
            if (mode != null && pad != null && (s = prov.getService("Cipher", alg + "/" + mode + "/" + pad)) != null) {
                out.add(new Candidate(s, null, null));
                continue;
            }
            if (mode != null && (s = prov.getService("Cipher", alg + "/" + mode)) != null) {
                out.add(new Candidate(s, null, pad));
                continue;
            }
            if (pad != null && (s = prov.getService("Cipher", alg + "//" + pad)) != null) {
                out.add(new Candidate(s, mode, null));
                continue;
            }
            if ((s = prov.getService("Cipher", alg)) != null) {
                if (mode != null && !supports(s, "SupportedModes", mode)) continue;
                if (pad != null && !supports(s, "SupportedPaddings", pad)) continue;
                out.add(new Candidate(s, mode, pad));
            }
        }
        return out;
    }

    private static boolean supports(Provider.Service s, String attr, String value) {
        String v = s.getAttribute(attr);
        if (v == null) return true;
        String want = value.toUpperCase(Locale.ENGLISH);
        for (String option : v.toUpperCase(Locale.ENGLISH).split("\\|")) {
            if (option.equals(want)) return true;
        }
        return false;
    }

    public static final Cipher getInstance(String transformation) throws NoSuchAlgorithmException, NoSuchPaddingException {
        List<Candidate> l = candidates(transformation, null);
        if (l.isEmpty()) throw new NoSuchAlgorithmException("Cannot find any provider supporting " + transformation);
        // Fail now if no candidate can even be configured for this mode and padding.
        Exception failure = null;
        ArrayList<Candidate> usable = new ArrayList<Candidate>();
        for (Candidate c : l) {
            try {
                c.create();
                usable.add(c);
            } catch (NoSuchAlgorithmException e) {
                if (failure == null) failure = e;
            } catch (NoSuchPaddingException e) {
                if (failure == null) failure = e;
            }
        }
        if (usable.isEmpty()) {
            NoSuchAlgorithmException e = new NoSuchAlgorithmException("Cannot find any provider supporting " + transformation);
            e.initCause(failure);
            throw e;
        }
        return new Cipher(usable, transformation);
    }

    public static final Cipher getInstance(String transformation, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException {
        if (provider == null || provider.isEmpty()) throw new IllegalArgumentException("Missing provider");
        Provider p = Security.getProvider(provider);
        if (p == null) throw new NoSuchProviderException("Provider not available: " + provider);
        return getInstance(transformation, p);
    }

    public static final Cipher getInstance(String transformation, Provider provider)
            throws NoSuchAlgorithmException, NoSuchPaddingException {
        if (provider == null) throw new IllegalArgumentException("Missing provider");
        List<Candidate> l = candidates(transformation, provider);
        if (l.isEmpty()) {
            throw new NoSuchAlgorithmException("No such algorithm: " + transformation + " for provider " + provider.getName());
        }
        Candidate c = l.get(0);
        return new Cipher(c.create(), provider, transformation);
    }

    /** Picks the first candidate that is chosen before init, for calls that need an implementation. */
    private void chooseFirst() {
        if (spi != null) return;
        for (Candidate c : candidates) {
            try {
                spi = c.create();
                provider = c.service.getProvider();
                candidates = null;
                return;
            } catch (Exception ignored) {
            }
        }
        throw new java.security.ProviderException("Could not construct CipherSpi instance");
    }

    private interface InitAction {
        void run(CipherSpi spi) throws InvalidKeyException, InvalidAlgorithmParameterException;
    }

    private void choose(InitAction init) throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (candidates == null) {
            init.run(spi);
            return;
        }
        Exception failure = null;
        for (Candidate c : candidates) {
            try {
                CipherSpi s = c.create();
                init.run(s);
                spi = s;
                provider = c.service.getProvider();
                candidates = null;
                return;
            } catch (InvalidKeyException e) {
                if (failure == null) failure = e;
            } catch (InvalidAlgorithmParameterException e) {
                if (failure == null) failure = e;
            } catch (Exception e) {
                if (failure == null) failure = e;
            }
        }
        if (failure instanceof InvalidKeyException) throw (InvalidKeyException) failure;
        if (failure instanceof InvalidAlgorithmParameterException) throw (InvalidAlgorithmParameterException) failure;
        throw new InvalidKeyException("No installed provider supports this key: " + (failure != null ? failure : ""), failure);
    }

    public final Provider getProvider() {
        chooseFirst();
        return provider;
    }

    public final String getAlgorithm() {
        return transformation;
    }

    public final int getBlockSize() {
        chooseFirst();
        return spi.engineGetBlockSize();
    }

    public final int getOutputSize(int inputLen) {
        if (opmode == UNINITIALIZED) throw new IllegalStateException("Cipher not initialized");
        if (inputLen < 0) throw new IllegalArgumentException("Input size must be equal to or greater than zero");
        return spi.engineGetOutputSize(inputLen);
    }

    public final byte[] getIV() {
        chooseFirst();
        return spi.engineGetIV();
    }

    public final AlgorithmParameters getParameters() {
        chooseFirst();
        return spi.engineGetParameters();
    }

    public final ExemptionMechanism getExemptionMechanism() {
        return null;
    }

    private static void checkOpmode(int opmode) {
        if (opmode < ENCRYPT_MODE || opmode > UNWRAP_MODE) throw new InvalidParameterException("Invalid operation mode");
    }

    public final void init(int opmode, Key key) throws InvalidKeyException {
        init(opmode, key, new SecureRandom());
    }

    public final void init(final int opmode, final Key key, final SecureRandom random) throws InvalidKeyException {
        checkOpmode(opmode);
        try {
            choose(new InitAction() {
                public void run(CipherSpi s) throws InvalidKeyException {
                    s.engineInit(opmode, key, random);
                }
            });
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        }
        this.opmode = opmode;
    }

    public final void init(int opmode, Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        init(opmode, key, params, new SecureRandom());
    }

    public final void init(final int opmode, final Key key, final AlgorithmParameterSpec params, final SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        checkOpmode(opmode);
        choose(new InitAction() {
            public void run(CipherSpi s) throws InvalidKeyException, InvalidAlgorithmParameterException {
                s.engineInit(opmode, key, params, random);
            }
        });
        this.opmode = opmode;
    }

    public final void init(int opmode, Key key, AlgorithmParameters params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        init(opmode, key, params, new SecureRandom());
    }

    public final void init(final int opmode, final Key key, final AlgorithmParameters params, final SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        checkOpmode(opmode);
        choose(new InitAction() {
            public void run(CipherSpi s) throws InvalidKeyException, InvalidAlgorithmParameterException {
                s.engineInit(opmode, key, params, random);
            }
        });
        this.opmode = opmode;
    }

    public final void init(int opmode, java.security.cert.Certificate certificate) throws InvalidKeyException {
        init(opmode, certificate, new SecureRandom());
    }

    public final void init(int opmode, java.security.cert.Certificate certificate, SecureRandom random)
            throws InvalidKeyException {
        init(opmode, certificate.getPublicKey(), random);
    }

    private void checkCipherState() {
        if (opmode == UNINITIALIZED) throw new IllegalStateException("Cipher not initialized");
        if (opmode != ENCRYPT_MODE && opmode != DECRYPT_MODE) {
            throw new IllegalStateException("Cipher not initialized for encryption/decryption");
        }
    }

    private static void checkRange(byte[] b, int off, int len) {
        if (b == null || off < 0 || len < 0 || len > b.length - off) throw new IllegalArgumentException("Bad arguments");
    }

    public final byte[] update(byte[] input) {
        checkCipherState();
        if (input == null) throw new IllegalArgumentException("Null input buffer");
        if (input.length == 0) return null;
        return spi.engineUpdate(input, 0, input.length);
    }

    public final byte[] update(byte[] input, int inputOffset, int inputLen) {
        checkCipherState();
        checkRange(input, inputOffset, inputLen);
        if (inputLen == 0) return null;
        return spi.engineUpdate(input, inputOffset, inputLen);
    }

    public final int update(byte[] input, int inputOffset, int inputLen, byte[] output) throws ShortBufferException {
        return update(input, inputOffset, inputLen, output, 0);
    }

    public final int update(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException {
        checkCipherState();
        checkRange(input, inputOffset, inputLen);
        if (outputOffset < 0) throw new IllegalArgumentException("Bad arguments");
        if (inputLen == 0) return 0;
        return spi.engineUpdate(input, inputOffset, inputLen, output, outputOffset);
    }

    public final int update(ByteBuffer input, ByteBuffer output) throws ShortBufferException {
        checkCipherState();
        if (input == null || output == null) throw new IllegalArgumentException("Buffers must not be null");
        if (input == output) throw new IllegalArgumentException("Input and output buffers must not be the same object, consider using buffer.duplicate()");
        if (output.isReadOnly()) throw new java.nio.ReadOnlyBufferException();
        return spi.engineUpdate(input, output);
    }

    public final byte[] doFinal() throws IllegalBlockSizeException, BadPaddingException {
        checkCipherState();
        return spi.engineDoFinal(null, 0, 0);
    }

    public final int doFinal(byte[] output, int outputOffset)
            throws IllegalBlockSizeException, ShortBufferException, BadPaddingException {
        checkCipherState();
        if (output == null || outputOffset < 0) throw new IllegalArgumentException("Bad arguments");
        return spi.engineDoFinal(null, 0, 0, output, outputOffset);
    }

    public final byte[] doFinal(byte[] input) throws IllegalBlockSizeException, BadPaddingException {
        checkCipherState();
        if (input == null) throw new IllegalArgumentException("Null input buffer");
        return spi.engineDoFinal(input, 0, input.length);
    }

    public final byte[] doFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        checkCipherState();
        checkRange(input, inputOffset, inputLen);
        return spi.engineDoFinal(input, inputOffset, inputLen);
    }

    public final int doFinal(byte[] input, int inputOffset, int inputLen, byte[] output)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return doFinal(input, inputOffset, inputLen, output, 0);
    }

    public final int doFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        checkCipherState();
        checkRange(input, inputOffset, inputLen);
        if (outputOffset < 0) throw new IllegalArgumentException("Bad arguments");
        return spi.engineDoFinal(input, inputOffset, inputLen, output, outputOffset);
    }

    public final int doFinal(ByteBuffer input, ByteBuffer output)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        checkCipherState();
        if (input == null || output == null) throw new IllegalArgumentException("Buffers must not be null");
        if (input == output) throw new IllegalArgumentException("Input and output buffers must not be the same object, consider using buffer.duplicate()");
        if (output.isReadOnly()) throw new java.nio.ReadOnlyBufferException();
        return spi.engineDoFinal(input, output);
    }

    public final byte[] wrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
        if (opmode != WRAP_MODE) throw new IllegalStateException("Cipher not initialized for wrapping keys");
        return spi.engineWrap(key);
    }

    public final Key unwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {
        if (opmode != UNWRAP_MODE) throw new IllegalStateException("Cipher not initialized for unwrapping keys");
        if (wrappedKeyType != SECRET_KEY && wrappedKeyType != PRIVATE_KEY && wrappedKeyType != PUBLIC_KEY) {
            throw new InvalidParameterException("Invalid key type");
        }
        return spi.engineUnwrap(wrappedKey, wrappedKeyAlgorithm, wrappedKeyType);
    }

    /** No import restrictions apply. */
    public static final int getMaxAllowedKeyLength(String transformation) throws NoSuchAlgorithmException {
        parse(transformation);
        return Integer.MAX_VALUE;
    }

    public static final AlgorithmParameterSpec getMaxAllowedParameterSpec(String transformation)
            throws NoSuchAlgorithmException {
        parse(transformation);
        return null;
    }

    public final void updateAAD(byte[] src) {
        if (src == null) throw new IllegalArgumentException("src buffer is null");
        updateAAD(src, 0, src.length);
    }

    public final void updateAAD(byte[] src, int offset, int len) {
        if (opmode == UNINITIALIZED) throw new IllegalStateException("Cipher not initialized");
        checkRange(src, offset, len);
        if (len == 0) return;
        spi.engineUpdateAAD(src, offset, len);
    }

    public final void updateAAD(ByteBuffer src) {
        if (opmode == UNINITIALIZED) throw new IllegalStateException("Cipher not initialized");
        if (src == null) throw new IllegalArgumentException("src ByteBuffer is null");
        if (src.remaining() == 0) return;
        spi.engineUpdateAAD(src);
    }

    public String toString() {
        String mode;
        switch (opmode) {
            case ENCRYPT_MODE: mode = "encryption"; break;
            case DECRYPT_MODE: mode = "decryption"; break;
            case WRAP_MODE: mode = "key wrapping"; break;
            case UNWRAP_MODE: mode = "key unwrapping"; break;
            default: mode = "not initialized";
        }
        return "Cipher." + transformation + ", mode: " + mode + ", algorithm from: "
                + (provider != null ? provider.getName() : "(no provider)");
    }
}
