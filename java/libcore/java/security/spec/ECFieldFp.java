package java.security.spec;

import java.math.BigInteger;

public class ECFieldFp implements ECField {
    private final BigInteger p;

    public ECFieldFp(BigInteger p) {
        if (p.signum() != 1) throw new IllegalArgumentException("p is not positive");
        this.p = p;
    }

    public int getFieldSize() {
        return p.bitLength();
    }

    public BigInteger getP() {
        return p;
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof ECFieldFp && p.equals(((ECFieldFp) obj).p);
    }

    public int hashCode() {
        return p.hashCode();
    }
}
