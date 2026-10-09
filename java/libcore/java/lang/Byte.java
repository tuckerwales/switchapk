package java.lang;

public final class Byte extends Number implements Comparable<Byte> {
    public static final byte MIN_VALUE = -128;
    public static final byte MAX_VALUE = 127;
    public static final int SIZE = 8;
    public static final int BYTES = 1;
    @SuppressWarnings("unchecked")
    public static final Class<Byte> TYPE = (Class<Byte>) Class.getPrimitiveClass("byte");

    private static final Byte[] CACHE = new Byte[256];

    private final byte value;

    public Byte(byte value) {
        this.value = value;
    }

    public Byte(String s) throws NumberFormatException {
        this.value = parseByte(s, 10);
    }

    public static Byte valueOf(byte v) {
        if (v >= -128 && v <= 127) {
            int idx = v + 128;
            Byte r = CACHE[idx];
            if (r == null) {
                r = new Byte(v);
                CACHE[idx] = r;
            }
            return r;
        }
        return new Byte(v);
    }

    public static Byte valueOf(String s) throws NumberFormatException {
        return valueOf(parseByte(s, 10));
    }

    public static Byte valueOf(String s, int radix) throws NumberFormatException {
        return valueOf(parseByte(s, radix));
    }

    public static Byte decode(String nm) throws NumberFormatException {
        long v = Long.decode(nm).longValue();
        if (v < MIN_VALUE || v > MAX_VALUE) {
            throw new NumberFormatException("Value out of range. Value:\"" + nm + "\"");
        }
        return valueOf((byte) v);
    }

    public static byte parseByte(String s) throws NumberFormatException {
        return parseByte(s, 10);
    }

    public static byte parseByte(String s, int radix) throws NumberFormatException {
        return (byte) Long.parseLongImpl(s, radix, MIN_VALUE, MAX_VALUE);
    }

    public static String toString(byte v) {
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

    public static int hashCode(byte value) {
        return value;
    }

    public boolean equals(Object obj) {
        return obj instanceof Byte && ((Byte) obj).value == value;
    }

    public int compareTo(Byte other) {
        return value - other.value;
    }

    public static int compare(byte x, byte y) {
        return x - y;
    }

    public static int toUnsignedInt(byte x) {
        return x & 0xff;
    }

    public static long toUnsignedLong(byte x) {
        return x & 0xffL;
    }

    public static int compareUnsigned(byte x, byte y) {
        return toUnsignedInt(x) - toUnsignedInt(y);
    }
}
