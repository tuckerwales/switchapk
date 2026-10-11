package java.security.spec;

public class PSSParameterSpec implements AlgorithmParameterSpec {
    public static final int TRAILER_FIELD_BC = 1;
    public static final PSSParameterSpec DEFAULT = new PSSParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, 20, TRAILER_FIELD_BC);

    private final String mdName;
    private final String mgfName;
    private final AlgorithmParameterSpec mgfSpec;
    private final int saltLen;
    private final int trailerField;

    public PSSParameterSpec(String mdName, String mgfName, AlgorithmParameterSpec mgfSpec, int saltLen, int trailerField) {
        if (mdName == null) throw new NullPointerException("digest algorithm is null");
        if (mgfName == null) throw new NullPointerException("mask generation function algorithm is null");
        if (saltLen < 0) throw new IllegalArgumentException("negative saltLen value: " + saltLen);
        if (trailerField < 0) throw new IllegalArgumentException("negative trailerField: " + trailerField);
        this.mdName = mdName;
        this.mgfName = mgfName;
        this.mgfSpec = mgfSpec;
        this.saltLen = saltLen;
        this.trailerField = trailerField;
    }

    @Deprecated
    public PSSParameterSpec(int saltLen) {
        this("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, saltLen, TRAILER_FIELD_BC);
    }

    public String getDigestAlgorithm() {
        return mdName;
    }

    public String getMGFAlgorithm() {
        return mgfName;
    }

    public AlgorithmParameterSpec getMGFParameters() {
        return mgfSpec;
    }

    public int getSaltLength() {
        return saltLen;
    }

    public int getTrailerField() {
        return trailerField;
    }

    public String toString() {
        return "PSSParameterSpec[hashAlgorithm=" + mdName + ", maskGenAlgorithm=" + mgfSpec + ", saltLength=" + saltLen
                + ", trailerField=" + trailerField + "]";
    }
}
