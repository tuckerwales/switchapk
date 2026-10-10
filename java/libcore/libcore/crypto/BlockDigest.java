package libcore.crypto;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * Merkle-Damgard hashes with a 64- or 128-byte block: buffers partial blocks,
 * pads with 0x80, zeros and the bit length, and leaves the compression
 * function and state to subclasses.
 */
abstract class BlockDigest extends MessageDigest implements Cloneable {
    private final int blockSize;
    private final int digestLength;
    private final boolean bigEndianLength;
    private byte[] buffer;
    private int bufferLen;
    private long byteCount;

    BlockDigest(String algorithm, int blockSize, int digestLength, boolean bigEndianLength) {
        super(algorithm);
        this.blockSize = blockSize;
        this.digestLength = digestLength;
        this.bigEndianLength = bigEndianLength;
        this.buffer = new byte[blockSize];
    }

    abstract void compress(byte[] block, int off);

    abstract void resetState();

    /** Writes the first digestLength bytes of the state to out. */
    abstract void output(byte[] out, int off);

    /** Called on a shallow clone: replaces the state array with a copy. */
    abstract void cloneState();

    protected final int engineGetDigestLength() {
        return digestLength;
    }

    protected final void engineUpdate(byte input) {
        buffer[bufferLen++] = input;
        byteCount++;
        if (bufferLen == blockSize) {
            compress(buffer, 0);
            bufferLen = 0;
        }
    }

    protected final void engineUpdate(byte[] input, int off, int len) {
        byteCount += len;
        if (bufferLen > 0) {
            int n = Math.min(len, blockSize - bufferLen);
            System.arraycopy(input, off, buffer, bufferLen, n);
            bufferLen += n;
            off += n;
            len -= n;
            if (bufferLen < blockSize) return;
            compress(buffer, 0);
            bufferLen = 0;
        }
        while (len >= blockSize) {
            compress(input, off);
            off += blockSize;
            len -= blockSize;
        }
        if (len > 0) {
            System.arraycopy(input, off, buffer, 0, len);
            bufferLen = len;
        }
    }

    protected final byte[] engineDigest() {
        byte[] out = new byte[digestLength];
        finish(out, 0);
        return out;
    }

    protected final int engineDigest(byte[] buf, int offset, int len) throws DigestException {
        if (len < digestLength) throw new DigestException("partial digests not returned");
        if (buf.length - offset < digestLength) throw new DigestException("insufficient space in the output buffer to store the digest");
        finish(buf, offset);
        return digestLength;
    }

    private void finish(byte[] out, int off) {
        long bits = byteCount << 3;
        int lengthBytes = blockSize == 128 ? 16 : 8;
        buffer[bufferLen++] = (byte) 0x80;
        if (bufferLen > blockSize - lengthBytes) {
            while (bufferLen < blockSize) buffer[bufferLen++] = 0;
            compress(buffer, 0);
            bufferLen = 0;
        }
        while (bufferLen < blockSize - 8) buffer[bufferLen++] = 0;
        for (int i = 0; i < 8; i++) {
            int shift = bigEndianLength ? 56 - 8 * i : 8 * i;
            buffer[blockSize - 8 + i] = (byte) (bits >>> shift);
        }
        compress(buffer, 0);
        output(out, off);
        engineReset();
    }

    protected final void engineReset() {
        bufferLen = 0;
        byteCount = 0;
        resetState();
    }

    public Object clone() throws CloneNotSupportedException {
        BlockDigest d = (BlockDigest) super.clone();
        d.buffer = buffer.clone();
        d.cloneState();
        return d;
    }

    static int beInt(byte[] b, int off) {
        return (b[off] << 24) | ((b[off + 1] & 0xff) << 16) | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    static int leInt(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16) | (b[off + 3] << 24);
    }

    static long beLong(byte[] b, int off) {
        return ((long) beInt(b, off) << 32) | (beInt(b, off + 4) & 0xffffffffL);
    }

    static void putBeInt(int v, byte[] b, int off) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    static void putLeInt(int v, byte[] b, int off) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }
}
