package java.security;

import java.nio.ByteBuffer;
import java.security.spec.AlgorithmParameterSpec;

public abstract class SignatureSpi {
    protected SecureRandom appRandom = null;

    public SignatureSpi() {
    }

    protected abstract void engineInitVerify(PublicKey publicKey) throws InvalidKeyException;

    protected abstract void engineInitSign(PrivateKey privateKey) throws InvalidKeyException;

    protected void engineInitSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
        this.appRandom = random;
        engineInitSign(privateKey);
    }

    protected abstract void engineUpdate(byte b) throws SignatureException;

    protected abstract void engineUpdate(byte[] b, int off, int len) throws SignatureException;

    protected void engineUpdate(ByteBuffer input) {
        if (!input.hasRemaining()) return;
        try {
            byte[] b = new byte[input.remaining()];
            input.get(b);
            engineUpdate(b, 0, b.length);
        } catch (SignatureException e) {
            throw new ProviderException("update() failed", e);
        }
    }

    protected abstract byte[] engineSign() throws SignatureException;

    protected int engineSign(byte[] outbuf, int offset, int len) throws SignatureException {
        byte[] sig = engineSign();
        if (len < sig.length) throw new SignatureException("partial signatures not returned");
        if (outbuf.length - offset < sig.length) throw new SignatureException("insufficient space in the output buffer to store the signature");
        System.arraycopy(sig, 0, outbuf, offset, sig.length);
        return sig.length;
    }

    protected abstract boolean engineVerify(byte[] sigBytes) throws SignatureException;

    protected boolean engineVerify(byte[] sigBytes, int offset, int length) throws SignatureException {
        byte[] b = new byte[length];
        System.arraycopy(sigBytes, offset, b, 0, length);
        return engineVerify(b);
    }

    @Deprecated
    protected abstract void engineSetParameter(String param, Object value) throws InvalidParameterException;

    protected void engineSetParameter(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
        throw new UnsupportedOperationException();
    }

    protected AlgorithmParameters engineGetParameters() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    protected abstract Object engineGetParameter(String param) throws InvalidParameterException;

    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) return super.clone();
        throw new CloneNotSupportedException();
    }
}
