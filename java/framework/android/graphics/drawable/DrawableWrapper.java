package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public abstract class DrawableWrapper extends Drawable implements Drawable.Callback {
    Drawable mDrawable;
    private boolean mMutated;

    public DrawableWrapper(Drawable dr) { setDrawable(dr); }

    public void setDrawable(Drawable dr) {
        if (mDrawable != null) mDrawable.setCallback(null);
        mDrawable = dr;
        if (dr != null) {
            dr.setCallback(this);
            dr.setVisible(isVisible(), true);
            dr.setState(getState());
            dr.setLevel(getLevel());
            dr.setBounds(getBounds());
            dr.setLayoutDirection(getLayoutDirection());
        }
        invalidateSelf();
    }

    public Drawable getDrawable() { return mDrawable; }

    /** Inflates a child drawable from either android:drawable or the first child element. */
    void inflateChildDrawable(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        Drawable dr = null;
        int type;
        final int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.START_TAG) dr = Drawable.createFromXmlInnerForDensity(r, parser, attrs, mSrcDensityOverride, theme);
        }
        if (dr != null) setDrawable(dr);
    }

    void inflateDrawableAttr(Resources r, TypedArray a, int index, Resources.Theme theme) {
        Drawable dr = a.getDrawable(index);
        if (dr != null) setDrawable(dr);
    }

    @Override
    public void draw(Canvas canvas) { if (mDrawable != null) mDrawable.draw(canvas); }

    @Override
    public int getChangingConfigurations() { return super.getChangingConfigurations() | (mDrawable != null ? mDrawable.getChangingConfigurations() : 0); }

    @Override
    public boolean getPadding(Rect padding) { return mDrawable != null && mDrawable.getPadding(padding); }

    @Override
    public Insets getOpticalInsets() { return mDrawable != null ? mDrawable.getOpticalInsets() : Insets.NONE; }

    @Override
    public void setHotspot(float x, float y) { if (mDrawable != null) mDrawable.setHotspot(x, y); }
    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) { if (mDrawable != null) mDrawable.setHotspotBounds(left, top, right, bottom); }
    @Override
    public void getHotspotBounds(Rect outRect) { if (mDrawable != null) mDrawable.getHotspotBounds(outRect); else outRect.set(getBounds()); }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean superChanged = super.setVisible(visible, restart);
        final boolean changed = mDrawable != null && mDrawable.setVisible(visible, restart);
        return superChanged | changed;
    }

    @Override
    public void setAlpha(int alpha) { if (mDrawable != null) mDrawable.setAlpha(alpha); }
    @Override
    public int getAlpha() { return mDrawable != null ? mDrawable.getAlpha() : 255; }
    @Override
    public void setColorFilter(ColorFilter colorFilter) { if (mDrawable != null) mDrawable.setColorFilter(colorFilter); }
    @Override
    public ColorFilter getColorFilter() { return mDrawable != null ? mDrawable.getColorFilter() : null; }
    @Override
    public void setTintList(ColorStateList tint) { if (mDrawable != null) mDrawable.setTintList(tint); }
    @Override
    public void setTintMode(PorterDuff.Mode tintMode) { if (mDrawable != null) mDrawable.setTintMode(tintMode); }
    @Override
    public void setTintBlendMode(BlendMode blendMode) { if (mDrawable != null) mDrawable.setTintBlendMode(blendMode); }
    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) { return mDrawable != null && mDrawable.setLayoutDirection(layoutDirection); }
    @Override
    public int getOpacity() { return mDrawable != null ? mDrawable.getOpacity() : PixelFormat.TRANSPARENT; }
    @Override
    public boolean isStateful() { return mDrawable != null && mDrawable.isStateful(); }
    @Override
    public boolean hasFocusStateSpecified() { return mDrawable != null && mDrawable.hasFocusStateSpecified(); }

    @Override
    protected boolean onStateChange(int[] state) {
        if (mDrawable != null && mDrawable.isStateful()) {
            final boolean changed = mDrawable.setState(state);
            if (changed) onBoundsChange(getBounds());
            return changed;
        }
        return false;
    }

    @Override
    public void jumpToCurrentState() { if (mDrawable != null) mDrawable.jumpToCurrentState(); }

    @Override
    protected boolean onLevelChange(int level) { return mDrawable != null && mDrawable.setLevel(level); }

    @Override
    protected void onBoundsChange(Rect bounds) { if (mDrawable != null) mDrawable.setBounds(bounds); }

    @Override
    public int getIntrinsicWidth() { return mDrawable != null ? mDrawable.getIntrinsicWidth() : -1; }
    @Override
    public int getIntrinsicHeight() { return mDrawable != null ? mDrawable.getIntrinsicHeight() : -1; }

    @Override
    public void getOutline(Outline outline) {
        if (mDrawable != null) mDrawable.getOutline(outline);
        else super.getOutline(outline);
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            if (mDrawable != null) mDrawable.mutate();
            mMutated = true;
        }
        return this;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        final Callback callback = getCallback();
        if (callback != null) callback.invalidateDrawable(this);
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        final Callback callback = getCallback();
        if (callback != null) callback.scheduleDrawable(this, what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        final Callback callback = getCallback();
        if (callback != null) callback.unscheduleDrawable(this, what);
    }

    @Override
    public ConstantState getConstantState() {
        final Drawable.ConstantState cs = mDrawable != null ? mDrawable.getConstantState() : null;
        if (cs == null) return null;
        final DrawableWrapper self = this;
        return new ConstantState() {
            public Drawable newDrawable() { return self; }
            public int getChangingConfigurations() { return 0; }
        };
    }
}
