package java.security.spec;

import java.math.BigInteger;

public class RSAPublicKeySpec implements KeySpec {
    private final BigInteger modulus;
    private final BigInteger publicExponent;
    private final AlgorithmParameterSpec params;

    public RSAPublicKeySpec(BigInteger modulus, BigInteger publicExponent) {
        this.modulus = modulus;
        this.publicExponent = publicExponent;
        this.params = null;
    }

    public RSAPublicKeySpec(BigInteger modulus, BigInteger publicExponent, AlgorithmParameterSpec params) {
        this.modulus = modulus;
        this.publicExponent = publicExponent;
        this.params = params;
    }

    public BigInteger getModulus() {
        return modulus;
    }

    public BigInteger getPublicExponent() {
        return publicExponent;
    }

    public AlgorithmParameterSpec getParams() {
        return params;
    }
}
