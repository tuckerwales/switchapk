package java.lang;

public final class Short extends Number implements Comparable<Short> {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;
    public static final int SIZE = 16;
    public static final int BYTES = 2;
    @SuppressWarnings("unchecked")
    public static final Class<Short> TYPE = (Class<Short>) Class.getPrimitiveClass("short");

    private static final Short[] CACHE = new Short[256];

    private final short value;

    public Short(short value) {
        this.value = value;
    }

    public Short(String s) throws NumberFormatException {
        this.value = parseShort(s, 10);
    }

    public static Short valueOf(short v) {
        if (v >= -128 && v <= 127) {
            int idx = v + 128;
            Short r = CACHE[idx];
            if (r == null) {
                r = new Short(v);
                CACHE[idx] = r;
            }
            return r;
        }
        return new Short(v);
    }

    public static Short valueOf(String s) throws NumberFormatException {
        return valueOf(parseShort(s, 10));
    }

    public static Short valueOf(String s, int radix) throws NumberFormatException {
        return valueOf(parseShort(s, radix));
    }

    public static Short decode(String nm) throws NumberFormatException {
        long v = Long.decode(nm).longValue();
        if (v < MIN_VALUE || v > MAX_VALUE) {
            throw new NumberFormatException("Value out of range. Value:\"" + nm + "\"");
        }
        return valueOf((short) v);
    }

    public static short parseShort(String s) throws NumberFormatException {
        return parseShort(s, 10);
    }

    public static short parseShort(String s, int radix) throws NumberFormatException {
        return (short) Long.parseLongImpl(s, radix, MIN_VALUE, MAX_VALUE);
    }

    public static String toString(short v) {
        return Integer.toString(v);
    }

    public byte byteValue() {
        return (byte) value;
    }

    public short shortValue() {
        return (short) value;
    }

    public int intValue() {
        return value;
    }

    public long longValue() {
        return value;
    }

    public float floatValue() {
        return value;
    }

    public double doubleValue() {
        return value;
    }

    public String toString() {
        return Integer.toString(value);
    }

    public int hashCode() {
        return value;
    }

    public static int hashCode(short value) {
        return value;
    }

    public boolean equals(Object obj) {
        return obj instanceof Short && ((Short) obj).value == value;
    }

    public int compareTo(Short other) {
        return value - other.value;
    }

    public static int compare(short x, short y) {
        return x - y;
    }

    public static int toUnsignedInt(short x) {
        return x & 0xffff;
    }

    public static long toUnsignedLong(short x) {
        return x & 0xffffL;
    }

    public static short reverseBytes(short i) {
        return (short) (((i & 0xFF00) >> 8) | (i << 8));
    }
}
