package android.graphics;

public final class Outline {
    public static final int MODE_EMPTY = 0;
    public static final int MODE_ROUND_RECT = 1;
    public static final int MODE_PATH = 2;

    public int mMode = MODE_EMPTY;
    public Path mPath;
    public final Rect mRect = new Rect();
    public float mRadius = Float.NEGATIVE_INFINITY;
    public float mAlpha;

    public Outline() {}
    public Outline(Outline src) { set(src); }

    public void setEmpty() {
        if (mPath != null) mPath.rewind();
        mMode = MODE_EMPTY;
        mRect.setEmpty();
        mRadius = Float.NEGATIVE_INFINITY;
    }

    public boolean isEmpty() { return mMode == MODE_EMPTY; }
    public boolean canClip() { return mMode != MODE_PATH; }
    public void setAlpha(float alpha) { mAlpha = alpha; }
    public float getAlpha() { return mAlpha; }

    public void set(Outline src) {
        mMode = src.mMode;
        if (src.mMode == MODE_PATH) {
            if (mPath == null) mPath = new Path();
            mPath.set(src.mPath);
        }
        mRect.set(src.mRect);
        mRadius = src.mRadius;
        mAlpha = src.mAlpha;
    }

    public void setRect(int left, int top, int right, int bottom) { setRoundRect(left, top, right, bottom, 0.0f); }
    public void setRect(Rect rect) { setRect(rect.left, rect.top, rect.right, rect.bottom); }

    public void setRoundRect(int left, int top, int right, int bottom, float radius) {
        if (left >= right || top >= bottom) {
            setEmpty();
            return;
        }
        mMode = MODE_ROUND_RECT;
        mRect.set(left, top, right, bottom);
        mRadius = radius;
    }

    public void setRoundRect(Rect rect, float radius) { setRoundRect(rect.left, rect.top, rect.right, rect.bottom, radius); }

    public boolean getRect(Rect outRect) {
        if (mMode != MODE_ROUND_RECT) return false;
        outRect.set(mRect);
        return true;
    }

    public float getRadius() { return mRadius; }

    public void setOval(int left, int top, int right, int bottom) {
        if (left >= right || top >= bottom) {
            setEmpty();
            return;
        }
        if ((bottom - top) == (right - left)) {
            setRoundRect(left, top, right, bottom, (bottom - top) / 2.0f);
            return;
        }
        if (mPath == null) mPath = new Path();
        else mPath.rewind();
        mMode = MODE_PATH;
        mPath.addOval(left, top, right, bottom, Path.Direction.CW);
        mRect.setEmpty();
        mRadius = Float.NEGATIVE_INFINITY;
    }

    public void setOval(Rect rect) { setOval(rect.left, rect.top, rect.right, rect.bottom); }

    public void setPath(Path path) {
        if (path.isEmpty()) {
            setEmpty();
            return;
        }
        if (mPath == null) mPath = new Path();
        mMode = MODE_PATH;
        mPath.set(path);
        mRect.setEmpty();
        mRadius = Float.NEGATIVE_INFINITY;
    }

    @Deprecated
    public void setConvexPath(Path convexPath) { setPath(convexPath); }

    public void offset(int dx, int dy) {
        if (mMode == MODE_ROUND_RECT) mRect.offset(dx, dy);
        else if (mMode == MODE_PATH) mPath.offset(dx, dy);
    }
}
