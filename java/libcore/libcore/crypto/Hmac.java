package libcore.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import javax.crypto.MacSpi;

/** HMAC (RFC 2104) over one of the built-in digests. */
final class Hmac extends MacSpi implements Cloneable {
    private final int blockSize;
    private MessageDigest inner;
    private MessageDigest outer;
    private byte[] ipad;
    private byte[] opad;
    private boolean first = true;

    Hmac(MessageDigest digest, int blockSize) {
        this.blockSize = blockSize;
        this.inner = digest;
        try {
            this.outer = (MessageDigest) digest.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    protected int engineGetMacLength() {
        return inner.getDigestLength();
    }

    protected void engineInit(Key key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) throw new InvalidAlgorithmParameterException("HMAC does not use parameters");
        initRaw(Keys.raw(key, "sign", null, null, inner.getAlgorithm()));
    }

    /** Keys the MAC with raw bytes (also the empty key PBKDF2 can need). Clears k. */
    void initRaw(byte[] k) {
        if (k.length > blockSize) {
            byte[] hashed = inner.digest(k);
            Arrays.fill(k, (byte) 0);
            k = hashed;
        }
        ipad = new byte[blockSize];
        opad = new byte[blockSize];
        for (int i = 0; i < blockSize; i++) {
            byte b = i < k.length ? k[i] : 0;
            ipad[i] = (byte) (b ^ 0x36);
            opad[i] = (byte) (b ^ 0x5c);
        }
        Arrays.fill(k, (byte) 0);
        engineReset();
    }

    private void start() {
        if (first) {
            inner.update(ipad);
            first = false;
        }
    }

    protected void engineUpdate(byte input) {
        start();
        inner.update(input);
    }

    protected void engineUpdate(byte[] input, int offset, int len) {
        start();
        inner.update(input, offset, len);
    }

    protected byte[] engineDoFinal() {
        start();
        byte[] tmp = inner.digest();
        outer.update(opad);
        outer.update(tmp);
        byte[] mac = outer.digest();
        first = true;
        return mac;
    }

    protected void engineReset() {
        inner.reset();
        outer.reset();
        first = true;
    }

    public Object clone() throws CloneNotSupportedException {
        Hmac h = (Hmac) super.clone();
        h.inner = (MessageDigest) inner.clone();
        h.outer = (MessageDigest) outer.clone();
        return h;
    }
}
