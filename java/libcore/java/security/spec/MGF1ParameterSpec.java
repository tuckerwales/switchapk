package java.security.spec;

public class MGF1ParameterSpec implements AlgorithmParameterSpec {
    public static final MGF1ParameterSpec SHA1 = new MGF1ParameterSpec("SHA-1");
    public static final MGF1ParameterSpec SHA224 = new MGF1ParameterSpec("SHA-224");
    public static final MGF1ParameterSpec SHA256 = new MGF1ParameterSpec("SHA-256");
    public static final MGF1ParameterSpec SHA384 = new MGF1ParameterSpec("SHA-384");
    public static final MGF1ParameterSpec SHA512 = new MGF1ParameterSpec("SHA-512");
    public static final MGF1ParameterSpec SHA512_224 = new MGF1ParameterSpec("SHA-512/224");
    public static final MGF1ParameterSpec SHA512_256 = new MGF1ParameterSpec("SHA-512/256");
    public static final MGF1ParameterSpec SHA3_224 = new MGF1ParameterSpec("SHA3-224");
    public static final MGF1ParameterSpec SHA3_256 = new MGF1ParameterSpec("SHA3-256");
    public static final MGF1ParameterSpec SHA3_384 = new MGF1ParameterSpec("SHA3-384");
    public static final MGF1ParameterSpec SHA3_512 = new MGF1ParameterSpec("SHA3-512");

    private final String mdName;

    public MGF1ParameterSpec(String mdName) {
        if (mdName == null) throw new NullPointerException("digest algorithm is null");
        this.mdName = mdName;
    }

    public String getDigestAlgorithm() {
        return mdName;
    }

    public String toString() {
        return "MGF1ParameterSpec[hashAlgorithm=" + mdName + "]";
    }
}
