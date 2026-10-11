package java.security.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

public interface ECPrivateKey extends PrivateKey, ECKey {
    long serialVersionUID = -7896394956925609184L;

    BigInteger getS();
}
