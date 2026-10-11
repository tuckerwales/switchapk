package javax.crypto.spec;

import java.security.spec.AlgorithmParameterSpec;

public class GCMParameterSpec implements AlgorithmParameterSpec {
    private final byte[] iv;
    private final int tLen;

    public GCMParameterSpec(int tLen, byte[] src) {
        if (src == null) throw new IllegalArgumentException("src array is null");
        if (tLen < 0) throw new IllegalArgumentException("Length argument is negative");
        this.tLen = tLen;
        this.iv = src.clone();
    }

    public GCMParameterSpec(int tLen, byte[] src, int offset, int len) {
        if (tLen < 0) throw new IllegalArgumentException("Length argument is negative");
        if (src == null) throw new IllegalArgumentException("src array is null");
        if (offset < 0 || len < 0 || len > src.length - offset) throw new IllegalArgumentException("Invalid buffer arguments");
        this.tLen = tLen;
        this.iv = new byte[len];
        System.arraycopy(src, offset, iv, 0, len);
    }

    public int getTLen() {
        return tLen;
    }

    public byte[] getIV() {
        return iv.clone();
    }
}
