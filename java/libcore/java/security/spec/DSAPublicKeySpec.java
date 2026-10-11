package java.security.spec;

import java.math.BigInteger;

public class DSAPublicKeySpec implements KeySpec {
    private final BigInteger y;
    private final BigInteger p;
    private final BigInteger q;
    private final BigInteger g;

    public DSAPublicKeySpec(BigInteger y, BigInteger p, BigInteger q, BigInteger g) {
        this.y = y;
        this.p = p;
        this.q = q;
        this.g = g;
    }

    public BigInteger getY() {
        return y;
    }

    public BigInteger getP() {
        return p;
    }

    public BigInteger getQ() {
        return q;
    }

    public BigInteger getG() {
        return g;
    }
}
