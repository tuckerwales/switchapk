package java.security.spec;

import java.math.BigInteger;

public class RSAPrivateKeySpec implements KeySpec {
    private final BigInteger modulus;
    private final BigInteger privateExponent;
    private final AlgorithmParameterSpec params;

    public RSAPrivateKeySpec(BigInteger modulus, BigInteger privateExponent) {
        this.modulus = modulus;
        this.privateExponent = privateExponent;
        this.params = null;
    }

    public RSAPrivateKeySpec(BigInteger modulus, BigInteger privateExponent, AlgorithmParameterSpec params) {
        this.modulus = modulus;
        this.privateExponent = privateExponent;
        this.params = params;
    }

    public BigInteger getModulus() {
        return modulus;
    }

    public BigInteger getPrivateExponent() {
        return privateExponent;
    }

    public AlgorithmParameterSpec getParams() {
        return params;
    }
}
