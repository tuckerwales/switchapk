package android.graphics;

public class PorterDuffColorFilter extends ColorFilter {
    private final int mSrcColor;
    private final PorterDuff.Mode mPdMode;

    public PorterDuffColorFilter(int color, PorterDuff.Mode mode) {
        mSrcColor = color;
        mPdMode = mode;
        mMode = mode.nativeInt;
        mColor = color;
    }

    public int getColor() { return mSrcColor; }
    public PorterDuff.Mode getMode() { return mPdMode; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        final PorterDuffColorFilter other = (PorterDuffColorFilter) object;
        return (mSrcColor == other.mSrcColor && mPdMode.nativeInt == other.mPdMode.nativeInt);
    }

    @Override
    public int hashCode() { return 31 * mPdMode.hashCode() + mSrcColor; }
}
