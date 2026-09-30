package android.util;

public final class Range<T extends Comparable<? super T>> {
    private final T mLower, mUpper;

    public Range(T lower, T upper) {
        if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("lower must be less than or equal to upper");
        mLower = lower;
        mUpper = upper;
    }

    public static <T extends Comparable<? super T>> Range<T> create(T lower, T upper) { return new Range<T>(lower, upper); }
    public T getLower() { return mLower; }
    public T getUpper() { return mUpper; }
    public boolean contains(T value) { return value.compareTo(mLower) >= 0 && value.compareTo(mUpper) <= 0; }
    public boolean contains(Range<T> range) { return range.mLower.compareTo(mLower) >= 0 && range.mUpper.compareTo(mUpper) <= 0; }

    public T clamp(T value) {
        if (value.compareTo(mLower) < 0) return mLower;
        if (value.compareTo(mUpper) > 0) return mUpper;
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Range && mLower.equals(((Range<?>) obj).mLower) && mUpper.equals(((Range<?>) obj).mUpper);
    }

    @Override
    public int hashCode() { return mLower.hashCode() * 31 + mUpper.hashCode(); }

    @Override
    public String toString() { return "[" + mLower + ", " + mUpper + "]"; }
}
