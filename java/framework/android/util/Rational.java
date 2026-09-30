package android.util;

public final class Rational extends Number implements Comparable<Rational> {
    private final int mNumerator, mDenominator;

    public Rational(int numerator, int denominator) {
        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }
        int g = gcd(Math.abs(numerator), denominator);
        if (g > 1) {
            numerator /= g;
            denominator /= g;
        }
        mNumerator = numerator;
        mDenominator = denominator;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    public int getNumerator() { return mNumerator; }
    public int getDenominator() { return mDenominator; }
    public double doubleValue() { return (double) mNumerator / mDenominator; }
    public float floatValue() { return (float) mNumerator / mDenominator; }
    public int intValue() { return mDenominator == 0 ? 0 : mNumerator / mDenominator; }
    public long longValue() { return mDenominator == 0 ? 0 : mNumerator / mDenominator; }
    public int compareTo(Rational another) { return Double.compare(doubleValue(), another.doubleValue()); }
    public boolean equals(Object o) { return o instanceof Rational && ((Rational) o).mNumerator == mNumerator && ((Rational) o).mDenominator == mDenominator; }
    public int hashCode() { return mNumerator * 31 + mDenominator; }
    public String toString() { return mNumerator + "/" + mDenominator; }
}
