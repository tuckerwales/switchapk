package java.security.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

public interface RSAPublicKey extends PublicKey, RSAKey {
    long serialVersionUID = -8727434096241101194L;

    BigInteger getPublicExponent();
}
