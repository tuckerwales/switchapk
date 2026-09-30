package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ColorDrawable extends Drawable {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ColorState mColorState;
    private PorterDuffColorFilter mTintFilter;
    private boolean mMutated;

    public ColorDrawable() { mColorState = new ColorState(); }

    public ColorDrawable(int color) {
        mColorState = new ColorState();
        setColor(color);
    }

    private ColorDrawable(ColorState state) { mColorState = state; }

    @Override
    public int getChangingConfigurations() { return super.getChangingConfigurations() | mColorState.mChangingConfigurations; }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mColorState = new ColorState(mColorState);
            mMutated = true;
        }
        return this;
    }

    @Override
    public void draw(Canvas canvas) {
        final ColorFilter colorFilter = mPaint.getColorFilter();
        if ((mColorState.mUseColor >>> 24) != 0 || colorFilter != null || mTintFilter != null) {
            if (colorFilter == null) mPaint.setColorFilter(mTintFilter);
            mPaint.setColor(mColorState.mUseColor);
            canvas.drawRect(getBounds(), mPaint);
            mPaint.setColorFilter(colorFilter);
        }
    }

    public int getColor() { return mColorState.mUseColor; }

    public void setColor(int color) {
        if (mColorState.mBaseColor != color || mColorState.mUseColor != color) {
            mColorState.mBaseColor = mColorState.mUseColor = color;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() { return mColorState.mUseColor >>> 24; }

    @Override
    public void setAlpha(int alpha) {
        alpha += alpha >> 7;
        final int baseAlpha = mColorState.mBaseColor >>> 24;
        final int useAlpha = baseAlpha * alpha >> 8;
        final int useColor = (mColorState.mBaseColor << 8 >>> 8) | (useAlpha << 24);
        if (mColorState.mUseColor != useColor) {
            mColorState.mUseColor = useColor;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { mPaint.setColorFilter(colorFilter); }
    @Override
    public ColorFilter getColorFilter() { return mPaint.getColorFilter(); }

    @Override
    public void setTintList(ColorStateList tint) {
        mColorState.mTint = tint;
        mTintFilter = updateTintFilter(mTintFilter, tint, mColorState.mTintMode);
        invalidateSelf();
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mColorState.mTintMode = tintMode;
        mTintFilter = updateTintFilter(mTintFilter, mColorState.mTint, tintMode);
        invalidateSelf();
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final ColorState state = mColorState;
        if (state.mTint != null && state.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
            return true;
        }
        return false;
    }

    @Override
    public boolean isStateful() { return mColorState.mTint != null && mColorState.mTint.isStateful(); }
    @Override
    public boolean hasFocusStateSpecified() { return mColorState.mTint != null && mColorState.mTint.hasFocusStateSpecified(); }

    @Override
    public int getOpacity() {
        if (mTintFilter != null || mPaint.getColorFilter() != null) return PixelFormat.TRANSLUCENT;
        switch (mColorState.mUseColor >>> 24) {
            case 255: return PixelFormat.OPAQUE;
            case 0: return PixelFormat.TRANSPARENT;
        }
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setRect(getBounds());
        outline.setAlpha(getAlpha() / 255.0f);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.color});
        mColorState.mBaseColor = a.getColor(0, mColorState.mBaseColor);
        mColorState.mUseColor = mColorState.mBaseColor;
        a.recycle();
    }

    @Override
    public ConstantState getConstantState() { return mColorState; }

    static final class ColorState extends ConstantState {
        int mBaseColor;
        int mUseColor;
        int mChangingConfigurations;
        ColorStateList mTint = null;
        PorterDuff.Mode mTintMode = DEFAULT_TINT_MODE;

        ColorState() {}

        ColorState(ColorState state) {
            mBaseColor = state.mBaseColor;
            mUseColor = state.mUseColor;
            mChangingConfigurations = state.mChangingConfigurations;
            mTint = state.mTint;
            mTintMode = state.mTintMode;
        }

        @Override
        public Drawable newDrawable() { return new ColorDrawable(this); }
        @Override
        public Drawable newDrawable(Resources res) { return new ColorDrawable(this); }
        @Override
        public int getChangingConfigurations() { return mChangingConfigurations; }
    }
}
