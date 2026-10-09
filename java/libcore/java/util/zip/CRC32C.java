package java.util.zip;

import java.nio.ByteBuffer;

/** CRC-32C (Castagnoli, reflected polynomial 0x82F63B78), table driven in Java (zlib has no CRC-32C). */
public final class CRC32C implements Checksum {
    private static final int[] TABLE = new int[256];

    static {
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? (c >>> 1) ^ 0x82F63B78 : c >>> 1;
            }
            TABLE[n] = c;
        }
    }

    private int crc = 0xFFFFFFFF;

    public CRC32C() {
    }

    public void update(int b) {
        crc = (crc >>> 8) ^ TABLE[(crc ^ b) & 0xff];
    }

    public void update(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        int c = crc;
        for (int i = off, end = off + len; i < end; i++) {
            c = (c >>> 8) ^ TABLE[(c ^ b[i]) & 0xff];
        }
        crc = c;
    }

    public void update(ByteBuffer buffer) {
        Checksum.super.update(buffer);
    }

    public void reset() {
        crc = 0xFFFFFFFF;
    }

    public long getValue() {
        return (~crc) & 0xFFFFFFFFL;
    }
}
