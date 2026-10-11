package java.security.spec;

import java.math.BigInteger;

public class DSAParameterSpec implements AlgorithmParameterSpec, java.security.interfaces.DSAParams {
    private final BigInteger p;
    private final BigInteger q;
    private final BigInteger g;

    public DSAParameterSpec(BigInteger p, BigInteger q, BigInteger g) {
        this.p = p;
        this.q = q;
        this.g = g;
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
