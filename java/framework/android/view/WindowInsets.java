package android.view;

import android.graphics.Insets;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Window insets. The Switch has no system bars, IME panel or cutout, so the
 * framework dispatches all-zero insets; apps may build and inset their own.
 */
public final class WindowInsets {
    private static final int TYPE_COUNT = 9;

    public static final WindowInsets CONSUMED = new WindowInsets(new Insets[TYPE_COUNT], new Insets[TYPE_COUNT],
            false, true, true);

    private final Insets[] mTypeInsets;
    private final Insets[] mTypeMaxInsets;
    private final boolean mIsRound;
    private final boolean mSystemWindowInsetsConsumed;
    private final boolean mStableInsetsConsumed;

    WindowInsets(Insets[] typeInsets, Insets[] typeMaxInsets, boolean isRound, boolean systemConsumed,
            boolean stableConsumed) {
        mTypeInsets = new Insets[TYPE_COUNT];
        mTypeMaxInsets = new Insets[TYPE_COUNT];
        for (int i = 0; i < TYPE_COUNT; i++) {
            mTypeInsets[i] = typeInsets != null && typeInsets[i] != null ? typeInsets[i] : Insets.NONE;
            mTypeMaxInsets[i] = typeMaxInsets != null && typeMaxInsets[i] != null ? typeMaxInsets[i] : Insets.NONE;
        }
        mIsRound = isRound;
        mSystemWindowInsetsConsumed = systemConsumed;
        mStableInsetsConsumed = stableConsumed;
    }

    /** framework-internal (hidden in AOSP). Zero insets for a window. */
    public WindowInsets(Rect systemWindowInsets) {
        this(null, null, false, false, false);
        if (systemWindowInsets != null) mTypeInsets[1] = Insets.of(systemWindowInsets);
    }

    public WindowInsets(WindowInsets src) {
        this(src.mTypeInsets, src.mTypeMaxInsets, src.mIsRound, src.mSystemWindowInsetsConsumed,
                src.mStableInsetsConsumed);
    }

    private static Insets union(Insets[] all, int typeMask) {
        Insets r = Insets.NONE;
        for (int i = 0; i < TYPE_COUNT; i++) {
            if ((typeMask & (1 << i)) != 0) r = Insets.max(r, all[i]);
        }
        return r;
    }

    private static final int SYSTEM_WINDOW = Type.STATUS_BARS | Type.NAVIGATION_BARS | Type.CAPTION_BAR | Type.IME
            | Type.DISPLAY_CUTOUT;

    public Insets getSystemWindowInsets() {
        return mSystemWindowInsetsConsumed ? Insets.NONE : union(mTypeInsets, SYSTEM_WINDOW);
    }

    public Insets getInsets(int typeMask) { return union(mTypeInsets, typeMask); }

    public Insets getInsetsIgnoringVisibility(int typeMask) {
        if ((typeMask & Type.IME) != 0) throw new IllegalArgumentException("Unable to query the maximum insets for IME");
        return union(mTypeMaxInsets, typeMask);
    }

    public boolean isVisible(int typeMask) { return true; }

    public int getSystemWindowInsetLeft() { return getSystemWindowInsets().left; }
    public int getSystemWindowInsetTop() { return getSystemWindowInsets().top; }
    public int getSystemWindowInsetRight() { return getSystemWindowInsets().right; }
    public int getSystemWindowInsetBottom() { return getSystemWindowInsets().bottom; }
    public boolean hasSystemWindowInsets() { return !getSystemWindowInsets().equals(Insets.NONE); }

    public boolean hasInsets() {
        for (int i = 0; i < TYPE_COUNT; i++) {
            if (!mTypeInsets[i].equals(Insets.NONE) || !mTypeMaxInsets[i].equals(Insets.NONE)) return true;
        }
        return false;
    }

    public List<Rect> getBoundingRects(int typeMask) { return Collections.emptyList(); }
    public List<Rect> getBoundingRectsIgnoringVisibility(int typeMask) { return Collections.emptyList(); }
    public Rect getPrivacyIndicatorBounds() { return null; }
    public WindowInsets consumeDisplayCutout() { return this; }
    public boolean isConsumed() { return mSystemWindowInsetsConsumed && mStableInsetsConsumed; }
    public boolean isRound() { return mIsRound; }

    public WindowInsets consumeSystemWindowInsets() {
        return new WindowInsets(null, mTypeMaxInsets, mIsRound, true, mStableInsetsConsumed);
    }

    public WindowInsets replaceSystemWindowInsets(int left, int top, int right, int bottom) {
        if (mSystemWindowInsetsConsumed) return this;
        return new Builder(this).setSystemWindowInsets(Insets.of(left, top, right, bottom)).build();
    }

    public WindowInsets replaceSystemWindowInsets(Rect systemWindowInsets) {
        return replaceSystemWindowInsets(systemWindowInsets.left, systemWindowInsets.top, systemWindowInsets.right,
                systemWindowInsets.bottom);
    }

    public Insets getStableInsets() {
        return mStableInsetsConsumed ? Insets.NONE : union(mTypeMaxInsets, SYSTEM_WINDOW);
    }

    public int getStableInsetTop() { return getStableInsets().top; }
    public int getStableInsetLeft() { return getStableInsets().left; }
    public int getStableInsetRight() { return getStableInsets().right; }
    public int getStableInsetBottom() { return getStableInsets().bottom; }
    public boolean hasStableInsets() { return !getStableInsets().equals(Insets.NONE); }
    public Insets getSystemGestureInsets() { return getInsets(Type.SYSTEM_GESTURES); }
    public Insets getMandatorySystemGestureInsets() { return getInsets(Type.MANDATORY_SYSTEM_GESTURES); }
    public Insets getTappableElementInsets() { return getInsets(Type.TAPPABLE_ELEMENT); }

    public WindowInsets consumeStableInsets() {
        return new WindowInsets(mTypeInsets, null, mIsRound, mSystemWindowInsetsConsumed, true);
    }

    public WindowInsets inset(Insets insets) { return inset(insets.left, insets.top, insets.right, insets.bottom); }

    public WindowInsets inset(int left, int top, int right, int bottom) {
        Insets[] a = new Insets[TYPE_COUNT];
        Insets[] b = new Insets[TYPE_COUNT];
        for (int i = 0; i < TYPE_COUNT; i++) {
            a[i] = insetInsets(mTypeInsets[i], left, top, right, bottom);
            b[i] = insetInsets(mTypeMaxInsets[i], left, top, right, bottom);
        }
        return new WindowInsets(a, b, mIsRound, mSystemWindowInsetsConsumed, mStableInsetsConsumed);
    }

    private static Insets insetInsets(Insets insets, int left, int top, int right, int bottom) {
        int newLeft = Math.max(0, insets.left - left);
        int newTop = Math.max(0, insets.top - top);
        int newRight = Math.max(0, insets.right - right);
        int newBottom = Math.max(0, insets.bottom - bottom);
        if (newLeft == left && newTop == top && newRight == right && newBottom == bottom) return insets;
        return Insets.of(newLeft, newTop, newRight, newBottom);
    }

    @Override
    public String toString() {
        return "WindowInsets{systemWindowInsets=" + getSystemWindowInsets() + " stableInsets=" + getStableInsets()
                + (isRound() ? " round" : "") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WindowInsets)) return false;
        WindowInsets that = (WindowInsets) o;
        if (mIsRound != that.mIsRound || mSystemWindowInsetsConsumed != that.mSystemWindowInsetsConsumed
                || mStableInsetsConsumed != that.mStableInsetsConsumed) {
            return false;
        }
        for (int i = 0; i < TYPE_COUNT; i++) {
            if (!mTypeInsets[i].equals(that.mTypeInsets[i])) return false;
            if (!mTypeMaxInsets[i].equals(that.mTypeMaxInsets[i])) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = java.util.Arrays.hashCode(mTypeInsets) * 31 + java.util.Arrays.hashCode(mTypeMaxInsets);
        return h * 31 + (mIsRound ? 1 : 0) + (mSystemWindowInsetsConsumed ? 2 : 0) + (mStableInsetsConsumed ? 4 : 0);
    }

    public static final class Builder {
        private final Insets[] mTypeInsets = new Insets[TYPE_COUNT];
        private final Insets[] mTypeMaxInsets = new Insets[TYPE_COUNT];
        private boolean mSystemInsetsConsumed = true;
        private boolean mStableInsetsConsumed = true;
        private boolean mIsRound;

        public Builder() {}

        public Builder(WindowInsets insets) {
            System.arraycopy(insets.mTypeInsets, 0, mTypeInsets, 0, TYPE_COUNT);
            System.arraycopy(insets.mTypeMaxInsets, 0, mTypeMaxInsets, 0, TYPE_COUNT);
            mSystemInsetsConsumed = insets.mSystemWindowInsetsConsumed;
            mStableInsetsConsumed = insets.mStableInsetsConsumed;
            mIsRound = insets.mIsRound;
        }

        public Builder setSystemWindowInsets(Insets systemWindowInsets) {
            Objects.requireNonNull(systemWindowInsets);
            mTypeInsets[0] = Insets.NONE;
            mTypeInsets[1] = systemWindowInsets;
            for (int i = 2; i < TYPE_COUNT; i++) {
                if ((SYSTEM_WINDOW & (1 << i)) != 0) mTypeInsets[i] = Insets.NONE;
            }
            mSystemInsetsConsumed = false;
            return this;
        }

        public Builder setSystemGestureInsets(Insets insets) { return set(Type.SYSTEM_GESTURES, insets); }
        public Builder setMandatorySystemGestureInsets(Insets insets) { return set(Type.MANDATORY_SYSTEM_GESTURES, insets); }
        public Builder setTappableElementInsets(Insets insets) { return set(Type.TAPPABLE_ELEMENT, insets); }

        private Builder set(int typeMask, Insets insets) {
            Objects.requireNonNull(insets);
            for (int i = 0; i < TYPE_COUNT; i++) if ((typeMask & (1 << i)) != 0) mTypeInsets[i] = insets;
            mSystemInsetsConsumed = false;
            return this;
        }

        public Builder setInsets(int typeMask, Insets insets) { return set(typeMask, insets); }

        public Builder setInsetsIgnoringVisibility(int typeMask, Insets insets) throws IllegalArgumentException {
            if (typeMask == Type.IME) throw new IllegalArgumentException("Maximum inset not available for IME");
            Objects.requireNonNull(insets);
            for (int i = 0; i < TYPE_COUNT; i++) if ((typeMask & (1 << i)) != 0) mTypeMaxInsets[i] = insets;
            mStableInsetsConsumed = false;
            return this;
        }

        public Builder setVisible(int typeMask, boolean visible) { return this; }

        public Builder setStableInsets(Insets stableInsets) {
            Objects.requireNonNull(stableInsets);
            mTypeMaxInsets[1] = stableInsets;
            mStableInsetsConsumed = false;
            return this;
        }

        public Builder setPrivacyIndicatorBounds(Rect bounds) { return this; }
        public Builder setBoundingRects(int typeMask, List<Rect> rects) { return this; }
        public Builder setBoundingRectsIgnoringVisibility(int typeMask, List<Rect> rects) { return this; }
        public Builder setFrame(int width, int height) { return this; }

        /** framework-internal (hidden in AOSP). */
        public Builder setRound(boolean round) {
            mIsRound = round;
            return this;
        }

        public WindowInsets build() {
            return new WindowInsets(mSystemInsetsConsumed ? null : mTypeInsets,
                    mStableInsetsConsumed ? null : mTypeMaxInsets, mIsRound, mSystemInsetsConsumed,
                    mStableInsetsConsumed);
        }
    }

    public static final class Type {
        static final int STATUS_BARS = 1;
        static final int NAVIGATION_BARS = 1 << 1;
        static final int CAPTION_BAR = 1 << 2;
        static final int IME = 1 << 3;
        static final int SYSTEM_GESTURES = 1 << 4;
        static final int MANDATORY_SYSTEM_GESTURES = 1 << 5;
        static final int TAPPABLE_ELEMENT = 1 << 6;
        static final int DISPLAY_CUTOUT = 1 << 7;
        static final int SYSTEM_OVERLAYS = 1 << 8;

        private Type() {}

        public static int statusBars() { return STATUS_BARS; }
        public static int navigationBars() { return NAVIGATION_BARS; }
        public static int captionBar() { return CAPTION_BAR; }
        public static int ime() { return IME; }
        public static int systemGestures() { return SYSTEM_GESTURES; }
        public static int mandatorySystemGestures() { return MANDATORY_SYSTEM_GESTURES; }
        public static int tappableElement() { return TAPPABLE_ELEMENT; }
        public static int displayCutout() { return DISPLAY_CUTOUT; }
        public static int systemOverlays() { return SYSTEM_OVERLAYS; }
        public static int systemBars() { return STATUS_BARS | NAVIGATION_BARS | CAPTION_BAR | SYSTEM_OVERLAYS; }

        /** framework-internal (hidden in AOSP). */
        public static int all() { return 0xffffffff; }
    }
}
