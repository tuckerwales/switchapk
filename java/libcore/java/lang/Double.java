package java.lang;

public final class Double extends Number implements Comparable<Double> {
    public static final double POSITIVE_INFINITY = 1.0 / 0.0;
    public static final double NEGATIVE_INFINITY = -1.0 / 0.0;
    public static final double NaN = 0.0d / 0.0;
    public static final double MAX_VALUE = 0x1.fffffffffffffP+1023;
    public static final double MIN_NORMAL = 0x1.0p-1022;
    public static final double MIN_VALUE = 0x0.0000000000001P-1022;
    public static final int MAX_EXPONENT = 1023;
    public static final int MIN_EXPONENT = -1022;
    public static final int SIZE = 64;
    public static final int BYTES = 8;
    @SuppressWarnings("unchecked")
    public static final Class<Double> TYPE = (Class<Double>) Class.getPrimitiveClass("double");

    private final double value;

    public Double(double value) {
        this.value = value;
    }

    public Double(String s) throws NumberFormatException {
        this.value = parseDouble(s);
    }

    private static native String toStringNative(double d);

    static native double parseDoubleNative(String s);

    public static String toString(double d) {
        return toStringNative(d);
    }

    public static String toHexString(double d) {
        if (isNaN(d)) {
            return "NaN";
        }
        if (isInfinite(d)) {
            return d > 0 ? "Infinity" : "-Infinity";
        }
        long bits = doubleToRawLongBits(d);
        boolean neg = bits < 0;
        int exp = (int) ((bits >>> 52) & 0x7ff);
        long mant = bits & 0xfffffffffffffL;
        StringBuilder sb = new StringBuilder();
        if (neg) {
            sb.append('-');
        }
        if (exp == 0 && mant == 0) {
            return sb.append("0x0.0p0").toString();
        }
        sb.append(exp == 0 ? "0x0." : "0x1.");
        String m = Long.toHexString(mant | 0x10000000000000L).substring(1);
        int end = m.length();
        while (end > 1 && m.charAt(end - 1) == '0') {
            end--;
        }
        sb.append(m, 0, end).append('p').append(exp == 0 ? -1022 : exp - 1023);
        return sb.toString();
    }

    public static Double valueOf(String s) throws NumberFormatException {
        return new Double(parseDouble(s));
    }

    public static Double valueOf(double d) {
        return new Double(d);
    }

    public static double parseDouble(String s) throws NumberFormatException {
        return parseDoubleNative(s);
    }

    public static boolean isNaN(double v) {
        return v != v;
    }

    public static boolean isInfinite(double v) {
        return v == POSITIVE_INFINITY || v == NEGATIVE_INFINITY;
    }

    public static boolean isFinite(double d) {
        return Math.abs(d) <= MAX_VALUE;
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    public String toString() {
        return toString(value);
    }

    public byte byteValue() {
        return (byte) value;
    }

    public short shortValue() {
        return (short) value;
    }

    public int intValue() {
        return (int) value;
    }

    public long longValue() {
        return (long) value;
    }

    public float floatValue() {
        return (float) value;
    }

    public double doubleValue() {
        return value;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(double value) {
        long bits = doubleToLongBits(value);
        return (int) (bits ^ (bits >>> 32));
    }

    public boolean equals(Object obj) {
        return obj instanceof Double && doubleToLongBits(((Double) obj).value) == doubleToLongBits(value);
    }

    public static long doubleToLongBits(double value) {
        if (value != value) {
            return 0x7ff8000000000000L;
        }
        return doubleToRawLongBits(value);
    }

    public static native long doubleToRawLongBits(double value);

    public static native double longBitsToDouble(long bits);

    public int compareTo(Double anotherDouble) {
        return compare(value, anotherDouble.value);
    }

    public static int compare(double d1, double d2) {
        if (d1 < d2) {
            return -1;
        }
        if (d1 > d2) {
            return 1;
        }
        long b1 = doubleToLongBits(d1), b2 = doubleToLongBits(d2);
        return (b1 == b2 ? 0 : (b1 < b2 ? -1 : 1));
    }

    public static double sum(double a, double b) {
        return a + b;
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double min(double a, double b) {
        return Math.min(a, b);
    }
}
