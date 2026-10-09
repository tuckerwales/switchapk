package java.lang;

public final class Integer extends Number implements Comparable<Integer> {
    public static final int MIN_VALUE = 0x80000000;
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int SIZE = 32;
    public static final int BYTES = 4;
    @SuppressWarnings("unchecked")
    public static final Class<Integer> TYPE = (Class<Integer>) Class.getPrimitiveClass("int");

    private static final Integer[] CACHE = new Integer[256];
    static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g',
        'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private final int value;

    public Integer(int value) {
        this.value = value;
    }

    public Integer(String s) throws NumberFormatException {
        this.value = parseInt(s, 10);
    }

    public static Integer valueOf(int i) {
        if (i >= -128 && i <= 127) {
            Integer r = CACHE[i + 128];
            if (r == null) {
                r = new Integer(i);
                CACHE[i + 128] = r;
            }
            return r;
        }
        return new Integer(i);
    }

    public static Integer valueOf(String s) throws NumberFormatException {
        return valueOf(parseInt(s, 10));
    }

    public static Integer valueOf(String s, int radix) throws NumberFormatException {
        return valueOf(parseInt(s, radix));
    }

    public static Integer decode(String nm) throws NumberFormatException {
        return valueOf((int) Long.decode(nm).longValue());
    }

    public static Integer getInteger(String nm) {
        return getInteger(nm, null);
    }

    public static Integer getInteger(String nm, int val) {
        Integer r = getInteger(nm, null);
        return r == null ? valueOf(val) : r;
    }

    public static Integer getInteger(String nm, Integer val) {
        String v = System.getProperty(nm);
        if (v != null) {
            try {
                return decode(v);
            } catch (NumberFormatException e) {
            }
        }
        return val;
    }

    public static int parseInt(String s) throws NumberFormatException {
        return parseInt(s, 10);
    }

    public static int parseInt(String s, int radix) throws NumberFormatException {
        if (s == null) {
            throw new NumberFormatException("s == null");
        }
        long v = Long.parseLongImpl(s, radix, MIN_VALUE, MAX_VALUE);
        return (int) v;
    }

    public static int parseUnsignedInt(String s) {
        return parseUnsignedInt(s, 10);
    }

    public static int parseUnsignedInt(String s, int radix) {
        long v = Long.parseLongImpl(s, radix, 0, 0xffffffffL);
        return (int) v;
    }

    public static String toString(int i) {
        if (i == MIN_VALUE) {
            return "-2147483648";
        }
        char[] buf = new char[11];
        int pos = 11;
        boolean neg = i < 0;
        if (neg) {
            i = -i;
        }
        do {
            buf[--pos] = (char) ('0' + i % 10);
            i /= 10;
        } while (i != 0);
        if (neg) {
            buf[--pos] = '-';
        }
        return new String(buf, pos, 11 - pos);
    }

    public static String toString(int i, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            radix = 10;
        }
        if (radix == 10) {
            return toString(i);
        }
        return Long.toString(i, radix);
    }

    private static String toUnsigned(int i, int shift) {
        char[] buf = new char[32];
        int pos = 32;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            buf[--pos] = DIGITS[i & mask];
            i >>>= shift;
        } while (i != 0);
        return new String(buf, pos, 32 - pos);
    }

    public static String toHexString(int i) {
        return toUnsigned(i, 4);
    }

    public static String toOctalString(int i) {
        return toUnsigned(i, 3);
    }

    public static String toBinaryString(int i) {
        return toUnsigned(i, 1);
    }

    public static String toUnsignedString(int i) {
        return Long.toString(i & 0xffffffffL);
    }

    public static String toUnsignedString(int i, int radix) {
        return Long.toString(i & 0xffffffffL, radix);
    }

    public static long toUnsignedLong(int x) {
        return x & 0xffffffffL;
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
        return toString(value);
    }

    public int hashCode() {
        return value;
    }

    public static int hashCode(int value) {
        return value;
    }

    public boolean equals(Object obj) {
        return obj instanceof Integer && ((Integer) obj).value == value;
    }

    public int compareTo(Integer other) {
        return compare(value, other.value);
    }

    public static int compare(int x, int y) {
        return x < y ? -1 : (x == y ? 0 : 1);
    }

    public static int compareUnsigned(int x, int y) {
        return compare(x + MIN_VALUE, y + MIN_VALUE);
    }

    public static int divideUnsigned(int dividend, int divisor) {
        return (int) (toUnsignedLong(dividend) / toUnsignedLong(divisor));
    }

    public static int remainderUnsigned(int dividend, int divisor) {
        return (int) (toUnsignedLong(dividend) % toUnsignedLong(divisor));
    }

    public static int highestOneBit(int i) {
        return i & (MIN_VALUE >>> numberOfLeadingZeros(i));
    }

    public static int lowestOneBit(int i) {
        return i & -i;
    }

    public static int numberOfLeadingZeros(int i) {
        if (i == 0) {
            return 32;
        }
        int n = 1;
        if (i >>> 16 == 0) { n += 16; i <<= 16; }
        if (i >>> 24 == 0) { n += 8; i <<= 8; }
        if (i >>> 28 == 0) { n += 4; i <<= 4; }
        if (i >>> 30 == 0) { n += 2; i <<= 2; }
        n -= i >>> 31;
        return n;
    }

    public static int numberOfTrailingZeros(int i) {
        if (i == 0) {
            return 32;
        }
        int n = 0;
        while ((i & 1) == 0) {
            i >>>= 1;
            n++;
        }
        return n;
    }

    public static int bitCount(int i) {
        i = i - ((i >>> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >>> 2) & 0x33333333);
        i = (i + (i >>> 4)) & 0x0f0f0f0f;
        i = i + (i >>> 8);
        i = i + (i >>> 16);
        return i & 0x3f;
    }

    public static int rotateLeft(int i, int distance) {
        return (i << distance) | (i >>> -distance);
    }

    public static int rotateRight(int i, int distance) {
        return (i >>> distance) | (i << -distance);
    }

    public static int reverse(int i) {
        i = (i & 0x55555555) << 1 | (i >>> 1) & 0x55555555;
        i = (i & 0x33333333) << 2 | (i >>> 2) & 0x33333333;
        i = (i & 0x0f0f0f0f) << 4 | (i >>> 4) & 0x0f0f0f0f;
        return reverseBytes(i);
    }

    public static int reverseBytes(int i) {
        return (i << 24) | ((i & 0xff00) << 8) | ((i >>> 8) & 0xff00) | (i >>> 24);
    }

    public static int signum(int i) {
        return (i >> 31) | (-i >>> 31);
    }

    public static int sum(int a, int b) {
        return a + b;
    }

    public static int max(int a, int b) {
        return Math.max(a, b);
    }

    public static int min(int a, int b) {
        return Math.min(a, b);
    }

    public static int parseInt(CharSequence s, int beginIndex, int endIndex, int radix)
            throws NumberFormatException {
        java.util.Objects.requireNonNull(s);
        java.util.Objects.checkFromToIndex(beginIndex, endIndex, s.length());
        return parseInt(s.subSequence(beginIndex, endIndex).toString(), radix);
    }

    public static int parseUnsignedInt(CharSequence s, int beginIndex, int endIndex, int radix)
            throws NumberFormatException {
        java.util.Objects.requireNonNull(s);
        java.util.Objects.checkFromToIndex(beginIndex, endIndex, s.length());
        return parseUnsignedInt(s.subSequence(beginIndex, endIndex).toString(), radix);
    }
}
