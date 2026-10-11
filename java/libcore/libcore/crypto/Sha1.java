package libcore.crypto;

/** SHA-1 (FIPS 180-4). */
final class Sha1 extends BlockDigest {
    private int[] h = new int[5];
    private final int[] w = new int[80];

    Sha1() {
        super("SHA-1", 64, 20, true);
        resetState();
    }

    void resetState() {
        h[0] = 0x67452301;
        h[1] = 0xefcdab89;
        h[2] = 0x98badcfe;
        h[3] = 0x10325476;
        h[4] = 0xc3d2e1f0;
    }

    void cloneState() {
        h = h.clone();
    }

    void compress(byte[] block, int off) {
        int[] w = this.w;
        for (int i = 0; i < 16; i++) w[i] = beInt(block, off + 4 * i);
        for (int i = 16; i < 80; i++) w[i] = Integer.rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4];
        for (int i = 0; i < 80; i++) {
            int f, k;
            if (i < 20) {
                f = (b & c) | (~b & d);
                k = 0x5a827999;
            } else if (i < 40) {
                f = b ^ c ^ d;
                k = 0x6ed9eba1;
            } else if (i < 60) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8f1bbcdc;
            } else {
                f = b ^ c ^ d;
                k = 0xca62c1d6;
            }
            int t = Integer.rotateLeft(a, 5) + f + e + k + w[i];
            e = d;
            d = c;
            c = Integer.rotateLeft(b, 30);
            b = a;
            a = t;
        }
        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
        h[4] += e;
    }

    void output(byte[] out, int off) {
        for (int i = 0; i < 5; i++) putBeInt(h[i], out, off + 4 * i);
    }
}
