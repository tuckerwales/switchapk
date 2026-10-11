package javax.crypto.spec;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;

public class OAEPParameterSpec implements AlgorithmParameterSpec {
    public static final OAEPParameterSpec DEFAULT = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);

    private final String mdName;
    private final String mgfName;
    private final AlgorithmParameterSpec mgfSpec;
    private final PSource pSrc;

    public OAEPParameterSpec(String mdName, String mgfName, AlgorithmParameterSpec mgfSpec, PSource pSrc) {
        if (mdName == null) throw new NullPointerException("digest algorithm is null");
        if (mgfName == null) throw new NullPointerException("mask generation function algorithm is null");
        if (pSrc == null) throw new NullPointerException("source of the encoding input is null");
        this.mdName = mdName;
        this.mgfName = mgfName;
        this.mgfSpec = mgfSpec;
        this.pSrc = pSrc;
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

    public PSource getPSource() {
        return pSrc;
    }
}
