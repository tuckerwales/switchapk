package java.security.spec;

import java.math.BigInteger;

public class ECPrivateKeySpec implements KeySpec {
    private final BigInteger s;
    private final ECParameterSpec params;

    public ECPrivateKeySpec(BigInteger s, ECParameterSpec params) {
        this.s = s;
        this.params = params;
    }

    public BigInteger getS() {
        return s;
    }

    public ECParameterSpec getParams() {
        return params;
    }
}
