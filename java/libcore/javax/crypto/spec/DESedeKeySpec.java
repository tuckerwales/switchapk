package javax.crypto.spec;

import java.security.InvalidKeyException;
import java.security.spec.KeySpec;

public class DESedeKeySpec implements KeySpec {
    public static final int DES_EDE_KEY_LEN = 24;

    private final byte[] key;

    public DESedeKeySpec(byte[] key) throws InvalidKeyException {
        this(key, 0);
    }

    public DESedeKeySpec(byte[] key, int offset) throws InvalidKeyException {
        if (key.length - offset < DES_EDE_KEY_LEN) throw new InvalidKeyException("Wrong key size");
        this.key = new byte[DES_EDE_KEY_LEN];
        System.arraycopy(key, offset, this.key, 0, DES_EDE_KEY_LEN);
    }

    public byte[] getKey() {
        return key.clone();
    }

    public static boolean isParityAdjusted(byte[] key, int offset) throws InvalidKeyException {
        if (key.length - offset < DES_EDE_KEY_LEN) throw new InvalidKeyException("Wrong key size");
        for (int i = 0; i < DES_EDE_KEY_LEN; i++) {
            if ((Integer.bitCount(key[offset + i] & 0xff) & 1) == 0) return false;
        }
        return true;
    }
}
