package java.security.spec;

import java.math.BigInteger;

public class RSAPrivateCrtKeySpec extends RSAPrivateKeySpec {
    private final BigInteger publicExponent;
    private final BigInteger primeP;
    private final BigInteger primeQ;
    private final BigInteger primeExponentP;
    private final BigInteger primeExponentQ;
    private final BigInteger crtCoefficient;

    public RSAPrivateCrtKeySpec(BigInteger modulus, BigInteger publicExponent, BigInteger privateExponent,
            BigInteger primeP, BigInteger primeQ, BigInteger primeExponentP, BigInteger primeExponentQ,
            BigInteger crtCoefficient) {
        this(modulus, publicExponent, privateExponent, primeP, primeQ, primeExponentP, primeExponentQ, crtCoefficient, null);
    }

    public RSAPrivateCrtKeySpec(BigInteger modulus, BigInteger publicExponent, BigInteger privateExponent,
            BigInteger primeP, BigInteger primeQ, BigInteger primeExponentP, BigInteger primeExponentQ,
            BigInteger crtCoefficient, AlgorithmParameterSpec keyParams) {
        super(modulus, privateExponent, keyParams);
        this.publicExponent = publicExponent;
        this.primeP = primeP;
        this.primeQ = primeQ;
        this.primeExponentP = primeExponentP;
        this.primeExponentQ = primeExponentQ;
        this.crtCoefficient = crtCoefficient;
    }

    public BigInteger getPublicExponent() {
        return publicExponent;
    }

    public BigInteger getPrimeP() {
        return primeP;
    }

    public BigInteger getPrimeQ() {
        return primeQ;
    }

    public BigInteger getPrimeExponentP() {
        return primeExponentP;
    }

    public BigInteger getPrimeExponentQ() {
        return primeExponentQ;
    }

    public BigInteger getCrtCoefficient() {
        return crtCoefficient;
    }
}
