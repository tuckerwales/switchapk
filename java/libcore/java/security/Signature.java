package java.security;

import java.nio.ByteBuffer;
import java.security.spec.AlgorithmParameterSpec;
import libcore.crypto.Services;

public abstract class Signature extends SignatureSpi {
    protected static final int UNINITIALIZED = 0;
    protected static final int SIGN = 2;
    protected static final int VERIFY = 3;

    protected int state = UNINITIALIZED;
    private final String algorithm;
    Provider provider;

    protected Signature(String algorithm) {
        this.algorithm = algorithm;
    }

    public static Signature getInstance(String algorithm) throws NoSuchAlgorithmException {
        return wrap(Services.first("Signature", algorithm), algorithm);
    }

    public static Signature getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return wrap(Services.in("Signature", algorithm, provider), algorithm);
    }

    public static Signature getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        return wrap(Services.in("Signature", algorithm, provider), algorithm);
    }

    private static Signature wrap(Provider.Service s, String algorithm) throws NoSuchAlgorithmException {
        SignatureSpi spi = Services.newSpi(s, SignatureSpi.class, null);
        Signature sig = spi instanceof Signature ? (Signature) spi : new Delegate(spi, algorithm);
        sig.provider = s.getProvider();
        return sig;
    }

    public final Provider getProvider() {
        return provider;
    }

    public final void initVerify(PublicKey publicKey) throws InvalidKeyException {
        engineInitVerify(publicKey);
        state = VERIFY;
    }

    public final void initVerify(java.security.cert.Certificate certificate) throws InvalidKeyException {
        initVerify(certificate.getPublicKey());
    }

    public final void initSign(PrivateKey privateKey) throws InvalidKeyException {
        engineInitSign(privateKey);
        state = SIGN;
    }

    public final void initSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
        engineInitSign(privateKey, random);
        state = SIGN;
    }

    public final byte[] sign() throws SignatureException {
        if (state != SIGN) throw new SignatureException("object not initialized for signing");
        return engineSign();
    }

    public final int sign(byte[] outbuf, int offset, int len) throws SignatureException {
        if (outbuf == null) throw new IllegalArgumentException("No output buffer given");
        if (offset < 0 || len < 0) throw new IllegalArgumentException("offset or len is less than 0");
        if (outbuf.length - offset < len) throw new IllegalArgumentException("Output buffer too small for specified offset and length");
        if (state != SIGN) throw new SignatureException("object not initialized for signing");
        return engineSign(outbuf, offset, len);
    }

    public final boolean verify(byte[] signature) throws SignatureException {
        if (state != VERIFY) throw new SignatureException("object not initialized for verification");
        return engineVerify(signature);
    }

    public final boolean verify(byte[] signature, int offset, int length) throws SignatureException {
        if (state != VERIFY) throw new SignatureException("object not initialized for verification");
        if (signature == null) throw new IllegalArgumentException("signature is null");
        if (offset < 0 || length < 0) throw new IllegalArgumentException("offset or length is less than 0");
        if (signature.length - offset < length) throw new IllegalArgumentException("signature too small for specified offset and length");
        return engineVerify(signature, offset, length);
    }

    public final void update(byte b) throws SignatureException {
        checkState();
        engineUpdate(b);
    }

    public final void update(byte[] data) throws SignatureException {
        update(data, 0, data.length);
    }

    public final void update(byte[] data, int off, int len) throws SignatureException {
        checkState();
        if (data == null) throw new IllegalArgumentException("data is null");
        if (off < 0 || len < 0) throw new IllegalArgumentException("off or len is less than 0");
        if (data.length - off < len) throw new IllegalArgumentException("data too small for specified offset and length");
        engineUpdate(data, off, len);
    }

    public final void update(ByteBuffer data) throws SignatureException {
        checkState();
        if (data == null) throw new NullPointerException();
        engineUpdate(data);
    }

    private void checkState() throws SignatureException {
        if (state != SIGN && state != VERIFY) throw new SignatureException("object not initialized for signature or verification");
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public String toString() {
        String s = state == SIGN ? "SIGN" : state == VERIFY ? "VERIFY" : "UNINITIALIZED";
        return "Signature object: " + algorithm + "<" + s + ">";
    }

    @Deprecated
    public final void setParameter(String param, Object value) throws InvalidParameterException {
        engineSetParameter(param, value);
    }

    public final void setParameter(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
        engineSetParameter(params);
    }

    public final AlgorithmParameters getParameters() {
        return engineGetParameters();
    }

    @Deprecated
    public final Object getParameter(String param) throws InvalidParameterException {
        return engineGetParameter(param);
    }

    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) return super.clone();
        throw new CloneNotSupportedException();
    }

    static final class Delegate extends Signature implements Cloneable {
        private SignatureSpi spi;

        Delegate(SignatureSpi spi, String algorithm) {
            super(algorithm);
            this.spi = spi;
        }

        public Object clone() throws CloneNotSupportedException {
            if (!(spi instanceof Cloneable)) throw new CloneNotSupportedException();
            Delegate d = (Delegate) super.clone();
            d.spi = (SignatureSpi) spi.clone();
            return d;
        }

        protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
            spi.engineInitVerify(publicKey);
        }

        protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
            spi.engineInitSign(privateKey);
        }

        protected void engineInitSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
            spi.engineInitSign(privateKey, random);
        }

        protected void engineUpdate(byte b) throws SignatureException {
            spi.engineUpdate(b);
        }

        protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
            spi.engineUpdate(b, off, len);
        }

        protected void engineUpdate(ByteBuffer data) {
            spi.engineUpdate(data);
        }

        protected byte[] engineSign() throws SignatureException {
            return spi.engineSign();
        }

        protected int engineSign(byte[] outbuf, int offset, int len) throws SignatureException {
            return spi.engineSign(outbuf, offset, len);
        }

        protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
            return spi.engineVerify(sigBytes);
        }

        protected boolean engineVerify(byte[] sigBytes, int offset, int length) throws SignatureException {
            return spi.engineVerify(sigBytes, offset, length);
        }

        protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
            spi.engineSetParameter(param, value);
        }

        protected void engineSetParameter(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
            spi.engineSetParameter(params);
        }

        protected AlgorithmParameters engineGetParameters() {
            return spi.engineGetParameters();
        }

        protected Object engineGetParameter(String param) throws InvalidParameterException {
            return spi.engineGetParameter(param);
        }
    }
}
