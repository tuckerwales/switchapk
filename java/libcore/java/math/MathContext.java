package java.math;

public final class MathContext implements java.io.Serializable {
    public static final MathContext UNLIMITED = new MathContext(0, RoundingMode.HALF_UP);
    public static final MathContext DECIMAL32 = new MathContext(7, RoundingMode.HALF_EVEN);
    public static final MathContext DECIMAL64 = new MathContext(16, RoundingMode.HALF_EVEN);
    public static final MathContext DECIMAL128 = new MathContext(34, RoundingMode.HALF_EVEN);

    private final int precision;
    private final RoundingMode roundingMode;

    public MathContext(int setPrecision) {
        this(setPrecision, RoundingMode.HALF_UP);
    }

    public MathContext(int setPrecision, RoundingMode setRoundingMode) {
        if (setPrecision < 0) {
            throw new IllegalArgumentException("Digits < 0");
        }
        precision = setPrecision;
        roundingMode = setRoundingMode;
    }

    public int getPrecision() {
        return precision;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public boolean equals(Object x) {
        if (!(x instanceof MathContext)) {
            return false;
        }
        MathContext mc = (MathContext) x;
        return mc.precision == precision && mc.roundingMode == roundingMode;
    }

    public int hashCode() {
        return roundingMode.hashCode() * 59 + precision;
    }

    public String toString() {
        return "precision=" + precision + " roundingMode=" + roundingMode;
    }
}
