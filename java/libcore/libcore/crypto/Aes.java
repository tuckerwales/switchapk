package libcore.crypto;

import java.security.InvalidKeyException;

/**
 * The AES block cipher (FIPS 197) with 128, 192 and 256-bit keys. Uses the
 * usual four 256-entry round tables per direction, computed at class load.
 * Table lookups depend on secret data, so this is not constant time; there is
 * no AES instruction support to fall back on from Java here.
 */
final class Aes {
    static final int BLOCK_SIZE = 16;

    private static final byte[] SBOX = new byte[256];
    private static final byte[] INV_SBOX = new byte[256];
    private static final int[] TE0 = new int[256], TE1 = new int[256], TE2 = new int[256], TE3 = new int[256];
    private static final int[] TD0 = new int[256], TD1 = new int[256], TD2 = new int[256], TD3 = new int[256];
    private static final int[] RCON = {0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36};

    static {
        // Build the S-box from the multiplicative inverse in GF(2^8) and the affine map.
        int p = 1, q = 1;
        do {
            p = p ^ ((p << 1) & 0xff) ^ (((p & 0x80) != 0) ? 0x1b : 0);
            q ^= q << 1;
            q ^= q << 2;
            q ^= q << 4;
            q &= 0xff;
            if ((q & 0x80) != 0) q ^= 0x09;
            int x = q ^ rotl8(q, 1) ^ rotl8(q, 2) ^ rotl8(q, 3) ^ rotl8(q, 4);
            SBOX[p] = (byte) (x ^ 0x63);
        } while (p != 1);
        SBOX[0] = 0x63;
        for (int i = 0; i < 256; i++) INV_SBOX[SBOX[i] & 0xff] = (byte) i;
        for (int i = 0; i < 256; i++) {
            int s = SBOX[i] & 0xff;
            int s2 = xtime(s), s3 = s2 ^ s;
            int te = (s2 << 24) | (s << 16) | (s << 8) | s3;
            TE0[i] = te;
            TE1[i] = Integer.rotateRight(te, 8);
            TE2[i] = Integer.rotateRight(te, 16);
            TE3[i] = Integer.rotateRight(te, 24);
            int r = INV_SBOX[i] & 0xff;
            int td = (mul(r, 14) << 24) | (mul(r, 9) << 16) | (mul(r, 13) << 8) | mul(r, 11);
            TD0[i] = td;
            TD1[i] = Integer.rotateRight(td, 8);
            TD2[i] = Integer.rotateRight(td, 16);
            TD3[i] = Integer.rotateRight(td, 24);
        }
    }

    private static int rotl8(int x, int s) {
        return ((x << s) | (x >>> (8 - s))) & 0xff;
    }

    private static int xtime(int x) {
        return ((x << 1) ^ ((x & 0x80) != 0 ? 0x1b : 0)) & 0xff;
    }

    private static int mul(int a, int b) {
        int r = 0;
        while (b != 0) {
            if ((b & 1) != 0) r ^= a;
            a = xtime(a);
            b >>= 1;
        }
        return r;
    }

    private final int rounds;
    private final int[] enc;
    private final int[] dec;

    Aes(byte[] key) throws InvalidKeyException {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new InvalidKeyException("Invalid AES key length: " + (key == null ? 0 : key.length) + " bytes");
        }
        int nk = key.length / 4;
        rounds = nk + 6;
        int total = 4 * (rounds + 1);
        enc = new int[total];
        for (int i = 0; i < nk; i++) enc[i] = BlockDigest.beInt(key, 4 * i);
        for (int i = nk; i < total; i++) {
            int t = enc[i - 1];
            if (i % nk == 0) {
                t = subWord(Integer.rotateLeft(t, 8)) ^ (RCON[i / nk - 1] << 24);
            } else if (nk > 6 && i % nk == 4) {
                t = subWord(t);
            }
            enc[i] = enc[i - nk] ^ t;
        }
        // Equivalent inverse cipher key schedule: reversed round order, InvMixColumns on inner keys.
        dec = new int[total];
        for (int r = 0; r <= rounds; r++) {
            for (int c = 0; c < 4; c++) {
                int w = enc[4 * (rounds - r) + c];
                if (r > 0 && r < rounds) {
                    w = TD0[SBOX[w >>> 24] & 0xff] ^ TD1[SBOX[(w >>> 16) & 0xff] & 0xff]
                            ^ TD2[SBOX[(w >>> 8) & 0xff] & 0xff] ^ TD3[SBOX[w & 0xff] & 0xff];
                }
                dec[4 * r + c] = w;
            }
        }
    }

    private static int subWord(int w) {
        return ((SBOX[w >>> 24] & 0xff) << 24) | ((SBOX[(w >>> 16) & 0xff] & 0xff) << 16)
                | ((SBOX[(w >>> 8) & 0xff] & 0xff) << 8) | (SBOX[w & 0xff] & 0xff);
    }

    void encryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
        int[] k = enc;
        int s0 = BlockDigest.beInt(in, inOff) ^ k[0];
        int s1 = BlockDigest.beInt(in, inOff + 4) ^ k[1];
        int s2 = BlockDigest.beInt(in, inOff + 8) ^ k[2];
        int s3 = BlockDigest.beInt(in, inOff + 12) ^ k[3];
        int ki = 4;
        for (int r = 1; r < rounds; r++) {
            int t0 = TE0[s0 >>> 24] ^ TE1[(s1 >>> 16) & 0xff] ^ TE2[(s2 >>> 8) & 0xff] ^ TE3[s3 & 0xff] ^ k[ki];
            int t1 = TE0[s1 >>> 24] ^ TE1[(s2 >>> 16) & 0xff] ^ TE2[(s3 >>> 8) & 0xff] ^ TE3[s0 & 0xff] ^ k[ki + 1];
            int t2 = TE0[s2 >>> 24] ^ TE1[(s3 >>> 16) & 0xff] ^ TE2[(s0 >>> 8) & 0xff] ^ TE3[s1 & 0xff] ^ k[ki + 2];
            int t3 = TE0[s3 >>> 24] ^ TE1[(s0 >>> 16) & 0xff] ^ TE2[(s1 >>> 8) & 0xff] ^ TE3[s2 & 0xff] ^ k[ki + 3];
            s0 = t0;
            s1 = t1;
            s2 = t2;
            s3 = t3;
            ki += 4;
        }
        BlockDigest.putBeInt(lastRound(SBOX, s0, s1, s2, s3) ^ k[ki], out, outOff);
        BlockDigest.putBeInt(lastRound(SBOX, s1, s2, s3, s0) ^ k[ki + 1], out, outOff + 4);
        BlockDigest.putBeInt(lastRound(SBOX, s2, s3, s0, s1) ^ k[ki + 2], out, outOff + 8);
        BlockDigest.putBeInt(lastRound(SBOX, s3, s0, s1, s2) ^ k[ki + 3], out, outOff + 12);
    }

    void decryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
        int[] k = dec;
        int s0 = BlockDigest.beInt(in, inOff) ^ k[0];
        int s1 = BlockDigest.beInt(in, inOff + 4) ^ k[1];
        int s2 = BlockDigest.beInt(in, inOff + 8) ^ k[2];
        int s3 = BlockDigest.beInt(in, inOff + 12) ^ k[3];
        int ki = 4;
        for (int r = 1; r < rounds; r++) {
            int t0 = TD0[s0 >>> 24] ^ TD1[(s3 >>> 16) & 0xff] ^ TD2[(s2 >>> 8) & 0xff] ^ TD3[s1 & 0xff] ^ k[ki];
            int t1 = TD0[s1 >>> 24] ^ TD1[(s0 >>> 16) & 0xff] ^ TD2[(s3 >>> 8) & 0xff] ^ TD3[s2 & 0xff] ^ k[ki + 1];
            int t2 = TD0[s2 >>> 24] ^ TD1[(s1 >>> 16) & 0xff] ^ TD2[(s0 >>> 8) & 0xff] ^ TD3[s3 & 0xff] ^ k[ki + 2];
            int t3 = TD0[s3 >>> 24] ^ TD1[(s2 >>> 16) & 0xff] ^ TD2[(s1 >>> 8) & 0xff] ^ TD3[s0 & 0xff] ^ k[ki + 3];
            s0 = t0;
            s1 = t1;
            s2 = t2;
            s3 = t3;
            ki += 4;
        }
        BlockDigest.putBeInt(lastRound(INV_SBOX, s0, s3, s2, s1) ^ k[ki], out, outOff);
        BlockDigest.putBeInt(lastRound(INV_SBOX, s1, s0, s3, s2) ^ k[ki + 1], out, outOff + 4);
        BlockDigest.putBeInt(lastRound(INV_SBOX, s2, s1, s0, s3) ^ k[ki + 2], out, outOff + 8);
        BlockDigest.putBeInt(lastRound(INV_SBOX, s3, s2, s1, s0) ^ k[ki + 3], out, outOff + 12);
    }

    private static int lastRound(byte[] box, int a, int b, int c, int d) {
        return ((box[a >>> 24] & 0xff) << 24) | ((box[(b >>> 16) & 0xff] & 0xff) << 16)
                | ((box[(c >>> 8) & 0xff] & 0xff) << 8) | (box[d & 0xff] & 0xff);
    }
}
