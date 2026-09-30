package java.math;

public class BigDecimal extends Number implements Comparable<BigDecimal> {
    private final BigInteger intVal;
    private final int scale;

    public static final BigDecimal ZERO = new BigDecimal(BigInteger.ZERO, 0);
    public static final BigDecimal ONE = new BigDecimal(BigInteger.ONE, 0);
    public static final BigDecimal TWO = new BigDecimal(BigInteger.TWO, 0);
    public static final BigDecimal TEN = new BigDecimal(BigInteger.TEN, 0);

    public static final int ROUND_UP = 0;
    public static final int ROUND_DOWN = 1;
    public static final int ROUND_CEILING = 2;
    public static final int ROUND_FLOOR = 3;
    public static final int ROUND_HALF_UP = 4;
    public static final int ROUND_HALF_DOWN = 5;
    public static final int ROUND_HALF_EVEN = 6;
    public static final int ROUND_UNNECESSARY = 7;

    public BigDecimal(BigInteger unscaledVal, int scale) {
        this.intVal = unscaledVal;
        this.scale = scale;
    }

    public BigDecimal(BigInteger val) {
        this(val, 0);
    }

    public BigDecimal(BigInteger unscaledVal, int scale, MathContext mc) {
        BigDecimal r = new BigDecimal(unscaledVal, scale).round(mc);
        intVal = r.intVal;
        this.scale = r.scale;
    }

    public BigDecimal(int val) {
        this(BigInteger.valueOf(val), 0);
    }

    public BigDecimal(long val) {
        this(BigInteger.valueOf(val), 0);
    }

    public BigDecimal(char[] in) {
        this(new String(in));
    }

    public BigDecimal(char[] in, int offset, int len) {
        this(new String(in, offset, len));
    }

    public BigDecimal(String val) {
        String s = val.trim();
        int exp = 0;
        int e = s.indexOf('e');
        if (e < 0) {
            e = s.indexOf('E');
        }
        if (e >= 0) {
            String es = s.substring(e + 1);
            if (es.startsWith("+")) {
                es = es.substring(1);
            }
            exp = Integer.parseInt(es);
            s = s.substring(0, e);
        }
        int dot = s.indexOf('.');
        int sc = 0;
        if (dot >= 0) {
            sc = s.length() - dot - 1;
            s = s.substring(0, dot) + s.substring(dot + 1);
        }
        if (s.isEmpty() || s.equals("-") || s.equals("+")) {
            throw new NumberFormatException("Character array is missing \"exponent\" mark 'e' or 'E'.");
        }
        intVal = new BigInteger(s);
        scale = sc - exp;
    }

    public BigDecimal(String val, MathContext mc) {
        BigDecimal r = new BigDecimal(val).round(mc);
        intVal = r.intVal;
        scale = r.scale;
    }

    public BigDecimal(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            throw new NumberFormatException("Infinite or NaN");
        }
        long bits = Double.doubleToRawLongBits(val);
        int sign = ((bits >> 63) == 0 ? 1 : -1);
        int exponent = (int) ((bits >> 52) & 0x7ffL);
        long significand = (exponent == 0 ? (bits & ((1L << 52) - 1)) << 1 : (bits & ((1L << 52) - 1)) | (1L << 52));
        exponent -= 1075;
        if (significand == 0) {
            intVal = BigInteger.ZERO;
            scale = 0;
            return;
        }
        while ((significand & 1) == 0) {
            significand >>= 1;
            exponent++;
        }
        BigInteger iv = BigInteger.valueOf(sign * significand);
        int sc;
        if (exponent >= 0) {
            iv = iv.shiftLeft(exponent);
            sc = 0;
        } else {
            iv = iv.multiply(BigInteger.valueOf(5).pow(-exponent));
            sc = -exponent;
        }
        intVal = iv;
        scale = sc;
    }

    public BigDecimal(double val, MathContext mc) {
        BigDecimal r = new BigDecimal(val).round(mc);
        intVal = r.intVal;
        scale = r.scale;
    }

    public static BigDecimal valueOf(long unscaledVal, int scale) {
        return new BigDecimal(BigInteger.valueOf(unscaledVal), scale);
    }

    public static BigDecimal valueOf(long val) {
        return new BigDecimal(BigInteger.valueOf(val), 0);
    }

    public static BigDecimal valueOf(double val) {
        return new BigDecimal(Double.toString(val));
    }

    private static BigInteger tenPow(int n) {
        return BigInteger.TEN.pow(n);
    }

    private static BigDecimal[] align(BigDecimal a, BigDecimal b) {
        if (a.scale == b.scale) {
            return new BigDecimal[] {a, b};
        }
        if (a.scale < b.scale) {
            return new BigDecimal[] {new BigDecimal(a.intVal.multiply(tenPow(b.scale - a.scale)), b.scale), b};
        }
        return new BigDecimal[] {a, new BigDecimal(b.intVal.multiply(tenPow(a.scale - b.scale)), a.scale)};
    }

    public BigDecimal add(BigDecimal augend) {
        BigDecimal[] x = align(this, augend);
        return new BigDecimal(x[0].intVal.add(x[1].intVal), x[0].scale);
    }

    public BigDecimal add(BigDecimal augend, MathContext mc) {
        return add(augend).round(mc);
    }

    public BigDecimal subtract(BigDecimal subtrahend) {
        return add(subtrahend.negate());
    }

    public BigDecimal subtract(BigDecimal subtrahend, MathContext mc) {
        return subtract(subtrahend).round(mc);
    }

    public BigDecimal multiply(BigDecimal multiplicand) {
        return new BigDecimal(intVal.multiply(multiplicand.intVal), scale + multiplicand.scale);
    }

    public BigDecimal multiply(BigDecimal multiplicand, MathContext mc) {
        return multiply(multiplicand).round(mc);
    }

    /** Divides unscaled a by b with rounding. */
    private static BigInteger divideRound(BigInteger a, BigInteger b, RoundingMode mode) {
        BigInteger[] qr = a.divideAndRemainder(b);
        BigInteger q = qr[0];
        BigInteger r = qr[1];
        if (r.signum() == 0) {
            return q;
        }
        int sign = a.signum() * b.signum();
        int cmpHalf = r.abs().shiftLeft(1).compareTo(b.abs());
        boolean increment;
        switch (mode) {
            case UP: increment = true; break;
            case DOWN: increment = false; break;
            case CEILING: increment = sign > 0; break;
            case FLOOR: increment = sign < 0; break;
            case HALF_UP: increment = cmpHalf >= 0; break;
            case HALF_DOWN: increment = cmpHalf > 0; break;
            case HALF_EVEN: increment = cmpHalf > 0 || (cmpHalf == 0 && q.testBit(0)); break;
            default: throw new ArithmeticException("Rounding necessary");
        }
        return increment ? q.add(BigInteger.valueOf(sign)) : q;
    }

    public BigDecimal divide(BigDecimal divisor, int scale, RoundingMode roundingMode) {
        if (divisor.intVal.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        // result = this / divisor with 'scale' digits: (intVal * 10^(scale + divisor.scale - this.scale)) / divisor.intVal
        int shift = scale + divisor.scale - this.scale;
        BigInteger num = intVal;
        BigInteger den = divisor.intVal;
        if (shift >= 0) {
            num = num.multiply(tenPow(shift));
        } else {
            den = den.multiply(tenPow(-shift));
        }
        return new BigDecimal(divideRound(num, den, roundingMode), scale);
    }

    public BigDecimal divide(BigDecimal divisor, int scale, int roundingMode) {
        return divide(divisor, scale, RoundingMode.valueOf(roundingMode));
    }

    public BigDecimal divide(BigDecimal divisor, RoundingMode roundingMode) {
        return divide(divisor, scale, roundingMode);
    }

    public BigDecimal divide(BigDecimal divisor, int roundingMode) {
        return divide(divisor, scale, RoundingMode.valueOf(roundingMode));
    }

    public BigDecimal divide(BigDecimal divisor) {
        if (divisor.intVal.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        int preferred = scale - divisor.scale;
        for (int extra = 0; extra < 400; extra++) {
            int sc = Math.max(preferred, 0) + extra;
            try {
                BigDecimal r = divide(divisor, sc, RoundingMode.UNNECESSARY);
                return r.stripToScale(preferred);
            } catch (ArithmeticException e) {
                // need more digits
            }
        }
        throw new ArithmeticException("Non-terminating decimal expansion; no exact representable decimal result.");
    }

    private BigDecimal stripToScale(int preferred) {
        BigDecimal r = this;
        while (r.scale > preferred) {
            BigInteger[] qr = r.intVal.divideAndRemainder(BigInteger.TEN);
            if (qr[1].signum() != 0) {
                break;
            }
            r = new BigDecimal(qr[0], r.scale - 1);
        }
        return r;
    }

    public BigDecimal divide(BigDecimal divisor, MathContext mc) {
        if (mc.getPrecision() == 0) {
            return divide(divisor);
        }
        int sc = mc.getPrecision() + Math.max(0, divisor.precision() - precision()) + scale - divisor.scale + 3;
        return divide(divisor, Math.max(sc, mc.getPrecision()), mc.getRoundingMode()).round(mc);
    }

    public BigDecimal divideToIntegralValue(BigDecimal divisor) {
        return divide(divisor, 0, RoundingMode.DOWN);
    }

    public BigDecimal remainder(BigDecimal divisor) {
        return subtract(divideToIntegralValue(divisor).multiply(divisor));
    }

    public BigDecimal[] divideAndRemainder(BigDecimal divisor) {
        BigDecimal q = divideToIntegralValue(divisor);
        return new BigDecimal[] {q, subtract(q.multiply(divisor))};
    }

    public BigDecimal pow(int n) {
        return new BigDecimal(intVal.pow(n), scale * n);
    }

    public BigDecimal pow(int n, MathContext mc) {
        return pow(n).round(mc);
    }

    public BigDecimal sqrt(MathContext mc) {
        double d = Math.sqrt(doubleValue());
        return new BigDecimal(d).round(mc);
    }

    public BigDecimal abs() {
        return signum() < 0 ? negate() : this;
    }

    public BigDecimal abs(MathContext mc) {
        return abs().round(mc);
    }

    public BigDecimal negate() {
        return new BigDecimal(intVal.negate(), scale);
    }

    public BigDecimal negate(MathContext mc) {
        return negate().round(mc);
    }

    public BigDecimal plus() {
        return this;
    }

    public BigDecimal plus(MathContext mc) {
        return round(mc);
    }

    public int signum() {
        return intVal.signum();
    }

    public int scale() {
        return scale;
    }

    public int precision() {
        if (intVal.signum() == 0) {
            return 1;
        }
        return intVal.abs().toString().length();
    }

    public BigInteger unscaledValue() {
        return intVal;
    }

    public BigDecimal round(MathContext mc) {
        int prec = mc.getPrecision();
        if (prec == 0) {
            return this;
        }
        int drop = precision() - prec;
        if (drop <= 0) {
            return this;
        }
        BigDecimal r = new BigDecimal(divideRound(intVal, tenPow(drop), mc.getRoundingMode()), scale - drop);
        if (r.precision() > prec) {
            return r.round(mc);
        }
        return r;
    }

    public BigDecimal setScale(int newScale, RoundingMode roundingMode) {
        if (newScale == scale) {
            return this;
        }
        if (newScale > scale) {
            return new BigDecimal(intVal.multiply(tenPow(newScale - scale)), newScale);
        }
        return new BigDecimal(divideRound(intVal, tenPow(scale - newScale), roundingMode), newScale);
    }

    public BigDecimal setScale(int newScale, int roundingMode) {
        return setScale(newScale, RoundingMode.valueOf(roundingMode));
    }

    public BigDecimal setScale(int newScale) {
        return setScale(newScale, RoundingMode.UNNECESSARY);
    }

    public BigDecimal movePointLeft(int n) {
        BigDecimal r = new BigDecimal(intVal, scale + n);
        return r.scale < 0 ? r.setScale(0) : r;
    }

    public BigDecimal movePointRight(int n) {
        BigDecimal r = new BigDecimal(intVal, scale - n);
        return r.scale < 0 ? r.setScale(0) : r;
    }

    public BigDecimal scaleByPowerOfTen(int n) {
        return new BigDecimal(intVal, scale - n);
    }

    public BigDecimal stripTrailingZeros() {
        if (intVal.signum() == 0) {
            return ZERO;
        }
        BigDecimal r = this;
        while (true) {
            BigInteger[] qr = r.intVal.divideAndRemainder(BigInteger.TEN);
            if (qr[1].signum() != 0) {
                return r;
            }
            r = new BigDecimal(qr[0], r.scale - 1);
        }
    }

    public int compareTo(BigDecimal val) {
        BigDecimal[] x = align(this, val);
        return x[0].intVal.compareTo(x[1].intVal);
    }

    public boolean equals(Object x) {
        if (!(x instanceof BigDecimal)) {
            return false;
        }
        BigDecimal b = (BigDecimal) x;
        return scale == b.scale && intVal.equals(b.intVal);
    }

    public BigDecimal min(BigDecimal val) {
        return compareTo(val) <= 0 ? this : val;
    }

    public BigDecimal max(BigDecimal val) {
        return compareTo(val) >= 0 ? this : val;
    }

    public int hashCode() {
        return 31 * intVal.hashCode() + scale;
    }

    public String toString() {
        String coeff = intVal.abs().toString();
        long adjusted = -(long) scale + (coeff.length() - 1);
        StringBuilder sb = new StringBuilder();
        if (intVal.signum() < 0) {
            sb.append('-');
        }
        if (scale >= 0 && adjusted >= -6) {
            sb.append(plainDigits(coeff));
            return sb.toString();
        }
        sb.append(coeff.charAt(0));
        if (coeff.length() > 1) {
            sb.append('.').append(coeff, 1, coeff.length());
        }
        sb.append('E');
        if (adjusted > 0) {
            sb.append('+');
        }
        sb.append(adjusted);
        return sb.toString();
    }

    private String plainDigits(String coeff) {
        if (scale <= 0) {
            StringBuilder sb = new StringBuilder(coeff);
            if (intVal.signum() != 0) {
                for (int i = 0; i < -scale; i++) {
                    sb.append('0');
                }
            }
            return sb.toString();
        }
        if (coeff.length() > scale) {
            return coeff.substring(0, coeff.length() - scale) + "." + coeff.substring(coeff.length() - scale);
        }
        StringBuilder sb = new StringBuilder("0.");
        for (int i = coeff.length(); i < scale; i++) {
            sb.append('0');
        }
        return sb.append(coeff).toString();
    }

    public String toPlainString() {
        String s = plainDigits(intVal.abs().toString());
        return intVal.signum() < 0 ? "-" + s : s;
    }

    public String toEngineeringString() {
        return toString();
    }

    public BigInteger toBigInteger() {
        return setScale(0, RoundingMode.DOWN).intVal;
    }

    public BigInteger toBigIntegerExact() {
        return setScale(0, RoundingMode.UNNECESSARY).intVal;
    }

    public long longValue() {
        return toBigInteger().longValue();
    }

    public long longValueExact() {
        return toBigIntegerExact().longValueExact();
    }

    public int intValue() {
        return toBigInteger().intValue();
    }

    public int intValueExact() {
        return toBigIntegerExact().intValueExact();
    }

    public short shortValueExact() {
        return (short) intValueExact();
    }

    public byte byteValueExact() {
        return (byte) intValueExact();
    }

    public float floatValue() {
        return Float.parseFloat(toString());
    }

    public double doubleValue() {
        return Double.parseDouble(toString());
    }

    public BigDecimal ulp() {
        return new BigDecimal(BigInteger.ONE, scale);
    }
}
