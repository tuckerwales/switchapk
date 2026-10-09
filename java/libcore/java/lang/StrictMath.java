package java.lang;

import java.util.Random;

public final class StrictMath {
    public static final double E = 2.718281828459045;
    public static final double PI = 3.141592653589793;

    private static Random random;

    private StrictMath() {
    }

    public static native double sin(double a);
    public static native double cos(double a);
    public static native double tan(double a);
    public static native double asin(double a);
    public static native double acos(double a);
    public static native double atan(double a);
    public static native double exp(double a);
    public static native double log(double a);
    public static native double log10(double a);
    public static native double log1p(double a);
    public static native double expm1(double a);
    public static native double sqrt(double a);
    public static native double cbrt(double a);
    public static native double IEEEremainder(double f1, double f2);
    public static native double ceil(double a);
    public static native double floor(double a);
    public static native double rint(double a);
    public static native double atan2(double y, double x);
    public static native double pow(double a, double b);
    public static native double sinh(double x);
    public static native double cosh(double x);
    public static native double tanh(double x);
    public static native double hypot(double x, double y);

    public static double toRadians(double angdeg) {
        return angdeg / 180.0 * PI;
    }

    public static double toDegrees(double angrad) {
        return angrad * 180.0 / PI;
    }

    public static int round(float a) {
        if (a != a) {
            return 0;
        }
        return (int) floor(a + 0.5f);
    }

    public static long round(double a) {
        if (a != a) {
            return 0;
        }
        return (long) floor(a + 0.5d);
    }

    public static synchronized double random() {
        if (random == null) {
            random = new Random();
        }
        return random.nextDouble();
    }

    public static int abs(int a) {
        return a < 0 ? -a : a;
    }

    public static long abs(long a) {
        return a < 0 ? -a : a;
    }

    public static float abs(float a) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(a) & 0x7fffffff);
    }

    public static double abs(double a) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(a) & 0x7fffffffffffffffL);
    }

    public static int max(int a, int b) {
        return a >= b ? a : b;
    }

    public static long max(long a, long b) {
        return a >= b ? a : b;
    }

    public static float max(float a, float b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0f && b == 0.0f && Float.floatToRawIntBits(a) == 0x80000000) {
            return b;
        }
        return a >= b ? a : b;
    }

    public static double max(double a, double b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0d && b == 0.0d && Double.doubleToRawLongBits(a) == 0x8000000000000000L) {
            return b;
        }
        return a >= b ? a : b;
    }

    public static int min(int a, int b) {
        return a <= b ? a : b;
    }

    public static long min(long a, long b) {
        return a <= b ? a : b;
    }

    public static float min(float a, float b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0f && b == 0.0f && Float.floatToRawIntBits(b) == 0x80000000) {
            return b;
        }
        return a <= b ? a : b;
    }

    public static double min(double a, double b) {
        if (a != a) {
            return a;
        }
        if (a == 0.0d && b == 0.0d && Double.doubleToRawLongBits(b) == 0x8000000000000000L) {
            return b;
        }
        return a <= b ? a : b;
    }

    public static double ulp(double d) {
        return Math.abs(nextUp(Math.abs(d)) - Math.abs(d));
    }

    public static float ulp(float f) {
        return Math.abs(nextUp(Math.abs(f)) - Math.abs(f));
    }

    public static double signum(double d) {
        return (d == 0.0 || d != d) ? d : copySign(1.0, d);
    }

    public static float signum(float f) {
        return (f == 0.0f || f != f) ? f : copySign(1.0f, f);
    }

    public static double copySign(double magnitude, double sign) {
        return Double.longBitsToDouble((Double.doubleToRawLongBits(sign) & 0x8000000000000000L)
                | (Double.doubleToRawLongBits(magnitude) & 0x7fffffffffffffffL));
    }

    public static float copySign(float magnitude, float sign) {
        return Float.intBitsToFloat((Float.floatToRawIntBits(sign) & 0x80000000)
                | (Float.floatToRawIntBits(magnitude) & 0x7fffffff));
    }

    public static double nextUp(double d) {
        if (d != d || d == Double.POSITIVE_INFINITY) {
            return d;
        }
        d += 0.0d;
        return Double.longBitsToDouble(Double.doubleToRawLongBits(d) + ((d >= 0.0d) ? +1L : -1L));
    }

    public static float nextUp(float f) {
        if (f != f || f == Float.POSITIVE_INFINITY) {
            return f;
        }
        f += 0.0f;
        return Float.intBitsToFloat(Float.floatToRawIntBits(f) + ((f >= 0.0f) ? +1 : -1));
    }

    public static double nextAfter(double start, double direction) {
        if (start > direction) {
            return -nextUp(-start);
        } else if (start < direction) {
            return nextUp(start);
        }
        return direction;
    }

    public static int getExponent(double d) {
        return (int) (((Double.doubleToRawLongBits(d) & 0x7ff0000000000000L) >> 52) - 1023);
    }

    public static int getExponent(float f) {
        return ((Float.floatToRawIntBits(f) & 0x7f800000) >> 23) - 127;
    }

    public static double scalb(double d, int scaleFactor) {
        return d * pow(2, scaleFactor);
    }

    public static float scalb(float f, int scaleFactor) {
        return (float) (f * pow(2, scaleFactor));
    }

    public static int addExact(int x, int y) {
        int r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    public static long addExact(long x, long y) {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    public static int subtractExact(int x, int y) {
        int r = x - y;
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    public static long subtractExact(long x, long y) {
        long r = x - y;
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    public static int multiplyExact(int x, int y) {
        long r = (long) x * (long) y;
        if ((int) r != r) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) r;
    }

    public static long multiplyExact(long x, long y) {
        long r = x * y;
        long ax = Math.abs(x), ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            if (((y != 0) && (r / y != x)) || (x == Long.MIN_VALUE && y == -1)) {
                throw new ArithmeticException("long overflow");
            }
        }
        return r;
    }

    public static int incrementExact(int a) {
        return addExact(a, 1);
    }

    public static int decrementExact(int a) {
        return subtractExact(a, 1);
    }

    public static int negateExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return -a;
    }

    public static int toIntExact(long value) {
        if ((int) value != value) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) value;
    }

    public static int floorDiv(int x, int y) {
        int r = x / y;
        if ((x ^ y) < 0 && (r * y != x)) {
            r--;
        }
        return r;
    }

    public static long floorDiv(long x, long y) {
        long r = x / y;
        if ((x ^ y) < 0 && (r * y != x)) {
            r--;
        }
        return r;
    }

    public static int floorMod(int x, int y) {
        return x - floorDiv(x, y) * y;
    }

    public static long floorMod(long x, long y) {
        return x - floorDiv(x, y) * y;
    }

    public static double fma(double a, double b, double c) {
        return a * b + c;
    }

    public static float fma(float a, float b, float c) {
        return a * b + c;
    }

    public static final double TAU = 2.0 * PI;

    public static double nextDown(double d) {
        if (d != d || d == Double.NEGATIVE_INFINITY) {
            return d;
        }
        if (d == 0.0) {
            return -Double.MIN_VALUE;
        }
        return Double.longBitsToDouble(Double.doubleToRawLongBits(d) + ((d > 0.0d) ? -1L : +1L));
    }

    public static float nextDown(float f) {
        if (f != f || f == Float.NEGATIVE_INFINITY) {
            return f;
        }
        if (f == 0.0f) {
            return -Float.MIN_VALUE;
        }
        return Float.intBitsToFloat(Float.floatToRawIntBits(f) + ((f > 0.0f) ? -1 : +1));
    }

    public static float nextAfter(float start, double direction) {
        if (start > direction) {
            return -nextUp(-start);
        } else if (start < direction) {
            return nextUp(start);
        } else if (start == direction) {
            return (float) direction;
        }
        return start + (float) direction;
    }

    public static long incrementExact(long a) {
        if (a == Long.MAX_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return a + 1L;
    }

    public static long decrementExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return a - 1L;
    }

    public static long negateExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return -a;
    }

    public static long multiplyExact(long x, int y) {
        return multiplyExact(x, (long) y);
    }

    public static long multiplyFull(int x, int y) {
        return (long) x * (long) y;
    }

    public static long multiplyHigh(long x, long y) {
        long x1 = x >> 32;
        long x2 = x & 0xFFFFFFFFL;
        long y1 = y >> 32;
        long y2 = y & 0xFFFFFFFFL;
        long z2 = x2 * y2;
        long t = x1 * y2 + (z2 >>> 32);
        long z1 = t & 0xFFFFFFFFL;
        long z0 = t >> 32;
        z1 += x2 * y1;
        return x1 * y1 + z0 + (z1 >> 32);
    }

    public static long unsignedMultiplyHigh(long x, long y) {
        long result = multiplyHigh(x, y);
        result += (y & (x >> 63));
        result += (x & (y >> 63));
        return result;
    }

    public static int divideExact(int x, int y) {
        int q = x / y;
        if ((x & y & q) >= 0) {
            return q;
        }
        throw new ArithmeticException("integer overflow");
    }

    public static long divideExact(long x, long y) {
        long q = x / y;
        if ((x & y & q) >= 0) {
            return q;
        }
        throw new ArithmeticException("long overflow");
    }

    public static int floorDivExact(int x, int y) {
        final int q = x / y;
        if ((x & y & q) >= 0) {
            if ((x ^ y) < 0 && (q * y != x)) {
                return q - 1;
            }
            return q;
        }
        throw new ArithmeticException("integer overflow");
    }

    public static long floorDivExact(long x, long y) {
        final long q = x / y;
        if ((x & y & q) >= 0) {
            if ((x ^ y) < 0 && (q * y != x)) {
                return q - 1;
            }
            return q;
        }
        throw new ArithmeticException("long overflow");
    }

    public static int ceilDivExact(int x, int y) {
        final int q = x / y;
        if ((x & y & q) >= 0) {
            if ((x ^ y) >= 0 && (q * y != x)) {
                return q + 1;
            }
            return q;
        }
        throw new ArithmeticException("integer overflow");
    }

    public static long ceilDivExact(long x, long y) {
        final long q = x / y;
        if ((x & y & q) >= 0) {
            if ((x ^ y) >= 0 && (q * y != x)) {
                return q + 1;
            }
            return q;
        }
        throw new ArithmeticException("long overflow");
    }

    public static long floorDiv(long x, int y) {
        return floorDiv(x, (long) y);
    }

    public static int floorMod(long x, int y) {
        return (int) floorMod(x, (long) y);
    }

    public static int ceilDiv(int x, int y) {
        final int q = x / y;
        if ((x ^ y) >= 0 && (q * y != x)) {
            return q + 1;
        }
        return q;
    }

    public static long ceilDiv(long x, int y) {
        return ceilDiv(x, (long) y);
    }

    public static long ceilDiv(long x, long y) {
        final long q = x / y;
        if ((x ^ y) >= 0 && (q * y != x)) {
            return q + 1;
        }
        return q;
    }

    public static int ceilMod(int x, int y) {
        final int r = x % y;
        if ((x ^ y) >= 0 && r != 0) {
            return r - y;
        }
        return r;
    }

    public static int ceilMod(long x, int y) {
        return (int) ceilMod(x, (long) y);
    }

    public static long ceilMod(long x, long y) {
        final long r = x % y;
        if ((x ^ y) >= 0 && r != 0) {
            return r - y;
        }
        return r;
    }

    public static int absExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("Overflow to represent absolute value of Integer.MIN_VALUE");
        }
        return abs(a);
    }

    public static long absExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("Overflow to represent absolute value of Long.MIN_VALUE");
        }
        return abs(a);
    }

    public static int clamp(long value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        if (!(min < max)) {
            if (Double.isNaN(min)) {
                throw new IllegalArgumentException("min is NaN");
            }
            if (Double.isNaN(max)) {
                throw new IllegalArgumentException("max is NaN");
            }
            if (Double.compare(min, max) > 0) {
                throw new IllegalArgumentException(min + " > " + max);
            }
        }
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        if (!(min < max)) {
            if (Float.isNaN(min)) {
                throw new IllegalArgumentException("min is NaN");
            }
            if (Float.isNaN(max)) {
                throw new IllegalArgumentException("max is NaN");
            }
            if (Float.compare(min, max) > 0) {
                throw new IllegalArgumentException(min + " > " + max);
            }
        }
        return Math.min(max, Math.max(value, min));
    }
}
