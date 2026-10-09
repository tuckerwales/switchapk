package javax.crypto.interfaces;

import java.math.BigInteger;

public interface DHPrivateKey extends DHKey, java.security.PrivateKey {
    long serialVersionUID = 2211791113380396553L;

    BigInteger getX();
}
