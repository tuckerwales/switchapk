package javax.crypto.spec;

import java.security.spec.AlgorithmParameterSpec;

public class PBEParameterSpec implements AlgorithmParameterSpec {
    private final byte[] salt;
    private final int iterationCount;
    private final AlgorithmParameterSpec paramSpec;

    public PBEParameterSpec(byte[] salt, int iterationCount) {
        this(salt, iterationCount, null);
    }

    public PBEParameterSpec(byte[] salt, int iterationCount, AlgorithmParameterSpec paramSpec) {
        this.salt = salt.clone();
        this.iterationCount = iterationCount;
        this.paramSpec = paramSpec;
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public AlgorithmParameterSpec getParameterSpec() {
        return paramSpec;
    }
}
