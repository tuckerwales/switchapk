package libcore.crypto;

import java.io.ByteArrayOutputStream;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * AES in ECB, CBC, CTR and GCM modes with NoPadding or PKCS5Padding
 * (PKCS7Padding is the same thing for a 16-byte block). Behaviour follows the
 * JDK and Conscrypt: "AES" alone is AES/ECB/PKCS5Padding; encryption without
 * parameters picks a random IV (12 bytes for GCM); decryption needs them; GCM
 * decryption releases nothing until the tag has been checked in doFinal, and
 * a GCM key and IV pair cannot be used to encrypt twice.
 */
public final class AesCipher extends CipherSpi {
    static final int ECB = 0, CBC = 1, CTR = 2, GCM = 3;

    private int mode = ECB;
    private boolean padding = true;
    private boolean modeFixed;
    private boolean paddingFixed;

    private Aes aes;
    private byte[] keyBytes;
    private boolean encrypt;
    private byte[] iv;
    private int tagLen = 16;

    // CBC: previous ciphertext block. CTR: counter block. GCM: counter block.
    private byte[] chain = new byte[16];
    private final byte[] block = new byte[16];
    private final byte[] tmp = new byte[16];
    private int blockLen;

    // CTR keystream position.
    private final byte[] keystream = new byte[16];
    private int keystreamPos = 16;

    // GCM state.
    private Ghash ghash;
    private byte[] j0;
    private long aadLen;
    private long dataLen;
    private boolean aadDone;
    private ByteArrayOutputStream gcmDecryptBuffer;
    private ByteArrayOutputStream aadBuffer;
    private boolean needsReinit;
    private byte[] lastEncryptKey;
    private byte[] lastEncryptIv;

    public AesCipher() {
    }

    /** For the fixed-transformation registrations ("AES/GCM/NoPadding" and so on). */
    AesCipher(int mode, boolean padding) {
        this.mode = mode;
        this.padding = padding;
        this.modeFixed = true;
        this.paddingFixed = true;
    }

    protected void engineSetMode(String m) throws NoSuchAlgorithmException {
        String u = m.toUpperCase(Locale.ENGLISH);
        int want;
        if (u.equals("ECB")) want = ECB;
        else if (u.equals("CBC")) want = CBC;
        else if (u.equals("CTR")) want = CTR;
        else if (u.equals("GCM")) want = GCM;
        else throw new NoSuchAlgorithmException("Unsupported mode " + m);
        if (modeFixed && want != mode) throw new NoSuchAlgorithmException("Unsupported mode " + m);
        mode = want;
        if (!paddingFixed && (mode == CTR || mode == GCM)) padding = false;
    }

    protected void engineSetPadding(String p) throws NoSuchPaddingException {
        String u = p.toUpperCase(Locale.ENGLISH);
        boolean want;
        if (u.equals("NOPADDING")) want = false;
        else if (u.equals("PKCS5PADDING") || u.equals("PKCS7PADDING")) want = true;
        else throw new NoSuchPaddingException("Unsupported padding " + p);
        if (paddingFixed && want != padding) throw new NoSuchPaddingException("Unsupported padding " + p);
        if (want && (mode == CTR || mode == GCM)) throw new NoSuchPaddingException(p + " cannot be used with " + modeName());
        padding = want;
    }

    private String modeName() {
        switch (mode) {
            case CBC: return "CBC";
            case CTR: return "CTR";
            case GCM: return "GCM";
            default: return "ECB";
        }
    }

    protected int engineGetBlockSize() {
        return 16;
    }

    protected int engineGetOutputSize(int inputLen) {
        long total = (long) inputLen + blockLen;
        switch (mode) {
            case CTR:
                return inputLen;
            case GCM:
                if (encrypt) return (int) (total + tagLen);
                long buffered = gcmDecryptBuffer != null ? gcmDecryptBuffer.size() : 0;
                return (int) Math.max(0, buffered + inputLen - tagLen);
            default:
                if (encrypt && padding) return (int) ((total / 16 + 1) * 16);
                return (int) total;
        }
    }

    protected byte[] engineGetIV() {
        return iv == null ? null : iv.clone();
    }

    protected AlgorithmParameters engineGetParameters() {
        if (iv == null) return null;
        try {
            AlgorithmParameters p;
            if (mode == GCM) {
                p = AlgorithmParameters.getInstance("GCM");
                p.init(new GCMParameterSpec(tagLen * 8, iv));
            } else {
                p = AlgorithmParameters.getInstance("AES");
                p.init(new IvParameterSpec(iv));
            }
            return p;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidParameterSpecException e) {
            throw new RuntimeException(e);
        }
    }

    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e.getMessage(), e);
        }
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        AlgorithmParameterSpec spec = null;
        if (params != null) {
            try {
                spec = mode == GCM ? params.getParameterSpec(GCMParameterSpec.class)
                        : params.getParameterSpec(IvParameterSpec.class);
            } catch (InvalidParameterSpecException e) {
                throw new InvalidAlgorithmParameterException("Wrong parameter type: " + (mode == GCM ? "GCM" : "IV") + " expected");
            }
        }
        engineInit(opmode, key, spec, random);
    }

    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        boolean enc = opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE;
        byte[] k = Keys.raw(key, enc ? "encrypt" : "decrypt", modeName(), padding ? "PKCS7Padding" : "NoPadding", null);
        Aes cipher = new Aes(k);
        byte[] newIv = null;
        int newTagLen = 16;
        if (mode == ECB) {
            if (params != null) throw new InvalidAlgorithmParameterException("ECB mode cannot use IV");
        } else if (mode == GCM) {
            if (params instanceof GCMParameterSpec) {
                GCMParameterSpec g = (GCMParameterSpec) params;
                int bits = g.getTLen();
                if (bits < 96 || bits > 128 || (bits & 7) != 0) {
                    throw new InvalidAlgorithmParameterException("Unsupported TLen value.  Must be one of {128, 120, 112, 104, 96}");
                }
                newTagLen = bits / 8;
                newIv = g.getIV();
            } else if (params instanceof IvParameterSpec) {
                newIv = ((IvParameterSpec) params).getIV();
            } else if (params != null) {
                throw new InvalidAlgorithmParameterException("Unsupported parameter: " + params);
            }
            if (newIv == null) {
                if (!enc) throw new InvalidKeyException("Parameters missing");
                newIv = new byte[12];
                (random != null ? random : new SecureRandom()).nextBytes(newIv);
            }
            if (newIv.length == 0) throw new InvalidAlgorithmParameterException("IV is empty");
            if (enc && lastEncryptIv != null && Arrays.equals(lastEncryptIv, newIv) && Arrays.equals(lastEncryptKey, k)) {
                throw new InvalidAlgorithmParameterException("Cannot reuse iv for GCM encryption");
            }
        } else {
            if (params instanceof IvParameterSpec) {
                newIv = ((IvParameterSpec) params).getIV();
                if (newIv.length != 16) throw new InvalidAlgorithmParameterException("Wrong IV length: must be 16 bytes long");
            } else if (params != null) {
                throw new InvalidAlgorithmParameterException("Wrong parameter type: IV expected");
            } else {
                if (!enc) throw new InvalidKeyException("Parameters missing");
                newIv = new byte[16];
                (random != null ? random : new SecureRandom()).nextBytes(newIv);
            }
        }
        aes = cipher;
        keyBytes = k;
        encrypt = enc;
        iv = newIv;
        tagLen = newTagLen;
        if (mode == GCM && enc) {
            lastEncryptKey = k.clone();
            lastEncryptIv = newIv.clone();
        }
        reset();
    }

    /** Returns to the state just after init, as doFinal requires. */
    private void reset() {
        blockLen = 0;
        keystreamPos = 16;
        needsReinit = false;
        if (mode == CBC || mode == CTR) chain = iv.clone();
        if (mode == GCM) {
            byte[] h = new byte[16];
            aes.encryptBlock(h, 0, h, 0);
            ghash = new Ghash(h);
            if (iv.length == 12) {
                j0 = new byte[16];
                System.arraycopy(iv, 0, j0, 0, 12);
                j0[15] = 1;
            } else {
                Ghash g = new Ghash(h);
                g.update(iv, 0, iv.length);
                g.pad();
                byte[] lenBlock = new byte[16];
                long bits = (long) iv.length * 8;
                for (int i = 0; i < 8; i++) lenBlock[8 + i] = (byte) (bits >>> (56 - 8 * i));
                g.update(lenBlock, 0, 16);
                j0 = g.digest();
            }
            chain = j0.clone();
            inc32(chain);
            aadLen = 0;
            dataLen = 0;
            aadDone = false;
            gcmDecryptBuffer = encrypt ? null : new ByteArrayOutputStream();
            aadBuffer = null;
        }
    }

    private static void inc32(byte[] counter) {
        for (int i = 15; i >= 12; i--) {
            if (++counter[i] != 0) break;
        }
    }

    private static void incAll(byte[] counter) {
        for (int i = 15; i >= 0; i--) {
            if (++counter[i] != 0) break;
        }
    }

    private void checkInit() {
        if (aes == null) throw new IllegalStateException("Cipher not initialized");
        if (needsReinit) {
            throw new IllegalStateException("Cannot reuse iv for GCM encryption");
        }
    }

    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        checkInit();
        if (mode != GCM) throw new IllegalStateException("AAD is only supported in GCM mode");
        if (aadDone) throw new IllegalStateException("Update has been called; no more AAD data");
        ghash.update(src, offset, len);
        aadLen += len;
    }

    private void finishAad() {
        if (!aadDone) {
            ghash.pad();
            aadDone = true;
        }
    }

    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        checkInit();
        byte[] out = new byte[updateOutputSize(inputLen)];
        int n;
        try {
            n = engineUpdate(input, inputOffset, inputLen, out, 0);
        } catch (ShortBufferException e) {
            throw new java.security.ProviderException(e);
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private int updateOutputSize(int inputLen) {
        switch (mode) {
            case CTR:
                return inputLen;
            case GCM:
                return encrypt ? inputLen : 0;
            default:
                return blockLen + inputLen + 16;
        }
    }

    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException {
        checkInit();
        switch (mode) {
            case CTR:
                if (output.length - outputOffset < inputLen) throw new ShortBufferException("Output buffer too small");
                ctr(input, inputOffset, inputLen, output, outputOffset);
                return inputLen;
            case GCM:
                finishAad();
                if (!encrypt) {
                    gcmDecryptBuffer.write(input, inputOffset, inputLen);
                    return 0;
                }
                if (output.length - outputOffset < inputLen) throw new ShortBufferException("Output buffer too small");
                gcmCtr(input, inputOffset, inputLen, output, outputOffset);
                ghash.update(output, outputOffset, inputLen);
                dataLen += inputLen;
                return inputLen;
            default:
                return blocks(input, inputOffset, inputLen, output, outputOffset);
        }
    }

    /** ECB and CBC: processes whole blocks, keeping the last one back when decrypting with padding. */
    private int blocks(byte[] in, int off, int len, byte[] out, int outOff) throws ShortBufferException {
        int total = blockLen + len;
        int keep = total % 16;
        if (keep == 0 && !encrypt && padding && total > 0) keep = 16;
        int process = total - keep;
        if (out.length - outOff < process) throw new ShortBufferException("Output buffer too small");
        // Copy input aside first: output may overlap it.
        byte[] src = new byte[len];
        System.arraycopy(in, off, src, 0, len);
        int si = 0;
        int written = 0;
        while (written < process) {
            int need = 16 - blockLen;
            System.arraycopy(src, si, block, blockLen, need);
            si += need;
            blockLen = 0;
            processBlock(block, out, outOff + written);
            written += 16;
        }
        System.arraycopy(src, si, block, blockLen, len - si);
        blockLen += len - si;
        return written;
    }

    private void processBlock(byte[] b, byte[] out, int outOff) {
        if (mode == ECB) {
            if (encrypt) aes.encryptBlock(b, 0, out, outOff);
            else aes.decryptBlock(b, 0, out, outOff);
            return;
        }
        if (encrypt) {
            for (int i = 0; i < 16; i++) tmp[i] = (byte) (b[i] ^ chain[i]);
            aes.encryptBlock(tmp, 0, out, outOff);
            System.arraycopy(out, outOff, chain, 0, 16);
        } else {
            byte[] c = b.clone();
            aes.decryptBlock(b, 0, tmp, 0);
            for (int i = 0; i < 16; i++) out[outOff + i] = (byte) (tmp[i] ^ chain[i]);
            chain = c;
        }
    }

    private void ctr(byte[] in, int off, int len, byte[] out, int outOff) {
        for (int i = 0; i < len; i++) {
            if (keystreamPos == 16) {
                aes.encryptBlock(chain, 0, keystream, 0);
                incAll(chain);
                keystreamPos = 0;
            }
            out[outOff + i] = (byte) (in[off + i] ^ keystream[keystreamPos++]);
        }
    }

    private void gcmCtr(byte[] in, int off, int len, byte[] out, int outOff) {
        for (int i = 0; i < len; i++) {
            if (keystreamPos == 16) {
                aes.encryptBlock(chain, 0, keystream, 0);
                inc32(chain);
                keystreamPos = 0;
            }
            out[outOff + i] = (byte) (in[off + i] ^ keystream[keystreamPos++]);
        }
    }

    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        checkInit();
        byte[] out = new byte[Math.max(engineGetOutputSize(input == null ? 0 : inputLen), 0) + 16];
        int n;
        try {
            n = engineDoFinal(input, inputOffset, input == null ? 0 : inputLen, out, 0);
        } catch (ShortBufferException e) {
            throw new java.security.ProviderException(e);
        }
        return Arrays.copyOf(out, n);
    }

    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        checkInit();
        if (input == null) inputLen = 0;
        int needed = engineGetOutputSize(inputLen);
        if (mode != GCM && !(mode != CTR && !encrypt && padding) && output.length - outputOffset < needed) {
            throw new ShortBufferException("Output buffer must be (at least) " + needed + " bytes long");
        }
        try {
            switch (mode) {
                case CTR: {
                    if (inputLen > 0) ctr(input, inputOffset, inputLen, output, outputOffset);
                    return inputLen;
                }
                case GCM:
                    return gcmFinal(input, inputOffset, inputLen, output, outputOffset);
                default:
                    return blocksFinal(input, inputOffset, inputLen, output, outputOffset);
            }
        } finally {
            if (!(mode == GCM && encrypt)) reset();
        }
    }

    private int blocksFinal(byte[] in, int off, int len, byte[] out, int outOff)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        byte[] staging = new byte[blockLen + len + 16];
        int n = len > 0 ? blocks(in, off, len, staging, 0) : 0;
        if (encrypt) {
            if (padding) {
                int pad = 16 - blockLen;
                for (int i = blockLen; i < 16; i++) block[i] = (byte) pad;
                blockLen = 0;
                processBlock(block, staging, n);
                n += 16;
            } else if (blockLen != 0) {
                throw new IllegalBlockSizeException("Input length not multiple of 16 bytes");
            }
        } else {
            if (padding) {
                if (blockLen != 16) {
                    throw new IllegalBlockSizeException("Input length must be multiple of 16 when decrypting with padded cipher");
                }
                blockLen = 0;
                byte[] last = new byte[16];
                processBlock(block, last, 0);
                int pad = last[15] & 0xff;
                boolean bad = pad < 1 || pad > 16;
                for (int i = 16 - (bad ? 1 : pad); i < 16; i++) bad |= (last[i] & 0xff) != pad;
                if (bad) throw new BadPaddingException("Given final block not properly padded. Such issues can arise if a bad key is used during decryption.");
                System.arraycopy(last, 0, staging, n, 16 - pad);
                n += 16 - pad;
            } else if (blockLen != 0) {
                throw new IllegalBlockSizeException("Input length not multiple of 16 bytes");
            }
        }
        if (out.length - outOff < n) throw new ShortBufferException("Output buffer must be (at least) " + n + " bytes long");
        System.arraycopy(staging, 0, out, outOff, n);
        return n;
    }

    private byte[] tag() {
        ghash.pad();
        byte[] lenBlock = new byte[16];
        long a = aadLen * 8, c = dataLen * 8;
        for (int i = 0; i < 8; i++) {
            lenBlock[i] = (byte) (a >>> (56 - 8 * i));
            lenBlock[8 + i] = (byte) (c >>> (56 - 8 * i));
        }
        ghash.update(lenBlock, 0, 16);
        byte[] s = ghash.digest();
        byte[] ek = new byte[16];
        aes.encryptBlock(j0, 0, ek, 0);
        for (int i = 0; i < 16; i++) s[i] ^= ek[i];
        return s;
    }

    private int gcmFinal(byte[] in, int off, int len, byte[] out, int outOff)
            throws ShortBufferException, AEADBadTagException {
        finishAad();
        if (encrypt) {
            if (out.length - outOff < len + tagLen) throw new ShortBufferException("Output buffer too small");
            int n = 0;
            if (len > 0) {
                gcmCtr(in, off, len, out, outOff);
                ghash.update(out, outOff, len);
                dataLen += len;
                n = len;
            }
            byte[] t = tag();
            System.arraycopy(t, 0, out, outOff + n, tagLen);
            needsReinit = true;
            return n + tagLen;
        }
        if (len > 0) gcmDecryptBuffer.write(in, off, len);
        byte[] all = gcmDecryptBuffer.toByteArray();
        if (all.length < tagLen) throw new AEADBadTagException("Input data too short to contain an expected tag length of " + tagLen + "bytes");
        int clen = all.length - tagLen;
        if (out.length - outOff < clen) {
            throw new ShortBufferException("Output buffer too small");
        }
        ghash.update(all, 0, clen);
        dataLen = clen;
        byte[] t = tag();
        int diff = 0;
        for (int i = 0; i < tagLen; i++) diff |= t[i] ^ all[clen + i];
        if (diff != 0) throw new AEADBadTagException("Tag mismatch");
        gcmCtr(all, 0, clen, out, outOff);
        return clen;
    }

    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        byte[] k = Keys.raw(key, "size", null, null, null);
        if (k.length != 16 && k.length != 24 && k.length != 32) throw new InvalidKeyException("Invalid AES key length: " + k.length + " bytes");
        return k.length * 8;
    }

    /** GHASH (NIST SP 800-38D) with Shoup's 4-bit tables. */
    static final class Ghash {
        private static final long[] LAST4 = {
            0x0000, 0x1c20, 0x3840, 0x2460, 0x7080, 0x6ca0, 0x48c0, 0x54e0,
            0xe100, 0xfd20, 0xd940, 0xc560, 0x9180, 0x8da0, 0xa9c0, 0xb5e0,
        };
        private final long[] hh = new long[16];
        private final long[] hl = new long[16];
        private long yh, yl;
        private final byte[] buf = new byte[16];
        private int bufLen;

        Ghash(byte[] h) {
            long vh = BlockDigest.beLong(h, 0), vl = BlockDigest.beLong(h, 8);
            hh[8] = vh;
            hl[8] = vl;
            for (int i = 4; i > 0; i >>= 1) {
                long t = (vl & 1) != 0 ? 0xe1000000L << 32 : 0;
                vl = (vh << 63) | (vl >>> 1);
                vh = (vh >>> 1) ^ t;
                hh[i] = vh;
                hl[i] = vl;
            }
            for (int i = 2; i <= 8; i *= 2) {
                vh = hh[i];
                vl = hl[i];
                for (int j = 1; j < i; j++) {
                    hh[i + j] = vh ^ hh[j];
                    hl[i + j] = vl ^ hl[j];
                }
            }
        }

        private void mult(byte[] x) {
            int lo = x[15] & 0xf;
            long zh = hh[lo], zl = hl[lo];
            for (int i = 15; i >= 0; i--) {
                lo = x[i] & 0xf;
                int hi = (x[i] >> 4) & 0xf;
                if (i != 15) {
                    int rem = (int) (zl & 0xf);
                    zl = (zh << 60) | (zl >>> 4);
                    zh = (zh >>> 4) ^ (LAST4[rem] << 48);
                    zh ^= hh[lo];
                    zl ^= hl[lo];
                }
                int rem = (int) (zl & 0xf);
                zl = (zh << 60) | (zl >>> 4);
                zh = (zh >>> 4) ^ (LAST4[rem] << 48);
                zh ^= hh[hi];
                zl ^= hl[hi];
            }
            yh = zh;
            yl = zl;
        }

        private void block(byte[] b, int off) {
            byte[] x = new byte[16];
            for (int i = 0; i < 8; i++) {
                x[i] = (byte) (b[off + i] ^ (yh >>> (56 - 8 * i)));
                x[8 + i] = (byte) (b[off + 8 + i] ^ (yl >>> (56 - 8 * i)));
            }
            mult(x);
        }

        void update(byte[] b, int off, int len) {
            if (bufLen > 0) {
                int n = Math.min(len, 16 - bufLen);
                System.arraycopy(b, off, buf, bufLen, n);
                bufLen += n;
                off += n;
                len -= n;
                if (bufLen < 16) return;
                block(buf, 0);
                bufLen = 0;
            }
            while (len >= 16) {
                block(b, off);
                off += 16;
                len -= 16;
            }
            if (len > 0) {
                System.arraycopy(b, off, buf, 0, len);
                bufLen = len;
            }
        }

        /** Zero-pads a partial block (between the AAD and the ciphertext, and before the lengths). */
        void pad() {
            if (bufLen > 0) {
                Arrays.fill(buf, bufLen, 16, (byte) 0);
                block(buf, 0);
                bufLen = 0;
            }
        }

        byte[] digest() {
            pad();
            byte[] out = new byte[16];
            for (int i = 0; i < 8; i++) {
                out[i] = (byte) (yh >>> (56 - 8 * i));
                out[8 + i] = (byte) (yl >>> (56 - 8 * i));
            }
            return out;
        }
    }
}
