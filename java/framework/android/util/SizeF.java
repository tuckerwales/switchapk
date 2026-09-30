package android.util;

public final class SizeF {
    private final float mWidth, mHeight;

    public SizeF(float width, float height) {
        mWidth = width;
        mHeight = height;
    }

    public float getWidth() { return mWidth; }
    public float getHeight() { return mHeight; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SizeF && ((SizeF) obj).mWidth == mWidth && ((SizeF) obj).mHeight == mHeight;
    }

    @Override
    public String toString() { return mWidth + "x" + mHeight; }

    @Override
    public int hashCode() { return Float.floatToIntBits(mWidth) ^ Float.floatToIntBits(mHeight); }
}
