package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.NinePatch;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.AttributeSet;
import android.util.StateSet;
import android.util.TypedValue;
import android.util.Xml;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public abstract class Drawable {
    private static final Rect ZERO_BOUNDS_RECT = new Rect();
    static final PorterDuff.Mode DEFAULT_TINT_MODE = PorterDuff.Mode.SRC_IN;
    static final BlendMode DEFAULT_BLEND_MODE = BlendMode.SRC_IN;

    private int[] mStateSet = StateSet.WILD_CARD;
    private int mLevel = 0;
    private int mChangingConfigurations = 0;
    private Rect mBounds = ZERO_BOUNDS_RECT;
    private WeakReference<Callback> mCallback = null;
    private boolean mVisible = true;
    private int mLayoutDirection;
    protected int mSrcDensityOverride = 0;

    public abstract void draw(Canvas canvas);

    public void setBounds(int left, int top, int right, int bottom) {
        Rect oldBounds = mBounds;
        if (oldBounds == ZERO_BOUNDS_RECT) oldBounds = mBounds = new Rect();
        if (oldBounds.left != left || oldBounds.top != top || oldBounds.right != right || oldBounds.bottom != bottom) {
            if (!oldBounds.isEmpty()) invalidateSelf();
            mBounds.set(left, top, right, bottom);
            onBoundsChange(mBounds);
        }
    }

    public void setBounds(Rect bounds) { setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom); }
    public final void copyBounds(Rect bounds) { bounds.set(mBounds); }
    public final Rect copyBounds() { return new Rect(mBounds); }

    public final Rect getBounds() {
        if (mBounds == ZERO_BOUNDS_RECT) mBounds = new Rect();
        return mBounds;
    }

    public Rect getDirtyBounds() { return getBounds(); }
    public void setChangingConfigurations(int configs) { mChangingConfigurations = configs; }
    public int getChangingConfigurations() { return mChangingConfigurations; }
    @Deprecated public void setDither(boolean dither) {}
    public void setFilterBitmap(boolean filter) {}
    public boolean isFilterBitmap() { return false; }

    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    public final void setCallback(Callback cb) { mCallback = cb != null ? new WeakReference<Callback>(cb) : null; }
    public Callback getCallback() { return mCallback != null ? mCallback.get() : null; }

    public void invalidateSelf() {
        final Callback callback = getCallback();
        if (callback != null) callback.invalidateDrawable(this);
    }

    public void scheduleSelf(Runnable what, long when) {
        final Callback callback = getCallback();
        if (callback != null) callback.scheduleDrawable(this, what, when);
    }

    public void unscheduleSelf(Runnable what) {
        final Callback callback = getCallback();
        if (callback != null) callback.unscheduleDrawable(this, what);
    }

    public int getLayoutDirection() { return mLayoutDirection; }

    public final boolean setLayoutDirection(int layoutDirection) {
        if (mLayoutDirection != layoutDirection) {
            mLayoutDirection = layoutDirection;
            return onLayoutDirectionChanged(layoutDirection);
        }
        return false;
    }

    public boolean onLayoutDirectionChanged(int layoutDirection) { return false; }
    public abstract void setAlpha(int alpha);
    public int getAlpha() { return 0xFF; }
    public void setXfermode(android.graphics.Xfermode mode) {}
    public abstract void setColorFilter(ColorFilter colorFilter);

    @Deprecated
    public void setColorFilter(int color, PorterDuff.Mode mode) {
        if (getColorFilter() instanceof PorterDuffColorFilter) {
            PorterDuffColorFilter existing = (PorterDuffColorFilter) getColorFilter();
            if (existing.getColor() == color && existing.getMode() == mode) return;
        }
        setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    public void setTint(int tintColor) { setTintList(ColorStateList.valueOf(tintColor)); }
    public void setTintList(ColorStateList tint) {}
    public void setTintMode(PorterDuff.Mode tintMode) {}
    public void setTintBlendMode(BlendMode blendMode) {}
    public ColorFilter getColorFilter() { return null; }
    public void clearColorFilter() { setColorFilter(null); }
    public void setHotspot(float x, float y) {}
    public void setHotspotBounds(int left, int top, int right, int bottom) {}
    public void getHotspotBounds(Rect outRect) { outRect.set(getBounds()); }
    public boolean isProjected() { return false; }
    public boolean isStateful() { return false; }
    public boolean hasFocusStateSpecified() { return false; }

    public boolean setState(final int[] stateSet) {
        if (!java.util.Arrays.equals(mStateSet, stateSet)) {
            mStateSet = stateSet;
            return onStateChange(stateSet);
        }
        return false;
    }

    public int[] getState() { return mStateSet; }
    public void jumpToCurrentState() {}
    public Drawable getCurrent() { return this; }

    public final boolean setLevel(int level) {
        if (mLevel != level) {
            mLevel = level;
            return onLevelChange(level);
        }
        return false;
    }

    public final int getLevel() { return mLevel; }

    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = mVisible != visible;
        if (changed) {
            mVisible = visible;
            invalidateSelf();
        }
        return changed;
    }

    public final boolean isVisible() { return mVisible; }
    public void setAutoMirrored(boolean mirrored) {}
    public boolean isAutoMirrored() { return false; }
    public void applyTheme(Theme t) {}
    public boolean canApplyTheme() { return false; }
    @Deprecated public abstract int getOpacity();

    public static int resolveOpacity(int op1, int op2) {
        if (op1 == op2) return op1;
        if (op1 == PixelFormat.UNKNOWN || op2 == PixelFormat.UNKNOWN) return PixelFormat.UNKNOWN;
        if (op1 == PixelFormat.TRANSLUCENT || op2 == PixelFormat.TRANSLUCENT) return PixelFormat.TRANSLUCENT;
        if (op1 == PixelFormat.TRANSPARENT || op2 == PixelFormat.TRANSPARENT) return PixelFormat.TRANSPARENT;
        return PixelFormat.OPAQUE;
    }

    public Region getTransparentRegion() { return null; }
    protected boolean onStateChange(int[] state) { return false; }
    protected boolean onLevelChange(int level) { return false; }
    protected void onBoundsChange(Rect bounds) {}
    public int getIntrinsicWidth() { return -1; }
    public int getIntrinsicHeight() { return -1; }

    public int getMinimumWidth() {
        final int intrinsicWidth = getIntrinsicWidth();
        return intrinsicWidth > 0 ? intrinsicWidth : 0;
    }

    public int getMinimumHeight() {
        final int intrinsicHeight = getIntrinsicHeight();
        return intrinsicHeight > 0 ? intrinsicHeight : 0;
    }

    public boolean getPadding(Rect padding) {
        padding.set(0, 0, 0, 0);
        return false;
    }

    public Insets getOpticalInsets() { return Insets.NONE; }

    public void getOutline(Outline outline) {
        outline.setRect(getBounds());
        outline.setAlpha(0);
    }

    public Drawable mutate() { return this; }
    public void clearMutated() {}

    public static Drawable createFromStream(InputStream is, String srcName) { return createFromResourceStream(null, null, is, srcName, null); }

    public static Drawable createFromResourceStream(Resources res, TypedValue value, InputStream is, String srcName) {
        return createFromResourceStream(res, value, is, srcName, null);
    }

    public static Drawable createFromResourceStream(Resources res, TypedValue value, InputStream is, String srcName, BitmapFactory.Options opts) {
        if (is == null) return null;
        if (opts == null) opts = new BitmapFactory.Options();
        opts.inScreenDensity = res != null ? res.getDisplayMetrics().densityDpi : Bitmap.DENSITY_NONE;
        Rect pad = new Rect();
        Bitmap bm = BitmapFactory.decodeResourceStream(res, value, is, pad, opts);
        if (bm != null) {
            byte[] np = bm.getNinePatchChunk();
            if (np == null || !NinePatch.isNinePatchChunk(np)) {
                np = null;
                pad = null;
            }
            if (np != null) return new NinePatchDrawable(res, bm, np, pad, srcName);
            return new BitmapDrawable(res, bm);
        }
        return null;
    }

    public static Drawable createFromXml(Resources r, XmlPullParser parser) throws XmlPullParserException, IOException {
        return createFromXml(r, parser, null);
    }

    public static Drawable createFromXml(Resources r, XmlPullParser parser, Theme theme) throws XmlPullParserException, IOException {
        return createFromXmlForDensity(r, parser, 0, theme);
    }

    public static Drawable createFromXmlForDensity(Resources r, XmlPullParser parser, int density, Theme theme)
            throws XmlPullParserException, IOException {
        AttributeSet attrs = Xml.asAttributeSet(parser);
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
        if (type != XmlPullParser.START_TAG) throw new XmlPullParserException("No start tag found");
        Drawable drawable = createFromXmlInnerForDensity(r, parser, attrs, density, theme);
        if (drawable == null) throw new RuntimeException("Unknown initial tag: " + parser.getName());
        return drawable;
    }

    public static Drawable createFromXmlInner(Resources r, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
        return createFromXmlInner(r, parser, attrs, null);
    }

    public static Drawable createFromXmlInner(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme) throws XmlPullParserException, IOException {
        return createFromXmlInnerForDensity(r, parser, attrs, 0, theme);
    }

    static Drawable createFromXmlInnerForDensity(Resources r, XmlPullParser parser, AttributeSet attrs, int density, Theme theme)
            throws XmlPullParserException, IOException {
        final String name = parser.getName();
        Drawable drawable = DrawableInflater.inflateFromTag(name);
        if (drawable == null) {
            try {
                Class<?> clazz = Class.forName(name);
                drawable = (Drawable) clazz.newInstance();
            } catch (Exception e) {
                throw new XmlPullParserException(parser.getPositionDescription() + ": invalid drawable tag " + name);
            }
        }
        drawable.mSrcDensityOverride = density;
        drawable.inflate(r, parser, attrs, theme);
        return drawable;
    }

    public static Drawable createFromPath(String pathName) {
        if (pathName == null) return null;
        Bitmap bm = BitmapFactory.decodeFile(pathName);
        if (bm != null) return new BitmapDrawable(null, bm);
        return null;
    }

    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {
        inflate(r, parser, attrs, null);
    }

    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.visible});
        mVisible = a.getBoolean(0, mVisible);
        a.recycle();
    }

    void inflateWithAttributes(Resources r, XmlPullParser parser, TypedArray attrs, int visibleAttr) {
        mVisible = attrs.getBoolean(visibleAttr, mVisible);
    }

    public ConstantState getConstantState() { return null; }

    public static abstract class ConstantState {
        public abstract Drawable newDrawable();
        public Drawable newDrawable(Resources res) { return newDrawable(); }
        public Drawable newDrawable(Resources res, Theme theme) { return newDrawable(res); }
        public abstract int getChangingConfigurations();
        public boolean canApplyTheme() { return false; }
    }

    protected static TypedArray obtainAttributes(Resources res, Theme theme, AttributeSet set, int[] attrs) {
        if (theme == null) return res.obtainAttributes(set, attrs);
        return theme.obtainStyledAttributes(set, attrs, 0, 0);
    }

    /** Tint helper: returns a filter for the tint color in the current state (or null). */
    PorterDuffColorFilter updateTintFilter(PorterDuffColorFilter tintFilter, ColorStateList tint, PorterDuff.Mode tintMode) {
        if (tint == null || tintMode == null) return null;
        final int color = tint.getColorForState(getState(), tint.getDefaultColor());
        return new PorterDuffColorFilter(color, tintMode);
    }

    public static PorterDuff.Mode parseTintMode(int value, PorterDuff.Mode defaultMode) {
        switch (value) {
            case 3: return PorterDuff.Mode.SRC_OVER;
            case 5: return PorterDuff.Mode.SRC_IN;
            case 9: return PorterDuff.Mode.SRC_ATOP;
            case 14: return PorterDuff.Mode.MULTIPLY;
            case 15: return PorterDuff.Mode.SCREEN;
            case 16: return PorterDuff.Mode.ADD;
            default: return defaultMode;
        }
    }

    static int scaleFromDensity(int pixels, int sourceDensity, int targetDensity, boolean isSize) {
        if (pixels == 0 || sourceDensity == targetDensity || sourceDensity == 0 || targetDensity == 0) return pixels;
        final float result = pixels * targetDensity / (float) sourceDensity;
        if (!isSize) return (int) result;
        final int rounded = Math.round(result);
        if (rounded != 0) return rounded;
        else if (pixels > 0) return 1;
        else return -1;
    }

    static int resolveDensity(Resources r, int parentDensity) {
        final int densityDpi = r == null ? parentDensity : r.getDisplayMetrics().densityDpi;
        return densityDpi == 0 ? android.util.DisplayMetrics.DENSITY_DEFAULT : densityDpi;
    }
}
