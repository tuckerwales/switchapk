package java.security.spec;

import java.math.BigInteger;

public class EllipticCurve {
    private final ECField field;
    private final BigInteger a;
    private final BigInteger b;
    private final byte[] seed;

    public EllipticCurve(ECField field, BigInteger a, BigInteger b) {
        this(field, a, b, null);
    }

    public EllipticCurve(ECField field, BigInteger a, BigInteger b, byte[] seed) {
        if (field == null || a == null || b == null) throw new NullPointerException();
        if (field instanceof ECFieldFp) {
            BigInteger p = ((ECFieldFp) field).getP();
            if (a.signum() < 0 || a.compareTo(p) >= 0) throw new IllegalArgumentException("a is not in field");
            if (b.signum() < 0 || b.compareTo(p) >= 0) throw new IllegalArgumentException("b is not in field");
        }
        this.field = field;
        this.a = a;
        this.b = b;
        this.seed = seed == null ? null : seed.clone();
    }

    public ECField getField() {
        return field;
    }

    public BigInteger getA() {
        return a;
    }

    public BigInteger getB() {
        return b;
    }

    public byte[] getSeed() {
        return seed == null ? null : seed.clone();
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EllipticCurve)) return false;
        EllipticCurve o = (EllipticCurve) obj;
        return field.equals(o.field) && a.equals(o.a) && b.equals(o.b);
    }

    public int hashCode() {
        return field.hashCode() << 6 + (a.hashCode() << 4) + (b.hashCode() << 2);
    }
}
