package java.lang;

public final class Long extends Number implements Comparable<Long> {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
    public static final int SIZE = 64;
    public static final int BYTES = 8;
    @SuppressWarnings("unchecked")
    public static final Class<Long> TYPE = (Class<Long>) Class.getPrimitiveClass("long");

    private static final Long[] CACHE = new Long[256];

    private final long value;

    public Long(long value) {
        this.value = value;
    }

    public Long(String s) throws NumberFormatException {
        this.value = parseLong(s, 10);
    }

    public static Long valueOf(long l) {
        if (l >= -128 && l <= 127) {
            int idx = (int) l + 128;
            Long r = CACHE[idx];
            if (r == null) {
                r = new Long(l);
                CACHE[idx] = r;
            }
            return r;
        }
        return new Long(l);
    }

    public static Long valueOf(String s) throws NumberFormatException {
        return valueOf(parseLong(s, 10));
    }

    public static Long valueOf(String s, int radix) throws NumberFormatException {
        return valueOf(parseLong(s, radix));
    }

    public static Long getLong(String nm) {
        return getLong(nm, null);
    }

    public static Long getLong(String nm, long val) {
        Long r = getLong(nm, null);
        return r == null ? valueOf(val) : r;
    }

    public static Long getLong(String nm, Long val) {
        String v = System.getProperty(nm);
        if (v != null) {
            try {
                return decode(v);
            } catch (NumberFormatException e) {
            }
        }
        return val;
    }

    public static Long decode(String nm) throws NumberFormatException {
        if (nm.isEmpty()) {
            throw new NumberFormatException("Zero length string");
        }
        int index = 0;
        boolean negative = false;
        char first = nm.charAt(0);
        if (first == '-') {
            negative = true;
            index++;
        } else if (first == '+') {
            index++;
        }
        int radix = 10;
        if (nm.startsWith("0x", index) || nm.startsWith("0X", index)) {
            index += 2;
            radix = 16;
        } else if (nm.startsWith("#", index)) {
            index++;
            radix = 16;
        } else if (nm.startsWith("0", index) && nm.length() > 1 + index) {
            index++;
            radix = 8;
        }
        long r = parseLong((negative ? "-" : "") + nm.substring(index), radix);
        return valueOf(r);
    }

    static long parseLongImpl(String s, int radix, long min, long max) {
        if (s == null) {
            throw new NumberFormatException("s == null");
        }
        int len = s.length();
        if (len == 0) {
            throw new NumberFormatException("For input string: \"\"");
        }
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new NumberFormatException("Invalid radix: " + radix);
        }
        int i = 0;
        boolean neg = false;
        char c0 = s.charAt(0);
        if (c0 == '-' || c0 == '+') {
            neg = c0 == '-';
            i++;
            if (len == 1) {
                throw new NumberFormatException("For input string: \"" + s + "\"");
            }
        }
        if (neg && min >= 0) {
            throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s + ".");
        }
        // accumulate negatively to handle MIN_VALUE
        long result = 0;
        long limit = neg ? min : -max;
        long multmin = limit / radix;
        for (; i < len; i++) {
            int d = Character.digit(s.charAt(i), radix);
            if (d < 0 || result < multmin) {
                throw new NumberFormatException("For input string: \"" + s + "\"" + (radix != 10 ? " under radix " + radix : ""));
            }
            result *= radix;
            if (result < limit + d) {
                throw new NumberFormatException("For input string: \"" + s + "\"" + (radix != 10 ? " under radix " + radix : ""));
            }
            result -= d;
        }
        return neg ? result : -result;
    }

    public static long parseLong(String s) throws NumberFormatException {
        return parseLong(s, 10);
    }

    public static long parseLong(String s, int radix) throws NumberFormatException {
        return parseLongImpl(s, radix, MIN_VALUE, MAX_VALUE);
    }

    public static long parseUnsignedLong(String s) {
        return parseUnsignedLong(s, 10);
    }

    public static long parseUnsignedLong(String s, int radix) {
        if (s.length() <= 12) {
            return parseLong(s, radix);
        }
        long first = parseLong(s.substring(0, s.length() - 1), radix);
        int second = Character.digit(s.charAt(s.length() - 1), radix);
        if (second < 0) {
            throw new NumberFormatException("Bad digit at end of " + s);
        }
        return first * radix + second;
    }

    public static String toString(long l) {
        if (l == MIN_VALUE) {
            return "-9223372036854775808";
        }
        char[] buf = new char[20];
        int pos = 20;
        boolean neg = l < 0;
        if (neg) {
            l = -l;
        }
        do {
            buf[--pos] = (char) ('0' + (int) (l % 10));
            l /= 10;
        } while (l != 0);
        if (neg) {
            buf[--pos] = '-';
        }
        return new String(buf, pos, 20 - pos);
    }

    public static String toString(long l, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            radix = 10;
        }
        if (radix == 10) {
            return toString(l);
        }
        char[] buf = new char[65];
        int pos = 65;
        boolean neg = l < 0;
        if (!neg) {
            l = -l;
        }
        while (l <= -radix) {
            buf[--pos] = Integer.DIGITS[(int) (-(l % radix))];
            l = l / radix;
        }
        buf[--pos] = Integer.DIGITS[(int) (-l)];
        if (neg) {
            buf[--pos] = '-';
        }
        return new String(buf, pos, 65 - pos);
    }

    private static String toUnsigned(long i, int shift) {
        char[] buf = new char[64];
        int pos = 64;
        int radix = 1 << shift;
        long mask = radix - 1;
        do {
            buf[--pos] = Integer.DIGITS[(int) (i & mask)];
            i >>>= shift;
        } while (i != 0);
        return new String(buf, pos, 64 - pos);
    }

    public static String toHexString(long i) {
        return toUnsigned(i, 4);
    }

    public static String toOctalString(long i) {
        return toUnsigned(i, 3);
    }

    public static String toBinaryString(long i) {
        return toUnsigned(i, 1);
    }

    public static String toUnsignedString(long i) {
        if (i >= 0) {
            return toString(i);
        }
        long quot = (i >>> 1) / 5;
        long rem = i - quot * 10;
        return toString(quot) + rem;
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
        return hashCode(value);
    }

    public static int hashCode(long value) {
        return (int) (value ^ (value >>> 32));
    }

    public boolean equals(Object obj) {
        return obj instanceof Long && ((Long) obj).value == value;
    }

    public int compareTo(Long other) {
        return compare(value, other.value);
    }

    public static int compare(long x, long y) {
        return x < y ? -1 : (x == y ? 0 : 1);
    }

    public static int compareUnsigned(long x, long y) {
        return compare(x + MIN_VALUE, y + MIN_VALUE);
    }

    public static long divideUnsigned(long dividend, long divisor) {
        if (divisor < 0) {
            return compareUnsigned(dividend, divisor) < 0 ? 0 : 1;
        }
        if (dividend >= 0) {
            return dividend / divisor;
        }
        long q = ((dividend >>> 1) / divisor) << 1;
        long r = dividend - q * divisor;
        return q + (compareUnsigned(r, divisor) >= 0 ? 1 : 0);
    }

    public static long remainderUnsigned(long dividend, long divisor) {
        return dividend - divideUnsigned(dividend, divisor) * divisor;
    }

    public static long highestOneBit(long i) {
        return i & (MIN_VALUE >>> numberOfLeadingZeros(i));
    }

    public static long lowestOneBit(long i) {
        return i & -i;
    }

    public static int numberOfLeadingZeros(long i) {
        if (i == 0) {
            return 64;
        }
        int x = (int) (i >>> 32);
        return x == 0 ? 32 + Integer.numberOfLeadingZeros((int) i) : Integer.numberOfLeadingZeros(x);
    }

    public static int numberOfTrailingZeros(long i) {
        int x = (int) i;
        return x == 0 ? 32 + Integer.numberOfTrailingZeros((int) (i >>> 32)) : Integer.numberOfTrailingZeros(x);
    }

    public static int bitCount(long i) {
        return Integer.bitCount((int) i) + Integer.bitCount((int) (i >>> 32));
    }

    public static long rotateLeft(long i, int distance) {
        return (i << distance) | (i >>> -distance);
    }

    public static long rotateRight(long i, int distance) {
        return (i >>> distance) | (i << -distance);
    }

    public static long reverse(long i) {
        return ((long) Integer.reverse((int) i) << 32) | (Integer.reverse((int) (i >>> 32)) & 0xffffffffL);
    }

    public static long reverseBytes(long i) {
        return ((long) Integer.reverseBytes((int) i) << 32) | (Integer.reverseBytes((int) (i >>> 32)) & 0xffffffffL);
    }

    public static int signum(long i) {
        return (int) ((i >> 63) | (-i >>> 63));
    }

    public static long sum(long a, long b) {
        return a + b;
    }

    public static long max(long a, long b) {
        return Math.max(a, b);
    }

    public static long min(long a, long b) {
        return Math.min(a, b);
    }
}
