package javax.crypto.spec;

public class PSource {
    private final String pSrcName;

    protected PSource(String pSrcName) {
        if (pSrcName == null) throw new NullPointerException("pSource algorithm is null");
        this.pSrcName = pSrcName;
    }

    public String getAlgorithm() {
        return pSrcName;
    }

    public static final class PSpecified extends PSource {
        public static final PSpecified DEFAULT = new PSpecified(new byte[0]);

        private final byte[] p;

        public PSpecified(byte[] p) {
            super("PSpecified");
            this.p = p.clone();
        }

        public byte[] getValue() {
            return p.clone();
        }
    }
}
