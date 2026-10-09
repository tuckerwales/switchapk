package java.security.spec;

import java.math.BigInteger;
import java.util.Arrays;

public class ECFieldF2m implements ECField {
    private final int m;
    private final int[] ks;
    private final BigInteger rp;

    public ECFieldF2m(int m) {
        if (m <= 0) throw new IllegalArgumentException("m is not positive");
        this.m = m;
        this.ks = null;
        this.rp = null;
    }

    public ECFieldF2m(int m, BigInteger rp) {
        if (m <= 0) throw new IllegalArgumentException("m is not positive");
        this.m = m;
        this.rp = rp;
        int bits = rp.bitCount();
        if (!rp.testBit(0) || !rp.testBit(m) || (bits != 3 && bits != 5)) {
            throw new IllegalArgumentException("rp does not represent a valid reduction polynomial");
        }
        BigInteger temp = rp.clearBit(0).clearBit(m);
        ks = new int[bits - 2];
        for (int i = ks.length - 1; i >= 0; i--) {
            int index = temp.getLowestSetBit();
            ks[i] = index;
            temp = temp.clearBit(index);
        }
    }

    public ECFieldF2m(int m, int[] ks) {
        if (m <= 0) throw new IllegalArgumentException("m is not positive");
        if (ks.length != 1 && ks.length != 3) throw new IllegalArgumentException("length of ks is neither 1 nor 3");
        this.m = m;
        this.ks = ks.clone();
        BigInteger r = BigInteger.ONE.setBit(m);
        for (int k : this.ks) {
            if (k <= 0 || k >= m) throw new IllegalArgumentException("ks is not in range");
            r = r.setBit(k);
        }
        this.rp = r;
    }

    public int getFieldSize() {
        return m;
    }

    public int getM() {
        return m;
    }

    public BigInteger getReductionPolynomial() {
        return rp;
    }

    public int[] getMidTermsOfReductionPolynomial() {
        return ks == null ? null : ks.clone();
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ECFieldF2m)) return false;
        ECFieldF2m o = (ECFieldF2m) obj;
        return m == o.m && Arrays.equals(ks, o.ks);
    }

    public int hashCode() {
        return (m << 5) + (rp == null ? 0 : rp.hashCode());
    }
}
