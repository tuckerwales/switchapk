package java.security.spec;

import java.math.BigInteger;

public class ECPublicKeySpec implements KeySpec {
    private final ECPoint w;
    private final ECParameterSpec params;

    public ECPublicKeySpec(ECPoint w, ECParameterSpec params) {
        this.w = w;
        this.params = params;
    }

    public ECPoint getW() {
        return w;
    }

    public ECParameterSpec getParams() {
        return params;
    }
}
