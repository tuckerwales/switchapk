package libcore.crypto;

/** MD5 (RFC 1321). */
final class Md5 extends BlockDigest {
    private static final int[] S = {7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21};
    private static final int[] K = new int[64];

    static {
        for (int i = 0; i < 64; i++) K[i] = (int) (long) Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296.0);
    }

    private int[] h = new int[4];
    private final int[] x = new int[16];

    Md5() {
        super("MD5", 64, 16, false);
        resetState();
    }

    void resetState() {
        h[0] = 0x67452301;
        h[1] = 0xefcdab89;
        h[2] = 0x98badcfe;
        h[3] = 0x10325476;
    }

    void cloneState() {
        h = h.clone();
    }

    void compress(byte[] block, int off) {
        for (int i = 0; i < 16; i++) x[i] = leInt(block, off + 4 * i);
        int a = h[0], b = h[1], c = h[2], d = h[3];
        for (int i = 0; i < 64; i++) {
            int f, g;
            switch (i >> 4) {
                case 0: f = (b & c) | (~b & d); g = i; break;
                case 1: f = (d & b) | (~d & c); g = (5 * i + 1) & 15; break;
                case 2: f = b ^ c ^ d; g = (3 * i + 5) & 15; break;
                default: f = c ^ (b | ~d); g = (7 * i) & 15; break;
            }
            int tmp = d;
            d = c;
            c = b;
            b = b + Integer.rotateLeft(a + f + K[i] + x[g], S[((i >> 4) << 2) | (i & 3)]);
            a = tmp;
        }
        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
    }

    void output(byte[] out, int off) {
        for (int i = 0; i < 4; i++) putLeInt(h[i], out, off + 4 * i);
    }
}
