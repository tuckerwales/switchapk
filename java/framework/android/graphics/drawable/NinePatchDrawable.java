package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.NinePatch;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class NinePatchDrawable extends Drawable {
    private NinePatchState mNinePatchState;
    private PorterDuffColorFilter mTintFilter;
    private Rect mPadding;
    private int mTargetDensity = DisplayMetrics.DENSITY_DEFAULT;
    private float mScale = 1f;
    private boolean mMutated;
    private Paint mPaint;
    private int mBitmapWidth = -1, mBitmapHeight = -1;

    NinePatchDrawable() { mNinePatchState = new NinePatchState(); }

    @Deprecated
    public NinePatchDrawable(Bitmap bitmap, byte[] chunk, Rect padding, String srcName) {
        this(new NinePatchState(new NinePatch(bitmap, chunk, srcName), padding), null);
    }

    public NinePatchDrawable(Resources res, Bitmap bitmap, byte[] chunk, Rect padding, String srcName) {
        this(new NinePatchState(new NinePatch(bitmap, chunk, srcName), padding), res);
    }

    @Deprecated
    public NinePatchDrawable(NinePatch patch) { this(new NinePatchState(patch, new Rect()), null); }

    public NinePatchDrawable(Resources res, NinePatch patch) { this(new NinePatchState(patch, new Rect()), res); }

    private NinePatchDrawable(NinePatchState state, Resources res) {
        mNinePatchState = state;
        updateLocalState(res);
    }

    private void updateLocalState(Resources res) {
        final NinePatchState state = mNinePatchState;
        mTargetDensity = resolveDensity(res, mTargetDensity);
        if (state.mNinePatch != null) {
            int src = state.mNinePatch.getDensity();
            mScale = (src == 0 || src == mTargetDensity) ? 1f : mTargetDensity / (float) src;
            mBitmapWidth = Math.round(state.mNinePatch.getWidth() * mScale);
            mBitmapHeight = Math.round(state.mNinePatch.getHeight() * mScale);
        }
        if (state.mPadding != null) {
            mPadding = new Rect(state.mPadding);
            if (mScale != 1f) mPadding.scale(mScale);
        }
        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }

    public void setTargetDensity(Canvas canvas) { setTargetDensity(canvas.getDensity()); }
    public void setTargetDensity(DisplayMetrics metrics) { setTargetDensity(metrics.densityDpi); }

    public void setTargetDensity(int density) {
        if (density == 0) density = DisplayMetrics.DENSITY_DEFAULT;
        if (mTargetDensity != density) {
            mTargetDensity = density;
            updateLocalState(null);
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        final NinePatchState state = mNinePatchState;
        if (state.mNinePatch == null) return;
        Paint p = mPaint;
        boolean restoreFilter = false;
        if (mTintFilter != null && (p == null || p.getColorFilter() == null)) {
            if (p == null) p = mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(mTintFilter);
            restoreFilter = true;
        }
        state.mNinePatch.drawScaled(canvas, new RectF(getBounds()), p, mScale);
        if (restoreFilter) p.setColorFilter(null);
    }

    @Override
    public boolean getPadding(Rect padding) {
        if (mPadding != null) {
            padding.set(mPadding);
            return (padding.left | padding.top | padding.right | padding.bottom) != 0;
        }
        return super.getPadding(padding);
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setRect(getBounds());
        outline.setAlpha(0);
    }

    @Override
    public Insets getOpticalInsets() { return Insets.NONE; }

    @Override
    public void setAlpha(int alpha) {
        if (mPaint == null && alpha == 0xFF) return;
        getPaint().setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public int getAlpha() { return mPaint == null ? 0xFF : getPaint().getAlpha(); }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (mPaint == null && colorFilter == null) return;
        getPaint().setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mNinePatchState.mTint = tint;
        mTintFilter = updateTintFilter(mTintFilter, tint, mNinePatchState.mTintMode);
        invalidateSelf();
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mNinePatchState.mTintMode = tintMode;
        mTintFilter = updateTintFilter(mTintFilter, mNinePatchState.mTint, tintMode);
        invalidateSelf();
    }

    @Override
    public void setAutoMirrored(boolean mirrored) { mNinePatchState.mAutoMirrored = mirrored; }
    @Override
    public boolean isAutoMirrored() { return mNinePatchState.mAutoMirrored; }
    @Override
    public void setFilterBitmap(boolean filter) { getPaint().setFilterBitmap(filter); invalidateSelf(); }
    @Override
    public boolean isFilterBitmap() { return mPaint != null && getPaint().isFilterBitmap(); }

    private static final int[] ATTRS = {android.R.attr.src, android.R.attr.dither, android.R.attr.tint, android.R.attr.tintMode,
            android.R.attr.alpha, android.R.attr.autoMirrored};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        final NinePatchState state = mNinePatchState;
        final int srcResId = a.getResourceId(0, 0);
        if (srcResId != 0) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inDither = !state.mDither;
            final Rect padding = new Rect();
            Bitmap bitmap = null;
            try {
                final TypedValue value = new TypedValue();
                final InputStream is = r.openRawResource(srcResId, value);
                bitmap = BitmapFactory.decodeResourceStream(r, value, is, padding, options);
                is.close();
            } catch (IOException e) {
                // ignore
            }
            if (bitmap == null) throw new XmlPullParserException(parser.getPositionDescription() + ": <nine-patch> requires a valid src attribute");
            if (bitmap.getNinePatchChunk() == null) throw new XmlPullParserException(parser.getPositionDescription() + ": <nine-patch> requires a valid 9-patch source image");
            state.mNinePatch = new NinePatch(bitmap, bitmap.getNinePatchChunk());
            state.mPadding = padding;
        }
        if (a.hasValue(2)) state.mTint = a.getColorStateList(2);
        final int tintMode = a.getInt(3, -1);
        if (tintMode != -1) state.mTintMode = Drawable.parseTintMode(tintMode, PorterDuff.Mode.SRC_IN);
        if (a.hasValue(4)) getPaint().setAlpha((int) (a.getFloat(4, 1f) * 255));
        state.mAutoMirrored = a.getBoolean(5, state.mAutoMirrored);
        a.recycle();
        updateLocalState(r);
    }

    public Paint getPaint() {
        if (mPaint == null) {
            mPaint = new Paint();
            mPaint.setFilterBitmap(true);
        }
        return mPaint;
    }

    @Override
    public int getIntrinsicWidth() { return mBitmapWidth; }
    @Override
    public int getIntrinsicHeight() { return mBitmapHeight; }

    @Override
    public int getOpacity() {
        return mNinePatchState.mNinePatch == null || mNinePatchState.mNinePatch.hasAlpha() || (mPaint != null && mPaint.getAlpha() < 255)
                ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    public ConstantState getConstantState() { return mNinePatchState; }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mNinePatchState = new NinePatchState(mNinePatchState);
            mMutated = true;
        }
        return this;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final NinePatchState state = mNinePatchState;
        if (state.mTint != null && state.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
            return true;
        }
        return false;
    }

    @Override
    public boolean isStateful() { return mNinePatchState.mTint != null && mNinePatchState.mTint.isStateful(); }

    static final class NinePatchState extends ConstantState {
        int mChangingConfigurations;
        NinePatch mNinePatch = null;
        ColorStateList mTint = null;
        PorterDuff.Mode mTintMode = DEFAULT_TINT_MODE;
        Rect mPadding = null;
        boolean mDither = false;
        boolean mAutoMirrored = false;

        NinePatchState() {}

        NinePatchState(NinePatch ninePatch, Rect padding) {
            mNinePatch = ninePatch;
            mPadding = padding;
        }

        NinePatchState(NinePatchState orig) {
            mChangingConfigurations = orig.mChangingConfigurations;
            mNinePatch = orig.mNinePatch;
            mTint = orig.mTint;
            mTintMode = orig.mTintMode;
            mPadding = orig.mPadding;
            mDither = orig.mDither;
            mAutoMirrored = orig.mAutoMirrored;
        }

        @Override
        public Drawable newDrawable() { return new NinePatchDrawable(this, null); }
        @Override
        public Drawable newDrawable(Resources res) { return new NinePatchDrawable(this, res); }
        @Override
        public int getChangingConfigurations() { return mChangingConfigurations; }
    }
}
