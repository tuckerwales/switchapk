package java.security;

import java.nio.ByteBuffer;
import libcore.crypto.Services;

public abstract class MessageDigest extends MessageDigestSpi {
    private final String algorithm;
    Provider provider;

    protected MessageDigest(String algorithm) {
        this.algorithm = algorithm;
    }

    public static MessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
        NoSuchAlgorithmException failure = null;
        for (Provider.Service s : Services.all("MessageDigest", algorithm)) {
            try {
                return wrap(s, algorithm);
            } catch (NoSuchAlgorithmException e) {
                if (failure == null) failure = e;
            }
        }
        if (failure != null) throw failure;
        throw new NoSuchAlgorithmException(algorithm + " MessageDigest not available");
    }

    public static MessageDigest getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return wrap(Services.in("MessageDigest", algorithm, provider), algorithm);
    }

    public static MessageDigest getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        return wrap(Services.in("MessageDigest", algorithm, provider), algorithm);
    }

    private static MessageDigest wrap(Provider.Service s, String algorithm) throws NoSuchAlgorithmException {
        MessageDigestSpi spi = Services.newSpi(s, MessageDigestSpi.class, null);
        MessageDigest md = spi instanceof MessageDigest ? (MessageDigest) spi : new Delegate(spi, algorithm);
        md.provider = s.getProvider();
        return md;
    }

    public final Provider getProvider() {
        return provider;
    }

    public void update(byte input) {
        engineUpdate(input);
    }

    public void update(byte[] input, int offset, int len) {
        if (input == null) throw new IllegalArgumentException("No input buffer given");
        if (offset < 0 || len < 0 || input.length - offset < len) throw new IllegalArgumentException("Bad arguments");
        engineUpdate(input, offset, len);
    }

    public void update(byte[] input) {
        engineUpdate(input, 0, input.length);
    }

    public final void update(ByteBuffer input) {
        if (input == null) throw new NullPointerException();
        engineUpdate(input);
    }

    public byte[] digest() {
        return engineDigest();
    }

    public int digest(byte[] buf, int offset, int len) throws DigestException {
        if (buf == null) throw new IllegalArgumentException("No output buffer given");
        if (offset < 0 || len < 0 || buf.length - offset < len) throw new IllegalArgumentException("Output buffer too small for specified offset and length");
        return engineDigest(buf, offset, len);
    }

    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    public String toString() {
        return algorithm + " Message Digest from " + (provider != null ? provider.getName() : "<unknown>");
    }

    /** Constant time for equal lengths. */
    public static boolean isEqual(byte[] digesta, byte[] digestb) {
        if (digesta == digestb) return true;
        if (digesta == null || digestb == null || digesta.length != digestb.length) return false;
        int r = 0;
        for (int i = 0; i < digesta.length; i++) r |= digesta[i] ^ digestb[i];
        return r == 0;
    }

    public void reset() {
        engineReset();
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public final int getDigestLength() {
        int n = engineGetDigestLength();
        if (n == 0) {
            try {
                return ((MessageDigest) clone()).digest().length;
            } catch (CloneNotSupportedException e) {
                return n;
            }
        }
        return n;
    }

    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) return super.clone();
        throw new CloneNotSupportedException();
    }

    /** Wraps a provider's MessageDigestSpi that is not itself a MessageDigest. */
    static final class Delegate extends MessageDigest implements Cloneable {
        private MessageDigestSpi spi;

        Delegate(MessageDigestSpi spi, String algorithm) {
            super(algorithm);
            this.spi = spi;
        }

        public Object clone() throws CloneNotSupportedException {
            if (!(spi instanceof Cloneable)) throw new CloneNotSupportedException();
            Delegate d = (Delegate) super.clone();
            d.spi = (MessageDigestSpi) spi.clone();
            return d;
        }

        protected int engineGetDigestLength() {
            return spi.engineGetDigestLength();
        }

        protected void engineUpdate(byte input) {
            spi.engineUpdate(input);
        }

        protected void engineUpdate(byte[] input, int offset, int len) {
            spi.engineUpdate(input, offset, len);
        }

        protected void engineUpdate(ByteBuffer input) {
            spi.engineUpdate(input);
        }

        protected byte[] engineDigest() {
            return spi.engineDigest();
        }

        protected int engineDigest(byte[] buf, int offset, int len) throws DigestException {
            return spi.engineDigest(buf, offset, len);
        }

        protected void engineReset() {
            spi.engineReset();
        }
    }
}
