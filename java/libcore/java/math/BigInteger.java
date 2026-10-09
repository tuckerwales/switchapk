package java.math;

import java.util.Random;

public class BigInteger extends Number implements Comparable<BigInteger> {
    /** sign: -1, 0, 1 */
    final int signum;
    /** magnitude, little-endian 32-bit words, no trailing zero words */
    final int[] mag;

    public static final BigInteger ZERO = new BigInteger(0, new int[0]);
    public static final BigInteger ONE = valueOf(1);
    public static final BigInteger TWO = valueOf(2);
    public static final BigInteger TEN = valueOf(10);
    public static final BigInteger NEGATIVE_ONE = valueOf(-1);

    private static final long MASK = 0xffffffffL;

    BigInteger(int signum, int[] mag) {
        int len = mag.length;
        while (len > 0 && mag[len - 1] == 0) {
            len--;
        }
        if (len != mag.length) {
            int[] m = new int[len];
            System.arraycopy(mag, 0, m, 0, len);
            mag = m;
        }
        this.mag = mag;
        this.signum = len == 0 ? 0 : signum;
    }

    public BigInteger(String val) {
        this(val, 10);
    }

    public BigInteger(String val, int radix) {
        BigInteger r = parse(val, radix);
        this.signum = r.signum;
        this.mag = r.mag;
    }

    public BigInteger(byte[] val) {
        this(val, 0, val.length);
    }

    public BigInteger(byte[] val, int off, int len) {
        if (len == 0) {
            throw new NumberFormatException("Zero length BigInteger");
        }
        boolean neg = val[off] < 0;
        byte[] b = new byte[len];
        System.arraycopy(val, off, b, 0, len);
        if (neg) {
            // two's complement negate
            for (int i = 0; i < len; i++) {
                b[i] = (byte) ~b[i];
            }
            for (int i = len - 1; i >= 0; i--) {
                b[i]++;
                if (b[i] != 0) {
                    break;
                }
            }
        }
        BigInteger r = new BigInteger(neg ? -1 : 1, bytesToMag(b));
        this.signum = r.signum;
        this.mag = r.mag;
    }

    public BigInteger(int signum, byte[] magnitude) {
        this(signum, bytesToMag(magnitude));
        if (signum < -1 || signum > 1) {
            throw new NumberFormatException("Invalid signum value");
        }
    }

    public BigInteger(int numBits, Random rnd) {
        // Same bytes as the JDK: nextBytes, big-endian, excess top bits cleared.
        if (numBits < 0) {
            throw new IllegalArgumentException("numBits must be non-negative");
        }
        byte[] b = new byte[(int) (((long) numBits + 7) / 8)];
        if (b.length > 0) {
            rnd.nextBytes(b);
            int excess = 8 * b.length - numBits;
            b[0] &= (byte) ((1 << (8 - excess)) - 1);
        }
        BigInteger r = new BigInteger(1, bytesToMag(b));
        this.signum = r.signum;
        this.mag = r.mag;
    }

    public BigInteger(int bitLength, int certainty, Random rnd) {
        BigInteger r = probablePrime(bitLength, rnd);
        this.signum = r.signum;
        this.mag = r.mag;
    }

    private static int[] bytesToMag(byte[] b) {
        int words = (b.length + 3) / 4;
        int[] m = new int[words];
        for (int i = 0; i < b.length; i++) {
            int bi = b.length - 1 - i;
            m[i / 4] |= (b[bi] & 0xff) << ((i % 4) * 8);
        }
        return m;
    }

    private static BigInteger parse(String val, int radix) {
        int len = val.length();
        if (len == 0) {
            throw new NumberFormatException("Zero length BigInteger");
        }
        int i = 0;
        int sign = 1;
        if (val.charAt(0) == '-') {
            sign = -1;
            i = 1;
        } else if (val.charAt(0) == '+') {
            i = 1;
        }
        if (i >= len) {
            throw new NumberFormatException("Zero length BigInteger");
        }
        int[] m = new int[0];
        for (; i < len; i++) {
            int d = Character.digit(val.charAt(i), radix);
            if (d < 0) {
                throw new NumberFormatException("Illegal digit: " + val);
            }
            m = mulAddSmall(m, radix, d);
        }
        return new BigInteger(sign, m);
    }

    private static int[] mulAddSmall(int[] m, int mul, int add) {
        int[] r = new int[m.length + 1];
        long carry = add & MASK;
        for (int i = 0; i < m.length; i++) {
            long p = (m[i] & MASK) * (mul & MASK) + carry;
            r[i] = (int) p;
            carry = p >>> 32;
        }
        r[m.length] = (int) carry;
        return r;
    }

    public static BigInteger valueOf(long val) {
        if (val == 0) {
            return ZERO;
        }
        int sign = val < 0 ? -1 : 1;
        long a = val < 0 ? -val : val;
        if (val == Long.MIN_VALUE) {
            return new BigInteger(-1, new int[] {0, 0x80000000});
        }
        return new BigInteger(sign, new int[] {(int) a, (int) (a >>> 32)});
    }

    private static int cmpMag(int[] a, int[] b) {
        if (a.length != b.length) {
            return a.length < b.length ? -1 : 1;
        }
        for (int i = a.length - 1; i >= 0; i--) {
            long x = a[i] & MASK, y = b[i] & MASK;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] addMag(int[] a, int[] b) {
        if (a.length < b.length) {
            int[] t = a;
            a = b;
            b = t;
        }
        int[] r = new int[a.length + 1];
        long carry = 0;
        for (int i = 0; i < a.length; i++) {
            long s = (a[i] & MASK) + (i < b.length ? b[i] & MASK : 0) + carry;
            r[i] = (int) s;
            carry = s >>> 32;
        }
        r[a.length] = (int) carry;
        return r;
    }

    /** a - b, requires |a| >= |b| */
    private static int[] subMag(int[] a, int[] b) {
        int[] r = new int[a.length];
        long borrow = 0;
        for (int i = 0; i < a.length; i++) {
            long d = (a[i] & MASK) - (i < b.length ? b[i] & MASK : 0) - borrow;
            r[i] = (int) d;
            borrow = d < 0 ? 1 : 0;
        }
        return r;
    }

    private static int[] mulMag(int[] a, int[] b) {
        return nMul(a, b);
    }

    private static int bitLen(int[] m) {
        if (m.length == 0) {
            return 0;
        }
        return (m.length - 1) * 32 + (32 - Integer.numberOfLeadingZeros(m[m.length - 1]));
    }

    private static int[] shiftLeftMag(int[] m, int n) {
        int words = n >>> 5, bits = n & 31;
        int[] r = new int[m.length + words + 1];
        for (int i = 0; i < m.length; i++) {
            long v = (m[i] & MASK) << bits;
            r[i + words] |= (int) v;
            r[i + words + 1] |= (int) (v >>> 32);
        }
        return r;
    }

    private static int[] shiftRightMag(int[] m, int n) {
        int words = n >>> 5, bits = n & 31;
        if (words >= m.length) {
            return new int[0];
        }
        int[] r = new int[m.length - words];
        for (int i = 0; i < r.length; i++) {
            long lo = m[i + words] & MASK;
            long hi = (i + words + 1 < m.length) ? (m[i + words + 1] & MASK) : 0;
            r[i] = (int) (((hi << 32) | lo) >>> bits);
        }
        return r;
    }

    /** returns {quotient, remainder} magnitudes */
    private static int[][] divMag(int[] a, int[] b) {
        if (b.length == 0) {
            throw new ArithmeticException("BigInteger divide by zero");
        }
        int[] rem = new int[b.length];
        int[] q = nDivRem(a, b, rem);
        return new int[][] {q, rem};
    }

    // Magnitude arithmetic in C (src/native/java_math.c): schoolbook multiply, Knuth division,
    // Montgomery modPow. Inputs may have leading zero words; results are normalized by the constructor.
    private static native int[] nMul(int[] a, int[] b);

    private static native int[] nDivRem(int[] a, int[] b, int[] rem);

    private static native int[] nModPow(int[] base, int[] exp, int[] mod);

    public BigInteger add(BigInteger val) {
        if (val.signum == 0) {
            return this;
        }
        if (signum == 0) {
            return val;
        }
        if (val.signum == signum) {
            return new BigInteger(signum, addMag(mag, val.mag));
        }
        int c = cmpMag(mag, val.mag);
        if (c == 0) {
            return ZERO;
        }
        return c > 0 ? new BigInteger(signum, subMag(mag, val.mag)) : new BigInteger(val.signum, subMag(val.mag, mag));
    }

    public BigInteger subtract(BigInteger val) {
        return add(val.negate());
    }

    public BigInteger multiply(BigInteger val) {
        if (signum == 0 || val.signum == 0) {
            return ZERO;
        }
        return new BigInteger(signum * val.signum, mulMag(mag, val.mag));
    }

    public BigInteger divide(BigInteger val) {
        int[][] qr = divMag(mag, val.mag);
        return new BigInteger(signum * val.signum, qr[0]);
    }

    public BigInteger remainder(BigInteger val) {
        int[][] qr = divMag(mag, val.mag);
        return new BigInteger(signum, qr[1]);
    }

    public BigInteger[] divideAndRemainder(BigInteger val) {
        int[][] qr = divMag(mag, val.mag);
        return new BigInteger[] {new BigInteger(signum * val.signum, qr[0]), new BigInteger(signum, qr[1])};
    }

    public BigInteger mod(BigInteger m) {
        if (m.signum <= 0) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        BigInteger r = remainder(m);
        return r.signum >= 0 ? r : r.add(m);
    }

    public BigInteger pow(int exponent) {
        if (exponent < 0) {
            throw new ArithmeticException("Negative exponent");
        }
        BigInteger result = ONE;
        BigInteger base = this;
        while (exponent > 0) {
            if ((exponent & 1) != 0) {
                result = result.multiply(base);
            }
            exponent >>= 1;
            if (exponent > 0) {
                base = base.multiply(base);
            }
        }
        return result;
    }

    public BigInteger modPow(BigInteger exponent, BigInteger m) {
        if (m.signum <= 0) {
            throw new ArithmeticException("BigInteger: modulus not positive");
        }
        if (exponent.signum < 0) {
            return modInverse(m).modPow(exponent.negate(), m);
        }
        return new BigInteger(1, nModPow(mod(m).mag, exponent.mag, m.mag));
    }

    public BigInteger modInverse(BigInteger m) {
        BigInteger a = mod(m), b = m;
        BigInteger x0 = ONE, x1 = ZERO;
        while (b.signum != 0) {
            BigInteger[] qr = a.divideAndRemainder(b);
            BigInteger t = x0.subtract(qr[0].multiply(x1));
            x0 = x1;
            x1 = t;
            a = b;
            b = qr[1];
        }
        if (!a.equals(ONE)) {
            throw new ArithmeticException("BigInteger not invertible.");
        }
        return x0.mod(m);
    }

    public BigInteger gcd(BigInteger val) {
        BigInteger a = abs(), b = val.abs();
        while (b.signum != 0) {
            BigInteger t = a.remainder(b);
            a = b;
            b = t;
        }
        return a;
    }

    public BigInteger sqrt() {
        if (signum < 0) {
            throw new ArithmeticException("Negative BigInteger");
        }
        if (signum == 0) {
            return ZERO;
        }
        BigInteger x = ONE.shiftLeft((bitLength() + 1) / 2);
        while (true) {
            BigInteger y = x.add(divide(x)).shiftRight(1);
            if (y.compareTo(x) >= 0) {
                return x;
            }
            x = y;
        }
    }

    public BigInteger negate() {
        return new BigInteger(-signum, mag);
    }

    public BigInteger abs() {
        return signum >= 0 ? this : negate();
    }

    public int signum() {
        return signum;
    }

    public BigInteger shiftLeft(int n) {
        if (n < 0) {
            return shiftRight(-n);
        }
        if (signum == 0 || n == 0) {
            return this;
        }
        return new BigInteger(signum, shiftLeftMag(mag, n));
    }

    public BigInteger shiftRight(int n) {
        if (n < 0) {
            return shiftLeft(-n);
        }
        if (signum == 0 || n == 0) {
            return this;
        }
        if (signum > 0) {
            return new BigInteger(1, shiftRightMag(mag, n));
        }
        // floor division for negatives
        BigInteger q = new BigInteger(-1, shiftRightMag(mag, n));
        if (!q.shiftLeft(n).equals(this)) {
            q = q.subtract(ONE);
        }
        return q;
    }

    public boolean testBit(int n) {
        if (n < 0) {
            throw new ArithmeticException("Negative bit address");
        }
        if (signum >= 0) {
            int w = n >>> 5;
            return w < mag.length && ((mag[w] >>> (n & 31)) & 1) != 0;
        }
        int[] t = twos(n / 32 + 2);
        return ((t[n >>> 5] >>> (n & 31)) & 1) != 0;
    }

    /** two's complement words (little-endian) with at least 'words' words */
    private int[] twos(int words) {
        int n = Math.max(words, mag.length + 1);
        int[] r = new int[n];
        System.arraycopy(mag, 0, r, 0, mag.length);
        if (signum < 0) {
            for (int i = 0; i < n; i++) {
                r[i] = ~r[i];
            }
            for (int i = 0; i < n; i++) {
                if (++r[i] != 0) {
                    break;
                }
            }
        }
        return r;
    }

    private static BigInteger fromTwos(int[] t) {
        boolean neg = t[t.length - 1] < 0;
        int[] m = t.clone();
        if (neg) {
            for (int i = 0; i < m.length; i++) {
                m[i] = ~m[i];
            }
            for (int i = 0; i < m.length; i++) {
                if (++m[i] != 0) {
                    break;
                }
            }
        }
        return new BigInteger(neg ? -1 : 1, m);
    }

    private BigInteger bitwise(BigInteger val, int op) {
        int n = Math.max(mag.length, val.mag.length) + 1;
        int[] a = twos(n), b = val.twos(n);
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            switch (op) {
                case 0: r[i] = a[i] & b[i]; break;
                case 1: r[i] = a[i] | b[i]; break;
                case 2: r[i] = a[i] ^ b[i]; break;
                default: r[i] = a[i] & ~b[i]; break;
            }
        }
        return fromTwos(r);
    }

    public BigInteger and(BigInteger val) {
        return bitwise(val, 0);
    }

    public BigInteger or(BigInteger val) {
        return bitwise(val, 1);
    }

    public BigInteger xor(BigInteger val) {
        return bitwise(val, 2);
    }

    public BigInteger andNot(BigInteger val) {
        return bitwise(val, 3);
    }

    public BigInteger not() {
        return negate().subtract(ONE);
    }

    public BigInteger setBit(int n) {
        return or(ONE.shiftLeft(n));
    }

    public BigInteger clearBit(int n) {
        return andNot(ONE.shiftLeft(n));
    }

    public BigInteger flipBit(int n) {
        return xor(ONE.shiftLeft(n));
    }

    public int getLowestSetBit() {
        if (signum == 0) {
            return -1;
        }
        int i = 0;
        while (mag[i] == 0) {
            i++;
        }
        return i * 32 + Integer.numberOfTrailingZeros(mag[i]);
    }

    public int bitLength() {
        if (signum >= 0) {
            return bitLen(mag);
        }
        // for negatives: bitLength of (-this - 1)
        return bitLen(negate().subtract(ONE).mag);
    }

    public int bitCount() {
        int c = 0;
        int[] m = signum >= 0 ? mag : negate().subtract(ONE).mag;
        for (int w : m) {
            c += Integer.bitCount(w);
        }
        return c;
    }

    public boolean isProbablePrime(int certainty) {
        BigInteger n = abs();
        if (n.compareTo(TWO) < 0) {
            return false;
        }
        int[] small = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37};
        for (int p : small) {
            BigInteger bp = valueOf(p);
            if (n.equals(bp)) {
                return true;
            }
            if (n.remainder(bp).signum == 0) {
                return false;
            }
        }
        BigInteger d = n.subtract(ONE);
        int s = d.getLowestSetBit();
        d = d.shiftRight(s);
        BigInteger nm1 = n.subtract(ONE);
        // Fixed small bases, then random bases so adversarial composites fail too
        // (error below 4^-rounds; certainty asks for 2^-certainty).
        int rounds = Math.min(Math.max((certainty + 1) / 2, 0), 32);
        BigInteger[] bases = new BigInteger[small.length + rounds];
        for (int i = 0; i < small.length; i++) {
            bases[i] = valueOf(small[i]);
        }
        Random rnd = new Random();
        for (int i = 0; i < rounds; i++) {
            BigInteger a;
            do {
                a = new BigInteger(n.bitLength(), rnd);
            } while (a.compareTo(ONE) <= 0 || a.compareTo(nm1) >= 0);
            bases[small.length + i] = a;
        }
        for (BigInteger base : bases) {
            BigInteger x = base.modPow(d, n);
            if (x.equals(ONE) || x.equals(nm1)) {
                continue;
            }
            boolean composite = true;
            for (int r = 1; r < s; r++) {
                x = x.multiply(x).mod(n);
                if (x.equals(nm1)) {
                    composite = false;
                    break;
                }
            }
            if (composite) {
                return false;
            }
        }
        return true;
    }

    public static BigInteger probablePrime(int bitLength, Random rnd) {
        while (true) {
            BigInteger c = new BigInteger(bitLength, rnd).setBit(bitLength - 1).setBit(0);
            if (c.isProbablePrime(50)) {
                return c;
            }
        }
    }

    public BigInteger nextProbablePrime() {
        BigInteger c = add(ONE);
        if (!c.testBit(0) && !c.equals(TWO)) {
            c = c.add(ONE);
        }
        while (!c.isProbablePrime(50)) {
            c = c.add(TWO);
        }
        return c;
    }

    public int compareTo(BigInteger val) {
        if (signum != val.signum) {
            return signum < val.signum ? -1 : 1;
        }
        int c = cmpMag(mag, val.mag);
        return signum >= 0 ? c : -c;
    }

    public boolean equals(Object x) {
        if (x == this) {
            return true;
        }
        if (!(x instanceof BigInteger)) {
            return false;
        }
        BigInteger b = (BigInteger) x;
        return b.signum == signum && cmpMag(mag, b.mag) == 0;
    }

    public BigInteger min(BigInteger val) {
        return compareTo(val) < 0 ? this : val;
    }

    public BigInteger max(BigInteger val) {
        return compareTo(val) > 0 ? this : val;
    }

    public int hashCode() {
        int h = 0;
        for (int i = mag.length - 1; i >= 0; i--) {
            h = 31 * h + mag[i];
        }
        return h * signum;
    }

    public String toString(int radix) {
        if (signum == 0) {
            return "0";
        }
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            radix = 10;
        }
        StringBuilder sb = new StringBuilder();
        int[] m = mag.clone();
        int len = m.length;
        while (len > 0) {
            long rem = 0;
            for (int i = len - 1; i >= 0; i--) {
                long cur = (rem << 32) | (m[i] & MASK);
                m[i] = (int) (cur / radix);
                rem = cur % radix;
            }
            sb.append(Character.forDigit((int) rem, radix));
            while (len > 0 && m[len - 1] == 0) {
                len--;
            }
        }
        if (signum < 0) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }

    public String toString() {
        return toString(10);
    }

    public byte[] toByteArray() {
        int byteLen = bitLength() / 8 + 1;
        int[] t = twos((byteLen + 3) / 4 + 1);
        byte[] b = new byte[byteLen];
        for (int i = 0; i < byteLen; i++) {
            b[byteLen - 1 - i] = (byte) (t[i / 4] >>> ((i % 4) * 8));
        }
        return b;
    }

    public int intValue() {
        int v = mag.length > 0 ? mag[0] : 0;
        return signum < 0 ? -v : v;
    }

    public long longValue() {
        long v = 0;
        if (mag.length > 0) {
            v = mag[0] & MASK;
        }
        if (mag.length > 1) {
            v |= ((long) mag[1]) << 32;
        }
        return signum < 0 ? -v : v;
    }

    public long longValueExact() {
        if (bitLength() > 63) {
            throw new ArithmeticException("BigInteger out of long range");
        }
        return longValue();
    }

    public int intValueExact() {
        if (bitLength() > 31) {
            throw new ArithmeticException("BigInteger out of int range");
        }
        return intValue();
    }

    public float floatValue() {
        return (float) doubleValue();
    }

    public double doubleValue() {
        if (signum == 0) {
            return 0.0;
        }
        int bl = bitLen(mag);
        if (bl <= 63) {
            return signum * (double) (abs().longValue());
        }
        int shift = bl - 63;
        double top = (double) new BigInteger(1, shiftRightMag(mag, shift)).longValue();
        return signum * top * Math.pow(2, shift);
    }
}
