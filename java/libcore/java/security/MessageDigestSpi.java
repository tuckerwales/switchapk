package java.security;

import java.nio.ByteBuffer;

public abstract class MessageDigestSpi {
    public MessageDigestSpi() {
    }

    protected int engineGetDigestLength() {
        return 0;
    }

    protected abstract void engineUpdate(byte input);

    protected abstract void engineUpdate(byte[] input, int offset, int len);

    protected void engineUpdate(ByteBuffer input) {
        if (!input.hasRemaining()) return;
        if (input.hasArray()) {
            int pos = input.position();
            engineUpdate(input.array(), input.arrayOffset() + pos, input.remaining());
            input.position(input.limit());
            return;
        }
        byte[] tmp = new byte[Math.min(input.remaining(), 4096)];
        while (input.hasRemaining()) {
            int n = Math.min(tmp.length, input.remaining());
            input.get(tmp, 0, n);
            engineUpdate(tmp, 0, n);
        }
    }

    protected abstract byte[] engineDigest();

    protected int engineDigest(byte[] buf, int offset, int len) throws DigestException {
        byte[] digest = engineDigest();
        if (len < digest.length) throw new DigestException("partial digests not returned");
        if (buf.length - offset < digest.length) throw new DigestException("insufficient space in the output buffer to store the digest");
        System.arraycopy(digest, 0, buf, offset, digest.length);
        return digest.length;
    }

    protected abstract void engineReset();

    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) return super.clone();
        throw new CloneNotSupportedException();
    }
}
