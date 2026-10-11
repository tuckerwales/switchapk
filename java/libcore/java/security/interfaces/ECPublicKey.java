package java.security.interfaces;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

public interface ECPublicKey extends PublicKey, ECKey {
    long serialVersionUID = -3314988629879632826L;

    ECPoint getW();
}
