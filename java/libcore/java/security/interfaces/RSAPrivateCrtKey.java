package java.security.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

public interface RSAPrivateCrtKey extends RSAPrivateKey {
    long serialVersionUID = -5682214253527700368L;

    BigInteger getPublicExponent();

    BigInteger getPrimeP();

    BigInteger getPrimeQ();

    BigInteger getPrimeExponentP();

    BigInteger getPrimeExponentQ();

    BigInteger getCrtCoefficient();
}
