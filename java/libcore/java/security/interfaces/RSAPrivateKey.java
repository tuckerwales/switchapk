package java.security.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

public interface RSAPrivateKey extends PrivateKey, RSAKey {
    long serialVersionUID = 5187144804936595022L;

    BigInteger getPrivateExponent();
}
