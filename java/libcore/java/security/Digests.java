package java.security;

final class Digests {
    private Digests() {
    }

    private static byte[] pad(byte[] msg, boolean bigEndianLength) {
        long bitLen = (long) msg.length * 8;
        int padLen = (int) ((56 - (msg.length + 1) % 64 + 64) % 64);
        byte[] out = new byte[msg.length + 1 + padLen + 8];
        System.arraycopy(msg, 0, out, 0, msg.length);
        out[msg.length] = (byte) 0x80;
        for (int i = 0; i < 8; i++) {
            int shift = bigEndianLength ? (7 - i) * 8 : i * 8;
            out[out.length - 8 + i] = (byte) (bitLen >>> shift);
        }
        return out;
    }

    private static final int[] MD5_S = {7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14,
        20, 5, 9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15,
        21, 6, 10, 15, 21, 6, 10, 15, 21};
    private static final int[] MD5_K = new int[64];

    static {
        for (int i = 0; i < 64; i++) {
            MD5_K[i] = (int) (long) (Math.abs(Math.sin(i + 1)) * 4294967296.0);
        }
    }

    static byte[] md5(byte[] msg) {
        byte[] m = pad(msg, false);
        int a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
        int[] w = new int[16];
        for (int off = 0; off < m.length; off += 64) {
            for (int i = 0; i < 16; i++) {
                w[i] = (m[off + i * 4] & 0xff) | ((m[off + i * 4 + 1] & 0xff) << 8) | ((m[off + i * 4 + 2] & 0xff) << 16)
                        | ((m[off + i * 4 + 3] & 0xff) << 24);
            }
            int a = a0, b = b0, c = c0, d = d0;
            for (int i = 0; i < 64; i++) {
                int f, g;
                if (i < 16) {
                    f = (b & c) | (~b & d);
                    g = i;
                } else if (i < 32) {
                    f = (d & b) | (~d & c);
                    g = (5 * i + 1) % 16;
                } else if (i < 48) {
                    f = b ^ c ^ d;
                    g = (3 * i + 5) % 16;
                } else {
                    f = c ^ (b | ~d);
                    g = (7 * i) % 16;
                }
                int tmp = d;
                d = c;
                c = b;
                b = b + Integer.rotateLeft(a + f + MD5_K[i] + w[g], MD5_S[i]);
                a = tmp;
            }
            a0 += a;
            b0 += b;
            c0 += c;
            d0 += d;
        }
        byte[] out = new byte[16];
        int[] h = {a0, b0, c0, d0};
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                out[i * 4 + j] = (byte) (h[i] >>> (8 * j));
            }
        }
        return out;
    }

    static byte[] sha1(byte[] msg) {
        byte[] m = pad(msg, true);
        int h0 = 0x67452301, h1 = 0xEFCDAB89, h2 = 0x98BADCFE, h3 = 0x10325476, h4 = 0xC3D2E1F0;
        int[] w = new int[80];
        for (int off = 0; off < m.length; off += 64) {
            for (int i = 0; i < 16; i++) {
                w[i] = ((m[off + i * 4] & 0xff) << 24) | ((m[off + i * 4 + 1] & 0xff) << 16) | ((m[off + i * 4 + 2] & 0xff) << 8)
                        | (m[off + i * 4 + 3] & 0xff);
            }
            for (int i = 16; i < 80; i++) {
                w[i] = Integer.rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
            }
            int a = h0, b = h1, c = h2, d = h3, e = h4;
            for (int i = 0; i < 80; i++) {
                int f, k;
                if (i < 20) {
                    f = (b & c) | (~b & d);
                    k = 0x5A827999;
                } else if (i < 40) {
                    f = b ^ c ^ d;
                    k = 0x6ED9EBA1;
                } else if (i < 60) {
                    f = (b & c) | (b & d) | (c & d);
                    k = 0x8F1BBCDC;
                } else {
                    f = b ^ c ^ d;
                    k = 0xCA62C1D6;
                }
                int temp = Integer.rotateLeft(a, 5) + f + e + k + w[i];
                e = d;
                d = c;
                c = Integer.rotateLeft(b, 30);
                b = a;
                a = temp;
            }
            h0 += a;
            h1 += b;
            h2 += c;
            h3 += d;
            h4 += e;
        }
        return toBytes(new int[] {h0, h1, h2, h3, h4});
    }

    private static final int[] K256 = {0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4,
        0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152,
        0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138,
        0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70,
        0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa,
        0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

    static byte[] sha256(byte[] msg) {
        byte[] m = pad(msg, true);
        int[] h = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
        int[] w = new int[64];
        for (int off = 0; off < m.length; off += 64) {
            for (int i = 0; i < 16; i++) {
                w[i] = ((m[off + i * 4] & 0xff) << 24) | ((m[off + i * 4 + 1] & 0xff) << 16) | ((m[off + i * 4 + 2] & 0xff) << 8)
                        | (m[off + i * 4 + 3] & 0xff);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
            for (int i = 0; i < 64; i++) {
                int S1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
                int ch = (e & f) ^ (~e & g);
                int temp1 = hh + S1 + ch + K256[i] + w[i];
                int S0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
                int maj = (a & b) ^ (a & c) ^ (b & c);
                int temp2 = S0 + maj;
                hh = g;
                g = f;
                f = e;
                e = d + temp1;
                d = c;
                c = b;
                b = a;
                a = temp1 + temp2;
            }
            h[0] += a;
            h[1] += b;
            h[2] += c;
            h[3] += d;
            h[4] += e;
            h[5] += f;
            h[6] += g;
            h[7] += hh;
        }
        return toBytes(h);
    }

    private static byte[] toBytes(int[] h) {
        byte[] out = new byte[h.length * 4];
        for (int i = 0; i < h.length; i++) {
            out[i * 4] = (byte) (h[i] >>> 24);
            out[i * 4 + 1] = (byte) (h[i] >>> 16);
            out[i * 4 + 2] = (byte) (h[i] >>> 8);
            out[i * 4 + 3] = (byte) h[i];
        }
        return out;
    }
}
