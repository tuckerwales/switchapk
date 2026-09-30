package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class BitmapDrawable extends Drawable {
    private static final int DEFAULT_PAINT_FLAGS = Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG;
    private final Rect mDstRect = new Rect();
    private BitmapState mBitmapState;
    private PorterDuffColorFilter mTintFilter;
    private int mTargetDensity = DisplayMetrics.DENSITY_DEFAULT;
    private boolean mDstRectAndInsetsDirty = true;
    private boolean mMutated;
    private int mBitmapWidth, mBitmapHeight;

    @Deprecated
    public BitmapDrawable() { init(new BitmapState((Bitmap) null), null); }

    @Deprecated
    public BitmapDrawable(Resources res) { init(new BitmapState((Bitmap) null), res); }

    @Deprecated
    public BitmapDrawable(Bitmap bitmap) { init(new BitmapState(bitmap), null); }

    public BitmapDrawable(Resources res, Bitmap bitmap) { init(new BitmapState(bitmap), res); }

    @Deprecated
    public BitmapDrawable(String filepath) { this(null, filepath); }

    public BitmapDrawable(Resources res, String filepath) {
        Bitmap bitmap = BitmapFactory.decodeFile(filepath);
        init(new BitmapState(bitmap), res);
    }

    @Deprecated
    public BitmapDrawable(InputStream is) { this(null, is); }

    public BitmapDrawable(Resources res, InputStream is) {
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        init(new BitmapState(bitmap), res);
    }

    private BitmapDrawable(BitmapState state, Resources res) { init(state, res); }

    private void init(BitmapState state, Resources res) {
        mBitmapState = state;
        updateLocalState(res);
    }

    private void updateLocalState(Resources res) {
        mTargetDensity = resolveDensity(res, mBitmapState.mTargetDensity);
        mTintFilter = updateTintFilter(mTintFilter, mBitmapState.mTint, mBitmapState.mTintMode);
        computeBitmapSize();
    }

    public final Paint getPaint() { return mBitmapState.mPaint; }
    public final Bitmap getBitmap() { return mBitmapState.mBitmap; }

    private void computeBitmapSize() {
        final Bitmap bitmap = mBitmapState.mBitmap;
        if (bitmap != null) {
            mBitmapWidth = bitmap.getScaledWidth(mTargetDensity);
            mBitmapHeight = bitmap.getScaledHeight(mTargetDensity);
        } else {
            mBitmapWidth = mBitmapHeight = -1;
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mBitmapState.mBitmap != bitmap) {
            mBitmapState.mBitmap = bitmap;
            computeBitmapSize();
            invalidateSelf();
        }
    }

    public void setTargetDensity(Canvas canvas) { setTargetDensity(canvas.getDensity()); }
    public void setTargetDensity(DisplayMetrics metrics) { setTargetDensity(metrics.densityDpi); }

    public void setTargetDensity(int density) {
        if (mTargetDensity != density) {
            mTargetDensity = density == 0 ? DisplayMetrics.DENSITY_DEFAULT : density;
            if (mBitmapState.mBitmap != null) computeBitmapSize();
            invalidateSelf();
        }
    }

    public int getGravity() { return mBitmapState.mGravity; }

    public void setGravity(int gravity) {
        if (mBitmapState.mGravity != gravity) {
            mBitmapState.mGravity = gravity;
            mDstRectAndInsetsDirty = true;
            invalidateSelf();
        }
    }

    public void setMipMap(boolean mipMap) {}
    public boolean hasMipMap() { return false; }
    public void setAntiAlias(boolean aa) { mBitmapState.mPaint.setAntiAlias(aa); invalidateSelf(); }
    public boolean hasAntiAlias() { return mBitmapState.mPaint.isAntiAlias(); }
    @Override
    public void setFilterBitmap(boolean filter) { mBitmapState.mPaint.setFilterBitmap(filter); invalidateSelf(); }
    @Override
    public boolean isFilterBitmap() { return mBitmapState.mPaint.isFilterBitmap(); }
    @Override
    public void setDither(boolean dither) {}
    public Shader.TileMode getTileModeX() { return mBitmapState.mTileModeX; }
    public Shader.TileMode getTileModeY() { return mBitmapState.mTileModeY; }
    public void setTileModeX(Shader.TileMode mode) { setTileModeXY(mode, mBitmapState.mTileModeY); }
    public final void setTileModeY(Shader.TileMode mode) { setTileModeXY(mBitmapState.mTileModeX, mode); }

    public void setTileModeXY(Shader.TileMode xmode, Shader.TileMode ymode) {
        final BitmapState state = mBitmapState;
        if (state.mTileModeX != xmode || state.mTileModeY != ymode) {
            state.mTileModeX = xmode;
            state.mTileModeY = ymode;
            state.mRebuildShader = true;
            mDstRectAndInsetsDirty = true;
            invalidateSelf();
        }
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        if (mBitmapState.mAutoMirrored != mirrored) {
            mBitmapState.mAutoMirrored = mirrored;
            invalidateSelf();
        }
    }

    @Override
    public final boolean isAutoMirrored() { return mBitmapState.mAutoMirrored; }

    @Override
    public int getChangingConfigurations() { return super.getChangingConfigurations() | mBitmapState.getChangingConfigurations(); }

    @Override
    protected void onBoundsChange(Rect bounds) { mDstRectAndInsetsDirty = true; }

    @Override
    public void draw(Canvas canvas) {
        final Bitmap bitmap = mBitmapState.mBitmap;
        if (bitmap == null) return;
        final BitmapState state = mBitmapState;
        final Paint paint = state.mPaint;
        if (state.mRebuildShader) {
            final Shader.TileMode tmx = state.mTileModeX;
            final Shader.TileMode tmy = state.mTileModeY;
            if (tmx == null && tmy == null) {
                paint.setShader(null);
            } else {
                paint.setShader(new BitmapShader(bitmap, tmx == null ? Shader.TileMode.CLAMP : tmx, tmy == null ? Shader.TileMode.CLAMP : tmy));
            }
            state.mRebuildShader = false;
        }
        updateDstRectAndInsetsIfDirty();
        final boolean clearColorFilter;
        if (mTintFilter != null && paint.getColorFilter() == null) {
            paint.setColorFilter(mTintFilter);
            clearColorFilter = true;
        } else {
            clearColorFilter = false;
        }
        final Shader shader = paint.getShader();
        if (shader == null) {
            canvas.drawBitmap(bitmap, null, mDstRect, paint);
        } else {
            canvas.drawRect(mDstRect, paint);
        }
        if (clearColorFilter) paint.setColorFilter(null);
    }

    private void updateDstRectAndInsetsIfDirty() {
        if (mDstRectAndInsetsDirty) {
            if (mBitmapState.mTileModeX == null && mBitmapState.mTileModeY == null) {
                final Rect bounds = getBounds();
                Gravity.apply(mBitmapState.mGravity, mBitmapWidth, mBitmapHeight, bounds, mDstRect, getLayoutDirection());
            } else {
                copyBounds(mDstRect);
            }
        }
        mDstRectAndInsetsDirty = false;
    }

    @Override
    public void getOutline(Outline outline) {
        updateDstRectAndInsetsIfDirty();
        outline.setRect(mDstRect);
        boolean opaqueOverShape = mBitmapState.mBitmap != null && !mBitmapState.mBitmap.hasAlpha();
        outline.setAlpha(opaqueOverShape ? getAlpha() / 255.0f : 0.0f);
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = mBitmapState.mPaint.getAlpha();
        if (alpha != oldAlpha) {
            mBitmapState.mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() { return mBitmapState.mPaint.getAlpha(); }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBitmapState.mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public ColorFilter getColorFilter() { return mBitmapState.mPaint.getColorFilter(); }

    @Override
    public void setTintList(ColorStateList tint) {
        final BitmapState state = mBitmapState;
        if (state.mTint != tint) {
            state.mTint = tint;
            mTintFilter = updateTintFilter(mTintFilter, tint, mBitmapState.mTintMode);
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        final BitmapState state = mBitmapState;
        if (state.mTintMode != tintMode) {
            state.mTintMode = tintMode;
            mTintFilter = updateTintFilter(mTintFilter, mBitmapState.mTint, tintMode);
            invalidateSelf();
        }
    }

    public ColorStateList getTint() { return mBitmapState.mTint; }
    public PorterDuff.Mode getTintMode() { return mBitmapState.mTintMode; }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mBitmapState = new BitmapState(mBitmapState);
            mMutated = true;
        }
        return this;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final BitmapState state = mBitmapState;
        if (state.mTint != null && state.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
            return true;
        }
        return false;
    }

    @Override
    public boolean isStateful() { return mBitmapState.mTint != null && mBitmapState.mTint.isStateful(); }

    private static final int[] ATTRS = {android.R.attr.src, android.R.attr.antialias, android.R.attr.filter, android.R.attr.dither,
            android.R.attr.gravity, android.R.attr.tileMode, android.R.attr.tint, android.R.attr.tintMode, android.R.attr.alpha,
            android.R.attr.autoMirrored, android.R.attr.tileModeX, android.R.attr.tileModeY};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        final BitmapState state = mBitmapState;
        final int srcResId = a.getResourceId(0, 0);
        if (srcResId != 0) {
            final TypedValue value = new TypedValue();
            r.getValueForDensity(srcResId, mSrcDensityOverride, value, true);
            Bitmap bitmap = null;
            try {
                InputStream is = r.openRawResource(srcResId, value);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                bitmap = BitmapFactory.decodeResourceStream(r, value, is, null, opts);
                is.close();
            } catch (Exception e) {
                // fall through
            }
            if (bitmap == null) throw new XmlPullParserException(parser.getPositionDescription() + ": <bitmap> requires a valid 'src' attribute");
            state.mBitmap = bitmap;
        }
        state.mTargetDensity = r.getDisplayMetrics().densityDpi;
        state.mPaint.setAntiAlias(a.getBoolean(1, state.mPaint.isAntiAlias()));
        state.mPaint.setFilterBitmap(a.getBoolean(2, state.mPaint.isFilterBitmap()));
        state.mGravity = a.getInt(4, state.mGravity);
        final int tileMode = a.getInt(5, -2);
        if (tileMode != -2) {
            final Shader.TileMode mode = parseTileMode(tileMode);
            setTileModeXY(mode, mode);
        }
        final int tileModeX = a.getInt(10, -2);
        if (tileModeX != -2) setTileModeX(parseTileMode(tileModeX));
        final int tileModeY = a.getInt(11, -2);
        if (tileModeY != -2) setTileModeY(parseTileMode(tileModeY));
        if (a.hasValue(6)) state.mTint = a.getColorStateList(6);
        final int tintMode = a.getInt(7, -1);
        if (tintMode != -1) state.mTintMode = Drawable.parseTintMode(tintMode, PorterDuff.Mode.SRC_IN);
        if (a.hasValue(8)) state.mPaint.setAlpha((int) (a.getFloat(8, 1f) * 255 + 0.5f));
        state.mAutoMirrored = a.getBoolean(9, state.mAutoMirrored);
        a.recycle();
        updateLocalState(r);
    }

    private static Shader.TileMode parseTileMode(int tileMode) {
        switch (tileMode) {
            case 0: return Shader.TileMode.CLAMP;
            case 1: return Shader.TileMode.REPEAT;
            case 2: return Shader.TileMode.MIRROR;
            default: return null;
        }
    }

    @Override
    public int getIntrinsicWidth() { return mBitmapWidth; }
    @Override
    public int getIntrinsicHeight() { return mBitmapHeight; }

    @Override
    public int getOpacity() {
        if (mBitmapState.mGravity != Gravity.FILL) return PixelFormat.TRANSLUCENT;
        final Bitmap bitmap = mBitmapState.mBitmap;
        return (bitmap == null || bitmap.hasAlpha() || mBitmapState.mPaint.getAlpha() < 255) ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    public final ConstantState getConstantState() { return mBitmapState; }

    static final class BitmapState extends ConstantState {
        final Paint mPaint;
        ColorStateList mTint = null;
        PorterDuff.Mode mTintMode = DEFAULT_TINT_MODE;
        Bitmap mBitmap = null;
        int mChangingConfigurations;
        int mGravity = Gravity.FILL;
        Shader.TileMode mTileModeX = null;
        Shader.TileMode mTileModeY = null;
        int mTargetDensity = DisplayMetrics.DENSITY_DEFAULT;
        boolean mAutoMirrored = false;
        boolean mRebuildShader;

        BitmapState(Bitmap bitmap) {
            mBitmap = bitmap;
            mPaint = new Paint(DEFAULT_PAINT_FLAGS);
        }

        BitmapState(BitmapState bitmapState) {
            mBitmap = bitmapState.mBitmap;
            mTint = bitmapState.mTint;
            mTintMode = bitmapState.mTintMode;
            mChangingConfigurations = bitmapState.mChangingConfigurations;
            mGravity = bitmapState.mGravity;
            mTileModeX = bitmapState.mTileModeX;
            mTileModeY = bitmapState.mTileModeY;
            mTargetDensity = bitmapState.mTargetDensity;
            mPaint = new Paint(bitmapState.mPaint);
            mRebuildShader = bitmapState.mRebuildShader;
            mAutoMirrored = bitmapState.mAutoMirrored;
        }

        @Override
        public Drawable newDrawable() { return new BitmapDrawable(this, null); }
        @Override
        public Drawable newDrawable(Resources res) { return new BitmapDrawable(this, res); }
        @Override
        public int getChangingConfigurations() { return mChangingConfigurations; }
    }
}
