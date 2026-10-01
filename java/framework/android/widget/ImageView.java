package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.io.InputStream;

/**
 * Shows a drawable scaled into the view (AOSP ImageView). Scale type ordinals match
 * the framework enum so apps can persist them. The two-argument constructor uses
 * defStyleAttr 0, as AOSP does (there is no {@code imageViewStyle} attr).
 */
public class ImageView extends View {
    private static final String LOG_TAG = "ImageView";
    private static final ScaleType[] SCALE_TYPES = {
        ScaleType.MATRIX, ScaleType.FIT_XY, ScaleType.FIT_START, ScaleType.FIT_CENTER, ScaleType.FIT_END,
        ScaleType.CENTER, ScaleType.CENTER_CROP, ScaleType.CENTER_INSIDE,
    };
    private static final int[] ATTRS = {
        android.R.attr.src, android.R.attr.scaleType, android.R.attr.adjustViewBounds, android.R.attr.maxWidth,
        android.R.attr.maxHeight, android.R.attr.tint, android.R.attr.baselineAlignBottom,
        android.R.attr.cropToPadding, android.R.attr.tintMode,
    };

    private Uri mUri;
    private int mResource;
    private Matrix mMatrix;
    private ScaleType mScaleType;
    private boolean mHaveFrame;
    private boolean mAdjustViewBounds;
    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxHeight = Integer.MAX_VALUE;
    private ColorFilter mColorFilter;
    private boolean mHasColorFilter;
    private int mAlpha = 255;
    private Drawable mDrawable;
    private int[] mState;
    private boolean mMergeState;
    private int mLevel;
    private int mDrawableWidth;
    private int mDrawableHeight;
    private Matrix mDrawMatrix;
    private Matrix mAnimateMatrix;
    private final RectF mTempSrc = new RectF();
    private final RectF mTempDst = new RectF();
    private boolean mCropToPadding;
    private int mBaseline = -1;
    private boolean mBaselineAlignBottom;
    private ColorStateList mDrawableTintList;
    private PorterDuff.Mode mDrawableTintMode;
    private BlendMode mDrawableBlendMode;
    private boolean mHasDrawableTint;
    private boolean mHasDrawableTintMode;
    private boolean mLoggedUri;

    public ImageView(Context context) {
        super(context);
        initImageView();
    }

    public ImageView(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }

    public ImageView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initImageView();
        if (attrs == null && defStyleAttr == 0 && defStyleRes == 0) return;
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        int scale = a.getInt(1, -1);
        if (scale >= 0 && scale < SCALE_TYPES.length) mScaleType = SCALE_TYPES[scale];
        mAdjustViewBounds = a.getBoolean(2, false);
        mMaxWidth = a.getDimensionPixelSize(3, Integer.MAX_VALUE);
        mMaxHeight = a.getDimensionPixelSize(4, Integer.MAX_VALUE);
        mBaselineAlignBottom = a.getBoolean(6, false);
        mCropToPadding = a.getBoolean(7, false);
        int tintMode = a.getInt(8, -1);
        if (tintMode != -1) setImageTintMode(Drawable.parseTintMode(tintMode, mDrawableTintMode));
        ColorStateList tint = a.getColorStateList(5);
        if (tint != null) setImageTintList(tint);
        Drawable src = a.getDrawable(0);
        if (src != null) setImageDrawable(src);
        a.recycle();
    }

    private void initImageView() {
        mMatrix = new Matrix();
        mScaleType = ScaleType.FIT_CENTER;
    }

    @Override
    protected boolean verifyDrawable(Drawable dr) { return mDrawable == dr || super.verifyDrawable(dr); }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mDrawable != null) mDrawable.jumpToCurrentState();
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        if (dr == mDrawable) {
            if (dr != null) {
                int w = dr.getIntrinsicWidth();
                int h = dr.getIntrinsicHeight();
                if (w != mDrawableWidth || h != mDrawableHeight) {
                    mDrawableWidth = w;
                    mDrawableHeight = h;
                    requestLayout();
                }
            }
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }

    @Override
    public boolean hasOverlappingRendering() { return getBackground() != null && getBackground().getCurrent() != null; }

    public boolean getAdjustViewBounds() { return mAdjustViewBounds; }

    public void setAdjustViewBounds(boolean adjustViewBounds) {
        if (mAdjustViewBounds == adjustViewBounds) return;
        mAdjustViewBounds = adjustViewBounds;
        if (adjustViewBounds) setScaleType(ScaleType.FIT_CENTER);
        requestLayout();
    }

    public int getMaxWidth() { return mMaxWidth; }

    public void setMaxWidth(int maxWidth) {
        mMaxWidth = maxWidth;
        requestLayout();
    }

    public int getMaxHeight() { return mMaxHeight; }

    public void setMaxHeight(int maxHeight) {
        mMaxHeight = maxHeight;
        requestLayout();
    }

    public Drawable getDrawable() { return mDrawable; }

    public void setImageResource(int resId) {
        if (mUri != null || mResource != resId) {
            updateDrawable(null);
            mResource = resId;
            mUri = null;
            resolveUri();
            requestLayout();
            invalidate();
        }
    }

    public void setImageURI(Uri uri) {
        if (mResource != 0 || (mUri != uri && (uri == null || mUri == null || !uri.equals(mUri)))) {
            updateDrawable(null);
            mResource = 0;
            mUri = uri;
            resolveUri();
            requestLayout();
            invalidate();
        }
    }

    public void setImageDrawable(Drawable drawable) {
        if (mDrawable != drawable) {
            mResource = 0;
            mUri = null;
            updateDrawable(drawable);
            requestLayout();
            invalidate();
        }
    }

    public void setImageIcon(Icon icon) { setImageDrawable(icon == null ? null : icon.loadDrawable(getContext())); }

    public void setImageTintList(ColorStateList tint) {
        mDrawableTintList = tint;
        mHasDrawableTint = true;
        applyImageTint();
    }

    public ColorStateList getImageTintList() { return mDrawableTintList; }

    public void setImageTintMode(PorterDuff.Mode tintMode) {
        mDrawableTintMode = tintMode;
        mDrawableBlendMode = null;
        mHasDrawableTintMode = true;
        applyImageTint();
    }

    public void setImageTintBlendMode(BlendMode blendMode) {
        mDrawableBlendMode = blendMode;
        mDrawableTintMode = modeOf(blendMode);
        mHasDrawableTintMode = true;
        applyImageTint();
    }

    public PorterDuff.Mode getImageTintMode() { return mDrawableTintMode; }

    public BlendMode getImageTintBlendMode() { return mDrawableBlendMode; }

    public void setImageBitmap(Bitmap bm) {
        setImageDrawable(bm == null ? null : new BitmapDrawable(getResources(), bm));
    }

    public void setImageState(int[] state, boolean merge) {
        mState = state;
        mMergeState = merge;
        if (mDrawable != null) {
            refreshDrawableState();
            resizeFromDrawable();
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        resizeFromDrawable();
    }

    public void setImageLevel(int level) {
        mLevel = level;
        if (mDrawable != null) {
            mDrawable.setLevel(level);
            resizeFromDrawable();
        }
    }

    public void setScaleType(ScaleType scaleType) {
        if (scaleType == null) throw new NullPointerException();
        if (mScaleType != scaleType) {
            mScaleType = scaleType;
            requestLayout();
            invalidate();
        }
    }

    public ScaleType getScaleType() { return mScaleType; }

    public Matrix getImageMatrix() { return mMatrix; }

    public void setImageMatrix(Matrix matrix) {
        if (matrix != null && matrix.isIdentity()) matrix = null;
        if ((matrix == null && !mMatrix.isIdentity()) || (matrix != null && !mMatrix.equals(matrix))) {
            mMatrix.set(matrix);
            configureBounds();
            invalidate();
        }
    }

    public boolean getCropToPadding() { return mCropToPadding; }

    public void setCropToPadding(boolean cropToPadding) {
        if (mCropToPadding == cropToPadding) return;
        mCropToPadding = cropToPadding;
        requestLayout();
        invalidate();
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        if (mState == null) return super.onCreateDrawableState(extraSpace);
        if (!mMergeState) return mState;
        return mergeDrawableStates(super.onCreateDrawableState(extraSpace + mState.length), mState);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        if (mDrawable != null) mDrawable.setLayoutDirection(layoutDirection);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        resolveUri();
        int w;
        int h;
        float desiredAspect = 0;
        boolean resizeWidth = false;
        boolean resizeHeight = false;
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        if (mDrawable == null) {
            mDrawableWidth = -1;
            mDrawableHeight = -1;
            w = 0;
            h = 0;
        } else {
            w = mDrawableWidth;
            h = mDrawableHeight;
            if (w <= 0) w = 1;
            if (h <= 0) h = 1;
            if (mAdjustViewBounds) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
                desiredAspect = (float) w / (float) h;
            }
        }
        int pleft = mPaddingLeft;
        int pright = mPaddingRight;
        int ptop = mPaddingTop;
        int pbottom = mPaddingBottom;
        int widthSize;
        int heightSize;
        if (resizeWidth || resizeHeight) {
            widthSize = resolveAdjustedSize(w + pleft + pright, mMaxWidth, widthMeasureSpec);
            heightSize = resolveAdjustedSize(h + ptop + pbottom, mMaxHeight, heightMeasureSpec);
            if (desiredAspect != 0) {
                float actualAspect = (float) (widthSize - pleft - pright) / (heightSize - ptop - pbottom);
                if (Math.abs(actualAspect - desiredAspect) > 0.0000001f) {
                    boolean done = false;
                    if (resizeWidth) {
                        int newWidth = (int) (desiredAspect * (heightSize - ptop - pbottom)) + pleft + pright;
                        if (!resizeHeight) widthSize = resolveAdjustedSize(newWidth, mMaxWidth, widthMeasureSpec);
                        if (newWidth <= widthSize) {
                            widthSize = newWidth;
                            done = true;
                        }
                    }
                    if (!done && resizeHeight) {
                        int newHeight = (int) ((widthSize - pleft - pright) / desiredAspect) + ptop + pbottom;
                        if (!resizeWidth) heightSize = resolveAdjustedSize(newHeight, mMaxHeight, heightMeasureSpec);
                        if (newHeight <= heightSize) heightSize = newHeight;
                    }
                }
            }
        } else {
            w += pleft + pright;
            h += ptop + pbottom;
            w = Math.max(w, getSuggestedMinimumWidth());
            h = Math.max(h, getSuggestedMinimumHeight());
            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);
        }
        setMeasuredDimension(widthSize, heightSize);
    }

    private int resolveAdjustedSize(int desiredSize, int maxSize, int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                return Math.min(desiredSize, maxSize);
            case MeasureSpec.AT_MOST:
                return Math.min(Math.min(desiredSize, specSize), maxSize);
            case MeasureSpec.EXACTLY:
                return specSize;
            default:
                return desiredSize;
        }
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        mHaveFrame = true;
        configureBounds();
        return changed;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mDrawable != null && mDrawable.isStateful() && mDrawable.setState(getDrawableState())) {
            invalidateDrawable(mDrawable);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mDrawable != null) mDrawable.setHotspot(x, y);
    }

    /** Applies a temporary matrix, used by image animations. A null matrix clears it. */
    public void animateTransform(Matrix matrix) {
        mAnimateMatrix = matrix;
        if (mDrawable != null) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawable == null || mDrawableWidth == 0 || mDrawableHeight == 0) return;
        Matrix matrix = mAnimateMatrix != null ? mAnimateMatrix : mDrawMatrix;
        if (matrix == null && mPaddingTop == 0 && mPaddingLeft == 0) {
            mDrawable.draw(canvas);
        } else {
            int save = canvas.save();
            if (mCropToPadding) {
                int sx = getScrollX();
                int sy = getScrollY();
                canvas.clipRect(sx + mPaddingLeft, sy + mPaddingTop, sx + getWidth() - mPaddingRight,
                        sy + getHeight() - mPaddingBottom);
            }
            canvas.translate(mPaddingLeft, mPaddingTop);
            if (matrix != null) canvas.concat(matrix);
            mDrawable.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    @Override
    public int getBaseline() {
        if (mBaselineAlignBottom) return getMeasuredHeight();
        return mBaseline;
    }

    public void setBaseline(int baseline) {
        if (mBaseline == baseline) return;
        mBaseline = baseline;
        requestLayout();
    }

    public void setBaselineAlignBottom(boolean aligned) {
        if (mBaselineAlignBottom == aligned) return;
        mBaselineAlignBottom = aligned;
        requestLayout();
    }

    public boolean getBaselineAlignBottom() { return mBaselineAlignBottom; }

    public final void setColorFilter(int color, PorterDuff.Mode mode) {
        setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    public final void setColorFilter(int color) { setColorFilter(color, PorterDuff.Mode.SRC_ATOP); }

    public final void clearColorFilter() { setColorFilter((ColorFilter) null); }

    public ColorFilter getColorFilter() { return mColorFilter; }

    public void setColorFilter(ColorFilter cf) {
        if (mColorFilter == cf) return;
        mColorFilter = cf;
        mHasColorFilter = cf != null;
        applyColorMod();
        invalidate();
    }

    public int getImageAlpha() { return mAlpha; }

    public void setImageAlpha(int alpha) {
        alpha &= 0xFF;
        if (mAlpha == alpha) return;
        mAlpha = alpha;
        applyColorMod();
        invalidate();
    }

    /** @deprecated Use {@link #setImageAlpha(int)}. */
    @Deprecated
    public void setAlpha(int alpha) { setImageAlpha(alpha); }

    @Override
    public boolean isOpaque() {
        return super.isOpaque() || (mDrawable != null && mAlpha == 255 && mDrawable.getOpacity() == PixelFormat.OPAQUE);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mDrawable != null) mDrawable.setVisible(isVisible, false);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mDrawable != null) mDrawable.setVisible(visibility == VISIBLE, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDrawable != null) mDrawable.setVisible(getVisibility() == VISIBLE, false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDrawable != null) mDrawable.setVisible(false, false);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ImageView.class.getName(); }

    private void resolveUri() {
        if (mDrawable != null) return;
        if (mResource != 0) {
            try {
                updateDrawable(getContext().getDrawable(mResource));
            } catch (Exception e) {
                Log.w(LOG_TAG, "Unable to find resource: " + mResource);
                mResource = 0;
            }
            return;
        }
        if (mUri == null) return;
        InputStream stream = null;
        try {
            stream = getContext().getContentResolver().openInputStream(mUri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) updateDrawable(new BitmapDrawable(getResources(), bitmap));
        } catch (Exception e) {
            if (!mLoggedUri) {
                Log.w(LOG_TAG, "Unable to open content: " + mUri);
                mLoggedUri = true;
            }
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                    // The image is already decoded, or the open failed.
                }
            }
        }
    }

    private void updateDrawable(Drawable d) {
        if (mDrawable != null) {
            mDrawable.setCallback(null);
            unscheduleDrawable(mDrawable);
        }
        mDrawable = d;
        if (d != null) {
            d.setCallback(this);
            d.setLayoutDirection(getLayoutDirection());
            if (d.isStateful()) d.setState(getDrawableState());
            if (mLevel != 0) d.setLevel(mLevel);
            d.setVisible(getVisibility() == VISIBLE, false);
            mDrawableWidth = d.getIntrinsicWidth();
            mDrawableHeight = d.getIntrinsicHeight();
            applyImageTint();
            applyColorMod();
            configureBounds();
        } else {
            mDrawableWidth = mDrawableHeight = -1;
        }
    }

    private void resizeFromDrawable() {
        if (mDrawable == null) return;
        int w = mDrawable.getIntrinsicWidth();
        int h = mDrawable.getIntrinsicHeight();
        if (w != mDrawableWidth || h != mDrawableHeight) {
            mDrawableWidth = w;
            mDrawableHeight = h;
            requestLayout();
        }
    }

    private void applyImageTint() {
        if (mDrawable != null && (mHasDrawableTint || mHasDrawableTintMode)) {
            mDrawable = mDrawable.mutate();
            if (mHasDrawableTint) mDrawable.setTintList(mDrawableTintList);
            if (mHasDrawableTintMode) {
                mDrawable.setTintMode(mDrawableTintMode);
                if (mDrawableBlendMode != null) mDrawable.setTintBlendMode(mDrawableBlendMode);
            }
            if (mDrawable.isStateful()) mDrawable.setState(getDrawableState());
        }
    }

    private void applyColorMod() {
        if (mDrawable != null && (mHasColorFilter || mAlpha != 255)) {
            mDrawable = mDrawable.mutate();
            if (mHasColorFilter) mDrawable.setColorFilter(mColorFilter);
            mDrawable.setAlpha(mAlpha);
        }
    }

    private void configureBounds() {
        if (mDrawable == null || !mHaveFrame) return;
        int dwidth = mDrawableWidth;
        int dheight = mDrawableHeight;
        int vwidth = getWidth() - mPaddingLeft - mPaddingRight;
        int vheight = getHeight() - mPaddingTop - mPaddingBottom;
        boolean fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight);
        if (dwidth <= 0 || dheight <= 0 || mScaleType == ScaleType.FIT_XY) {
            mDrawable.setBounds(0, 0, vwidth, vheight);
            mDrawMatrix = null;
        } else {
            mDrawable.setBounds(0, 0, dwidth, dheight);
            if (mScaleType == ScaleType.MATRIX) {
                mDrawMatrix = mMatrix.isIdentity() ? null : mMatrix;
            } else if (fits) {
                mDrawMatrix = null;
            } else if (mScaleType == ScaleType.CENTER) {
                mDrawMatrix = mMatrix;
                mDrawMatrix.setTranslate(Math.round((vwidth - dwidth) * 0.5f), Math.round((vheight - dheight) * 0.5f));
            } else if (mScaleType == ScaleType.CENTER_CROP) {
                mDrawMatrix = mMatrix;
                float scale;
                float dx = 0;
                float dy = 0;
                if (dwidth * vheight > vwidth * dheight) {
                    scale = (float) vheight / (float) dheight;
                    dx = (vwidth - dwidth * scale) * 0.5f;
                } else {
                    scale = (float) vwidth / (float) dwidth;
                    dy = (vheight - dheight * scale) * 0.5f;
                }
                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(Math.round(dx), Math.round(dy));
            } else if (mScaleType == ScaleType.CENTER_INSIDE) {
                mDrawMatrix = mMatrix;
                float scale;
                if (dwidth <= vwidth && dheight <= vheight) scale = 1f;
                else scale = Math.min((float) vwidth / (float) dwidth, (float) vheight / (float) dheight);
                float dx = Math.round((vwidth - dwidth * scale) * 0.5f);
                float dy = Math.round((vheight - dheight * scale) * 0.5f);
                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
            } else {
                mTempSrc.set(0, 0, dwidth, dheight);
                mTempDst.set(0, 0, vwidth, vheight);
                mDrawMatrix = mMatrix;
                mDrawMatrix.setRectToRect(mTempSrc, mTempDst, scaleToFit(mScaleType));
            }
        }
    }

    private static Matrix.ScaleToFit scaleToFit(ScaleType type) {
        if (type == ScaleType.FIT_START) return Matrix.ScaleToFit.START;
        if (type == ScaleType.FIT_END) return Matrix.ScaleToFit.END;
        if (type == ScaleType.FIT_CENTER) return Matrix.ScaleToFit.CENTER;
        return Matrix.ScaleToFit.FILL;
    }

    static PorterDuff.Mode modeOf(BlendMode mode) {
        if (mode == null) return null;
        switch (mode) {
            case PLUS:
                return PorterDuff.Mode.ADD;
            case MODULATE:
            case MULTIPLY:
                return PorterDuff.Mode.MULTIPLY;
            case SCREEN:
                return PorterDuff.Mode.SCREEN;
            case OVERLAY:
                return PorterDuff.Mode.OVERLAY;
            case DARKEN:
                return PorterDuff.Mode.DARKEN;
            case LIGHTEN:
                return PorterDuff.Mode.LIGHTEN;
            default:
                PorterDuff.Mode[] modes = PorterDuff.Mode.values();
                int ord = mode.ordinal();
                return ord >= 0 && ord < modes.length ? modes[ord] : PorterDuff.Mode.SRC_IN;
        }
    }

    /** How the drawable is fitted into the view. Ordinals match the framework attribute. */
    public enum ScaleType {
        MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE
    }
}
