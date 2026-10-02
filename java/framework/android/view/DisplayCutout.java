package android.view;

import android.graphics.Insets;
import android.graphics.Path;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * The non-functional area of a display. The Switch has no cutout, so the
 * insets a window receives have none. Apps and tests can still build one.
 * Cutout path specs are not parsed, so {@link #getCutoutPath()} is null.
 */
public final class DisplayCutout {
    private final Rect mSafeInsets;
    private final Insets mWaterfallInsets;
    private final Rect mBoundLeft;
    private final Rect mBoundTop;
    private final Rect mBoundRight;
    private final Rect mBoundBottom;

    public DisplayCutout(Insets safeInsets, Rect boundLeft, Rect boundTop, Rect boundRight, Rect boundBottom) {
        this(safeInsets, boundLeft, boundTop, boundRight, boundBottom, Insets.NONE);
    }

    public DisplayCutout(Insets safeInsets, Rect boundLeft, Rect boundTop, Rect boundRight, Rect boundBottom,
            Insets waterfallInsets) {
        this(safeInsets.toRect(), waterfallInsets, copyOrEmpty(boundLeft), copyOrEmpty(boundTop),
                copyOrEmpty(boundRight), copyOrEmpty(boundBottom));
    }

    /** @deprecated Use {@link #DisplayCutout(Insets, Rect, Rect, Rect, Rect)}. */
    @Deprecated
    public DisplayCutout(Rect safeInsets, List<Rect> boundingRects) {
        this(safeInsets == null ? new Rect() : new Rect(safeInsets), Insets.NONE,
                extract(safeInsets, boundingRects));
    }

    private DisplayCutout(Rect safeInsets, Insets waterfallInsets, Rect left, Rect top, Rect right, Rect bottom) {
        mSafeInsets = safeInsets;
        mWaterfallInsets = waterfallInsets == null ? Insets.NONE : waterfallInsets;
        mBoundLeft = left;
        mBoundTop = top;
        mBoundRight = right;
        mBoundBottom = bottom;
    }

    private DisplayCutout(Rect safeInsets, Insets waterfallInsets, Rect[] bounds) {
        this(safeInsets, waterfallInsets, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    public Insets getWaterfallInsets() { return mWaterfallInsets; }

    public int getSafeInsetTop() { return mSafeInsets.top; }
    public int getSafeInsetBottom() { return mSafeInsets.bottom; }
    public int getSafeInsetLeft() { return mSafeInsets.left; }
    public int getSafeInsetRight() { return mSafeInsets.right; }

    public List<Rect> getBoundingRects() {
        ArrayList<Rect> out = new ArrayList<Rect>();
        addIfPresent(out, mBoundLeft);
        addIfPresent(out, mBoundTop);
        addIfPresent(out, mBoundRight);
        addIfPresent(out, mBoundBottom);
        return out;
    }

    public Rect getBoundingRectLeft() { return new Rect(mBoundLeft); }
    public Rect getBoundingRectTop() { return new Rect(mBoundTop); }
    public Rect getBoundingRectRight() { return new Rect(mBoundRight); }
    public Rect getBoundingRectBottom() { return new Rect(mBoundBottom); }

    /** No cutout spec is available, so there is no path. */
    public Path getCutoutPath() { return null; }

    /**
     * Moves the cutout frame. Package-private: AOSP hides this and
     * {@link WindowInsets#inset} is the public caller.
     */
    DisplayCutout inset(int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if ((insetLeft == 0 && insetTop == 0 && insetRight == 0 && insetBottom == 0)
                || (boundsEmpty() && mWaterfallInsets.equals(Insets.NONE))) {
            return this;
        }
        Rect safe = insetInsets(insetLeft, insetTop, insetRight, insetBottom, new Rect(mSafeInsets));
        if (insetLeft == 0 && insetTop == 0 && mSafeInsets.equals(safe)) return this;
        Rect water = insetInsets(insetLeft, insetTop, insetRight, insetBottom, mWaterfallInsets.toRect());
        return new DisplayCutout(safe, Insets.of(water), offsetBound(mBoundLeft, insetLeft, insetTop),
                offsetBound(mBoundTop, insetLeft, insetTop), offsetBound(mBoundRight, insetLeft, insetTop),
                offsetBound(mBoundBottom, insetLeft, insetTop));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisplayCutout)) return false;
        DisplayCutout c = (DisplayCutout) o;
        return mSafeInsets.equals(c.mSafeInsets) && mWaterfallInsets.equals(c.mWaterfallInsets)
                && mBoundLeft.equals(c.mBoundLeft) && mBoundTop.equals(c.mBoundTop)
                && mBoundRight.equals(c.mBoundRight) && mBoundBottom.equals(c.mBoundBottom);
    }

    @Override
    public int hashCode() {
        int h = mSafeInsets.hashCode();
        h = h * 31 + mBoundLeft.hashCode();
        h = h * 31 + mBoundTop.hashCode();
        h = h * 31 + mBoundRight.hashCode();
        h = h * 31 + mBoundBottom.hashCode();
        return h * 31 + mWaterfallInsets.hashCode();
    }

    @Override
    public String toString() {
        return "DisplayCutout{insets=" + mSafeInsets + " waterfall=" + mWaterfallInsets
                + " left=" + mBoundLeft + " top=" + mBoundTop + " right=" + mBoundRight
                + " bottom=" + mBoundBottom + "}";
    }

    private boolean boundsEmpty() {
        return mBoundLeft.isEmpty() && mBoundTop.isEmpty() && mBoundRight.isEmpty() && mBoundBottom.isEmpty();
    }

    private static void addIfPresent(List<Rect> out, Rect bound) {
        if (!bound.isEmpty()) out.add(new Rect(bound));
    }

    private static Rect copyOrEmpty(Rect r) { return r == null ? new Rect() : new Rect(r); }

    private static Rect offsetBound(Rect r, int insetLeft, int insetTop) {
        Rect c = new Rect(r);
        if (c.left != 0 || c.top != 0 || c.right != 0 || c.bottom != 0) c.offset(-insetLeft, -insetTop);
        return c;
    }

    /** Safe-inset amounts shrink. A zero amount on an edge that is already zero stays put. */
    private static Rect insetInsets(int insetLeft, int insetTop, int insetRight, int insetBottom, Rect insets) {
        if (insetTop > 0 || insets.top > 0) insets.top = atLeastZero(insets.top - insetTop);
        if (insetBottom > 0 || insets.bottom > 0) insets.bottom = atLeastZero(insets.bottom - insetBottom);
        if (insetLeft > 0 || insets.left > 0) insets.left = atLeastZero(insets.left - insetLeft);
        if (insetRight > 0 || insets.right > 0) insets.right = atLeastZero(insets.right - insetRight);
        return insets;
    }

    private static int atLeastZero(int value) { return value < 0 ? 0 : value; }

    /**
     * Places each rect on the short edges named by {@code safeInsets}. A
     * top or bottom inset sends top==0 rects to the top and the rest to
     * the bottom. Otherwise left==0 rects go left and the rest go right.
     * The last rect on a side wins.
     */
    private static Rect[] extract(Rect safeInsets, List<Rect> boundingRects) {
        Rect[] out = new Rect[] {new Rect(), new Rect(), new Rect(), new Rect()};
        if (safeInsets == null || boundingRects == null) return out;
        boolean topBottom = safeInsets.top > 0 || safeInsets.bottom > 0;
        for (int i = 0; i < boundingRects.size(); i++) {
            Rect bound = boundingRects.get(i);
            Rect copy = new Rect(bound);
            if (topBottom) out[bound.top == 0 ? 1 : 3] = copy;
            else out[bound.left == 0 ? 0 : 2] = copy;
        }
        return out;
    }
}
