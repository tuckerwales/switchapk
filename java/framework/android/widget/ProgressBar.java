package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.drawable.shapes.Shape;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import java.util.ArrayList;

/**
 * Port of AOSP ProgressBar: determinate (drawable levels per layer id) and
 * indeterminate (an Animatable drawable is started; anything else is cycled
 * through levels by an AlphaAnimation, as on Android). The animated progress
 * change of setProgress(int, true) jumps. The animated form is not wired.
 */
public class ProgressBar extends View {
    private static final int MAX_LEVEL = 10000;

    private static final int[] ATTRS = {
        android.R.attr.max, android.R.attr.progress, android.R.attr.secondaryProgress,
        android.R.attr.indeterminate, android.R.attr.indeterminateOnly, android.R.attr.indeterminateDrawable,
        android.R.attr.progressDrawable, android.R.attr.indeterminateDuration, android.R.attr.indeterminateBehavior,
        android.R.attr.minWidth, android.R.attr.maxWidth, android.R.attr.minHeight, android.R.attr.maxHeight,
        android.R.attr.interpolator, android.R.attr.mirrorForRtl, android.R.attr.progressTint,
        android.R.attr.progressTintMode, android.R.attr.progressBackgroundTint,
        android.R.attr.progressBackgroundTintMode, android.R.attr.secondaryProgressTint,
        android.R.attr.secondaryProgressTintMode, android.R.attr.indeterminateTint,
        android.R.attr.indeterminateTintMode, android.R.attr.min,
    };
    private static final int A_MAX = 0, A_PROGRESS = 1, A_SECONDARY = 2, A_INDETERMINATE = 3,
            A_INDETERMINATE_ONLY = 4, A_INDETERMINATE_DRAWABLE = 5, A_PROGRESS_DRAWABLE = 6, A_DURATION = 7,
            A_BEHAVIOR = 8, A_MIN_WIDTH = 9, A_MAX_WIDTH = 10, A_MIN_HEIGHT = 11, A_MAX_HEIGHT = 12,
            A_INTERPOLATOR = 13, A_MIRROR = 14, A_PROGRESS_TINT = 15, A_PROGRESS_TINT_MODE = 16,
            A_BG_TINT = 17, A_BG_TINT_MODE = 18, A_SECONDARY_TINT = 19, A_SECONDARY_TINT_MODE = 20,
            A_INDETERMINATE_TINT = 21, A_INDETERMINATE_TINT_MODE = 22, A_MIN = 23;

    int mMinWidth;
    int mMaxWidth;
    int mMinHeight;
    int mMaxHeight;

    private int mProgress;
    private int mSecondaryProgress;
    private int mMin;
    private boolean mMinInitialized;
    private int mMax;
    private boolean mMaxInitialized;

    private int mBehavior;
    private int mDuration;
    private boolean mIndeterminate;
    private boolean mOnlyIndeterminate;
    private Transformation mTransformation;
    private AlphaAnimation mAnimation;
    private boolean mHasAnimation;

    private Drawable mIndeterminateDrawable;
    private Drawable mProgressDrawable;
    private Drawable mCurrentDrawable;
    private ProgressTintInfo mProgressTintInfo;

    int mSampleWidth = 0;
    private boolean mNoInvalidate;
    private Interpolator mInterpolator;
    private RefreshProgressRunnable mRefreshProgressRunnable;
    private final long mUiThreadId;
    private boolean mShouldStartAnimationDrawable;

    private boolean mInDrawing;
    private boolean mAttached;
    private boolean mRefreshIsPosted;
    boolean mMirrorForRtl = false;
    private boolean mAggregatedIsVisible;
    private float mVisualProgress;
    private CharSequence mCustomStateDescription;

    private final ArrayList<RefreshData> mRefreshData = new ArrayList<RefreshData>();

    public ProgressBar(Context context) { this(context, null); }

    public ProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.progressBarStyle);
    }

    public ProgressBar(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mUiThreadId = Thread.currentThread().getId();
        initProgressBar();

        final TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        mNoInvalidate = true;

        final Drawable progressDrawable = a.getDrawable(A_PROGRESS_DRAWABLE);
        if (progressDrawable != null) {
            if (needsTileify(progressDrawable)) setProgressDrawableTiled(progressDrawable);
            else setProgressDrawable(progressDrawable);
        }

        mDuration = a.getInt(A_DURATION, mDuration);
        mMinWidth = a.getDimensionPixelSize(A_MIN_WIDTH, mMinWidth);
        mMaxWidth = a.getDimensionPixelSize(A_MAX_WIDTH, mMaxWidth);
        mMinHeight = a.getDimensionPixelSize(A_MIN_HEIGHT, mMinHeight);
        mMaxHeight = a.getDimensionPixelSize(A_MAX_HEIGHT, mMaxHeight);
        mBehavior = a.getInt(A_BEHAVIOR, mBehavior);

        final int resID = a.getResourceId(A_INTERPOLATOR, android.R.anim.linear_interpolator);
        if (resID > 0) {
            try {
                setInterpolator(context, resID);
            } catch (RuntimeException e) {
                mInterpolator = new LinearInterpolator();
            }
        }

        setMin(a.getInt(A_MIN, mMin));
        setMax(a.getInt(A_MAX, mMax));
        setProgress(a.getInt(A_PROGRESS, mProgress));
        setSecondaryProgress(a.getInt(A_SECONDARY, mSecondaryProgress));

        final Drawable indeterminateDrawable = a.getDrawable(A_INDETERMINATE_DRAWABLE);
        if (indeterminateDrawable != null) {
            if (needsTileify(indeterminateDrawable)) setIndeterminateDrawableTiled(indeterminateDrawable);
            else setIndeterminateDrawable(indeterminateDrawable);
        }

        mOnlyIndeterminate = a.getBoolean(A_INDETERMINATE_ONLY, mOnlyIndeterminate);
        mNoInvalidate = false;
        setIndeterminate(mOnlyIndeterminate || a.getBoolean(A_INDETERMINATE, mIndeterminate));
        mMirrorForRtl = a.getBoolean(A_MIRROR, mMirrorForRtl);

        if (a.hasValue(A_PROGRESS_TINT_MODE)) {
            tintInfo().mProgressTintMode = parseTintMode(a.getInt(A_PROGRESS_TINT_MODE, -1));
            tintInfo().mHasProgressTintMode = true;
        }
        if (a.hasValue(A_PROGRESS_TINT)) {
            tintInfo().mProgressTintList = a.getColorStateList(A_PROGRESS_TINT);
            tintInfo().mHasProgressTint = true;
        }
        if (a.hasValue(A_BG_TINT_MODE)) {
            tintInfo().mProgressBackgroundTintMode = parseTintMode(a.getInt(A_BG_TINT_MODE, -1));
            tintInfo().mHasProgressBackgroundTintMode = true;
        }
        if (a.hasValue(A_BG_TINT)) {
            tintInfo().mProgressBackgroundTintList = a.getColorStateList(A_BG_TINT);
            tintInfo().mHasProgressBackgroundTint = true;
        }
        if (a.hasValue(A_SECONDARY_TINT_MODE)) {
            tintInfo().mSecondaryProgressTintMode = parseTintMode(a.getInt(A_SECONDARY_TINT_MODE, -1));
            tintInfo().mHasSecondaryProgressTintMode = true;
        }
        if (a.hasValue(A_SECONDARY_TINT)) {
            tintInfo().mSecondaryProgressTintList = a.getColorStateList(A_SECONDARY_TINT);
            tintInfo().mHasSecondaryProgressTint = true;
        }
        if (a.hasValue(A_INDETERMINATE_TINT_MODE)) {
            tintInfo().mIndeterminateTintMode = parseTintMode(a.getInt(A_INDETERMINATE_TINT_MODE, -1));
            tintInfo().mHasIndeterminateTintMode = true;
        }
        if (a.hasValue(A_INDETERMINATE_TINT)) {
            tintInfo().mIndeterminateTintList = a.getColorStateList(A_INDETERMINATE_TINT);
            tintInfo().mHasIndeterminateTint = true;
        }
        a.recycle();

        applyProgressTints();
        applyIndeterminateTint();

        if (getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }

    private ProgressTintInfo tintInfo() {
        if (mProgressTintInfo == null) mProgressTintInfo = new ProgressTintInfo();
        return mProgressTintInfo;
    }

    /** Drawable.parseTintMode (hidden): the attr enum values of tintMode. */
    static PorterDuff.Mode parseTintMode(int value) {
        switch (value) {
            case 3: return PorterDuff.Mode.SRC_OVER;
            case 5: return PorterDuff.Mode.SRC_IN;
            case 9: return PorterDuff.Mode.SRC_ATOP;
            case 14: return PorterDuff.Mode.MULTIPLY;
            case 15: return PorterDuff.Mode.SCREEN;
            case 16: return PorterDuff.Mode.ADD;
            default: return PorterDuff.Mode.SRC_IN;
        }
    }

    private static boolean needsTileify(Drawable dr) {
        if (dr instanceof LayerDrawable) {
            final LayerDrawable orig = (LayerDrawable) dr;
            for (int i = 0; i < orig.getNumberOfLayers(); i++) {
                if (needsTileify(orig.getDrawable(i))) return true;
            }
            return false;
        }
        if (dr instanceof StateListDrawable) {
            final StateListDrawable in = (StateListDrawable) dr;
            for (int i = 0; i < in.getStateCount(); i++) {
                if (needsTileify(in.getStateDrawable(i))) return true;
            }
            return false;
        }
        return dr instanceof BitmapDrawable;
    }

    /** Tiles bitmap layers (RatingBar stars) with a repeating shader; progress layers are clipped by level. */
    private Drawable tileify(Drawable drawable, boolean clip) {
        if (drawable instanceof LayerDrawable) {
            final LayerDrawable orig = (LayerDrawable) drawable;
            final int n = orig.getNumberOfLayers();
            final Drawable[] outDrawables = new Drawable[n];
            for (int i = 0; i < n; i++) {
                final int id = orig.getId(i);
                outDrawables[i] = tileify(orig.getDrawable(i),
                        (id == android.R.id.progress || id == android.R.id.secondaryProgress));
            }
            final LayerDrawable clone = new LayerDrawable(outDrawables);
            for (int i = 0; i < n; i++) {
                clone.setId(i, orig.getId(i));
                clone.setLayerGravity(i, orig.getLayerGravity(i));
                clone.setLayerWidth(i, orig.getLayerWidth(i));
                clone.setLayerHeight(i, orig.getLayerHeight(i));
                clone.setLayerInsetLeft(i, orig.getLayerInsetLeft(i));
                clone.setLayerInsetRight(i, orig.getLayerInsetRight(i));
                clone.setLayerInsetTop(i, orig.getLayerInsetTop(i));
                clone.setLayerInsetBottom(i, orig.getLayerInsetBottom(i));
            }
            return clone;
        }
        if (drawable instanceof BitmapDrawable) {
            // As in AOSP N+: a repeating clone keeps the bitmap's tint (RatingBar stars).
            final Drawable.ConstantState cs = drawable.getConstantState();
            final BitmapDrawable clone = (BitmapDrawable) (cs != null ? cs.newDrawable(getResources()) : drawable);
            clone.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            if (mSampleWidth <= 0) mSampleWidth = clone.getIntrinsicWidth();
            return clip ? new ClipDrawable(clone, Gravity.LEFT, ClipDrawable.HORIZONTAL) : clone;
        }
        return drawable;
    }

    Shape getDrawableShape() {
        final float[] roundedCorners = new float[] { 5, 5, 5, 5, 5, 5, 5, 5 };
        return new RoundRectShape(roundedCorners, null, null);
    }

    private Drawable tileifyIndeterminate(Drawable drawable) {
        if (drawable instanceof AnimationDrawable) {
            AnimationDrawable background = (AnimationDrawable) drawable;
            final int n = background.getNumberOfFrames();
            AnimationDrawable newBg = new AnimationDrawable();
            newBg.setOneShot(background.isOneShot());
            for (int i = 0; i < n; i++) {
                Drawable frame = tileify(background.getFrame(i), true);
                frame.setLevel(MAX_LEVEL);
                newBg.addFrame(frame, background.getDuration(i));
            }
            newBg.setLevel(MAX_LEVEL);
            drawable = newBg;
        }
        return drawable;
    }

    private void initProgressBar() {
        mMin = 0;
        mMax = 100;
        mProgress = 0;
        mSecondaryProgress = 0;
        mIndeterminate = false;
        mOnlyIndeterminate = false;
        mDuration = 4000;
        mBehavior = AlphaAnimation.RESTART;
        mMinWidth = 24;
        mMaxWidth = 48;
        mMinHeight = 24;
        mMaxHeight = 48;
    }

    public void setMinWidth(int minWidth) {
        mMinWidth = minWidth;
        requestLayout();
    }

    public int getMinWidth() { return mMinWidth; }

    public void setMaxWidth(int maxWidth) {
        mMaxWidth = maxWidth;
        requestLayout();
    }

    public int getMaxWidth() { return mMaxWidth; }

    public void setMinHeight(int minHeight) {
        mMinHeight = minHeight;
        requestLayout();
    }

    public int getMinHeight() { return mMinHeight; }

    public void setMaxHeight(int maxHeight) {
        mMaxHeight = maxHeight;
        requestLayout();
    }

    public int getMaxHeight() { return mMaxHeight; }

    public synchronized boolean isIndeterminate() { return mIndeterminate; }

    public synchronized void setIndeterminate(boolean indeterminate) {
        if ((!mOnlyIndeterminate || !mIndeterminate) && indeterminate != mIndeterminate) {
            mIndeterminate = indeterminate;
            if (indeterminate) {
                swapCurrentDrawable(mIndeterminateDrawable);
                startAnimation();
            } else {
                swapCurrentDrawable(mProgressDrawable);
                stopAnimation();
            }
        }
    }

    private void swapCurrentDrawable(Drawable newDrawable) {
        final Drawable oldDrawable = mCurrentDrawable;
        mCurrentDrawable = newDrawable;
        if (oldDrawable != mCurrentDrawable) {
            if (oldDrawable != null) oldDrawable.setVisible(false, false);
            if (mCurrentDrawable != null) {
                mCurrentDrawable.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
            }
        }
    }

    public Drawable getIndeterminateDrawable() { return mIndeterminateDrawable; }

    public void setIndeterminateDrawable(Drawable d) {
        if (mIndeterminateDrawable != d) {
            if (mIndeterminateDrawable != null) {
                mIndeterminateDrawable.setCallback(null);
                unscheduleDrawable(mIndeterminateDrawable);
            }
            mIndeterminateDrawable = d;
            if (d != null) {
                d.setCallback(this);
                d.setLayoutDirection(getLayoutDirection());
                if (d.isStateful()) d.setState(getDrawableState());
                applyIndeterminateTint();
            }
            if (mIndeterminate) {
                swapCurrentDrawable(d);
                postInvalidate();
            }
        }
    }

    public void setIndeterminateTintList(ColorStateList tint) {
        tintInfo().mIndeterminateTintList = tint;
        mProgressTintInfo.mHasIndeterminateTint = true;
        applyIndeterminateTint();
    }

    public ColorStateList getIndeterminateTintList() {
        return mProgressTintInfo != null ? mProgressTintInfo.mIndeterminateTintList : null;
    }

    public void setIndeterminateTintMode(PorterDuff.Mode tintMode) {
        tintInfo().mIndeterminateTintMode = tintMode;
        mProgressTintInfo.mHasIndeterminateTintMode = true;
        applyIndeterminateTint();
    }

    public void setIndeterminateTintBlendMode(BlendMode blendMode) {
        tintInfo().mIndeterminateBlendMode = blendMode;
        mProgressTintInfo.mHasIndeterminateTintMode = true;
        applyIndeterminateTint();
    }

    public PorterDuff.Mode getIndeterminateTintMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mIndeterminateTintMode : null;
    }

    public BlendMode getIndeterminateTintBlendMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mIndeterminateBlendMode : null;
    }

    private void applyIndeterminateTint() {
        if (mIndeterminateDrawable != null && mProgressTintInfo != null) {
            final ProgressTintInfo tintInfo = mProgressTintInfo;
            if (tintInfo.mHasIndeterminateTint || tintInfo.mHasIndeterminateTintMode) {
                mIndeterminateDrawable = mIndeterminateDrawable.mutate();
                if (tintInfo.mHasIndeterminateTint) mIndeterminateDrawable.setTintList(tintInfo.mIndeterminateTintList);
                if (tintInfo.mHasIndeterminateTintMode && tintInfo.mIndeterminateTintMode != null) {
                    mIndeterminateDrawable.setTintMode(tintInfo.mIndeterminateTintMode);
                }
                if (mIndeterminateDrawable.isStateful()) mIndeterminateDrawable.setState(getDrawableState());
            }
        }
    }

    public void setIndeterminateDrawableTiled(Drawable d) {
        if (d != null) d = tileifyIndeterminate(d);
        setIndeterminateDrawable(d);
    }

    public Drawable getProgressDrawable() { return mProgressDrawable; }

    public void setProgressDrawable(Drawable d) {
        if (mProgressDrawable != d) {
            if (mProgressDrawable != null) {
                mProgressDrawable.setCallback(null);
                unscheduleDrawable(mProgressDrawable);
            }
            mProgressDrawable = d;
            if (d != null) {
                d.setCallback(this);
                d.setLayoutDirection(getLayoutDirection());
                if (d.isStateful()) d.setState(getDrawableState());
                int drawableHeight = d.getMinimumHeight();
                if (mMaxHeight < drawableHeight) {
                    mMaxHeight = drawableHeight;
                    requestLayout();
                }
                applyProgressTints();
            }
            if (!mIndeterminate) {
                swapCurrentDrawable(d);
                postInvalidate();
            }
            updateDrawableBounds(getWidth(), getHeight());
            updateDrawableState();
            doRefreshProgress(android.R.id.progress, mProgress, false, false, false);
            doRefreshProgress(android.R.id.secondaryProgress, mSecondaryProgress, false, false, false);
        }
    }

    boolean getMirrorForRtl() { return mMirrorForRtl; }

    private void applyProgressTints() {
        if (mProgressDrawable != null && mProgressTintInfo != null) {
            applyPrimaryProgressTint();
            applyProgressBackgroundTint();
            applySecondaryProgressTint();
        }
    }

    private void applyPrimaryProgressTint() {
        if (mProgressTintInfo.mHasProgressTint || mProgressTintInfo.mHasProgressTintMode) {
            final Drawable target = getTintTarget(android.R.id.progress, true);
            if (target != null) {
                if (mProgressTintInfo.mHasProgressTint) target.setTintList(mProgressTintInfo.mProgressTintList);
                if (mProgressTintInfo.mHasProgressTintMode && mProgressTintInfo.mProgressTintMode != null) {
                    target.setTintMode(mProgressTintInfo.mProgressTintMode);
                }
                if (target.isStateful()) target.setState(getDrawableState());
            }
        }
    }

    private void applyProgressBackgroundTint() {
        if (mProgressTintInfo.mHasProgressBackgroundTint || mProgressTintInfo.mHasProgressBackgroundTintMode) {
            final Drawable target = getTintTarget(android.R.id.background, false);
            if (target != null) {
                if (mProgressTintInfo.mHasProgressBackgroundTint) {
                    target.setTintList(mProgressTintInfo.mProgressBackgroundTintList);
                }
                if (mProgressTintInfo.mHasProgressBackgroundTintMode
                        && mProgressTintInfo.mProgressBackgroundTintMode != null) {
                    target.setTintMode(mProgressTintInfo.mProgressBackgroundTintMode);
                }
                if (target.isStateful()) target.setState(getDrawableState());
            }
        }
    }

    private void applySecondaryProgressTint() {
        if (mProgressTintInfo.mHasSecondaryProgressTint || mProgressTintInfo.mHasSecondaryProgressTintMode) {
            final Drawable target = getTintTarget(android.R.id.secondaryProgress, false);
            if (target != null) {
                if (mProgressTintInfo.mHasSecondaryProgressTint) {
                    target.setTintList(mProgressTintInfo.mSecondaryProgressTintList);
                }
                if (mProgressTintInfo.mHasSecondaryProgressTintMode
                        && mProgressTintInfo.mSecondaryProgressTintMode != null) {
                    target.setTintMode(mProgressTintInfo.mSecondaryProgressTintMode);
                }
                if (target.isStateful()) target.setState(getDrawableState());
            }
        }
    }

    public void setProgressTintList(ColorStateList tint) {
        tintInfo().mProgressTintList = tint;
        mProgressTintInfo.mHasProgressTint = true;
        if (mProgressDrawable != null) applyPrimaryProgressTint();
    }

    public ColorStateList getProgressTintList() {
        return mProgressTintInfo != null ? mProgressTintInfo.mProgressTintList : null;
    }

    public void setProgressTintMode(PorterDuff.Mode tintMode) {
        tintInfo().mProgressTintMode = tintMode;
        mProgressTintInfo.mHasProgressTintMode = true;
        if (mProgressDrawable != null) applyPrimaryProgressTint();
    }

    public void setProgressTintBlendMode(BlendMode blendMode) {
        tintInfo().mProgressBlendMode = blendMode;
        mProgressTintInfo.mHasProgressTintMode = true;
        if (mProgressDrawable != null) applyPrimaryProgressTint();
    }

    public PorterDuff.Mode getProgressTintMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mProgressTintMode : null;
    }

    public BlendMode getProgressTintBlendMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mProgressBlendMode : null;
    }

    public void setProgressBackgroundTintList(ColorStateList tint) {
        tintInfo().mProgressBackgroundTintList = tint;
        mProgressTintInfo.mHasProgressBackgroundTint = true;
        if (mProgressDrawable != null) applyProgressBackgroundTint();
    }

    public ColorStateList getProgressBackgroundTintList() {
        return mProgressTintInfo != null ? mProgressTintInfo.mProgressBackgroundTintList : null;
    }

    public void setProgressBackgroundTintMode(PorterDuff.Mode tintMode) {
        tintInfo().mProgressBackgroundTintMode = tintMode;
        mProgressTintInfo.mHasProgressBackgroundTintMode = true;
        if (mProgressDrawable != null) applyProgressBackgroundTint();
    }

    public void setProgressBackgroundTintBlendMode(BlendMode blendMode) {
        tintInfo().mProgressBackgroundBlendMode = blendMode;
        mProgressTintInfo.mHasProgressBackgroundTintMode = true;
        if (mProgressDrawable != null) applyProgressBackgroundTint();
    }

    public PorterDuff.Mode getProgressBackgroundTintMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mProgressBackgroundTintMode : null;
    }

    public BlendMode getProgressBackgroundTintBlendMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mProgressBackgroundBlendMode : null;
    }

    public void setSecondaryProgressTintList(ColorStateList tint) {
        tintInfo().mSecondaryProgressTintList = tint;
        mProgressTintInfo.mHasSecondaryProgressTint = true;
        if (mProgressDrawable != null) applySecondaryProgressTint();
    }

    public ColorStateList getSecondaryProgressTintList() {
        return mProgressTintInfo != null ? mProgressTintInfo.mSecondaryProgressTintList : null;
    }

    public void setSecondaryProgressTintMode(PorterDuff.Mode tintMode) {
        tintInfo().mSecondaryProgressTintMode = tintMode;
        mProgressTintInfo.mHasSecondaryProgressTintMode = true;
        if (mProgressDrawable != null) applySecondaryProgressTint();
    }

    public void setSecondaryProgressTintBlendMode(BlendMode blendMode) {
        tintInfo().mSecondaryProgressBlendMode = blendMode;
        mProgressTintInfo.mHasSecondaryProgressTintMode = true;
        if (mProgressDrawable != null) applySecondaryProgressTint();
    }

    public PorterDuff.Mode getSecondaryProgressTintMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mSecondaryProgressTintMode : null;
    }

    public BlendMode getSecondaryProgressTintBlendMode() {
        return mProgressTintInfo != null ? mProgressTintInfo.mSecondaryProgressBlendMode : null;
    }

    private Drawable getTintTarget(int layerId, boolean shouldFallback) {
        Drawable layer = null;
        final Drawable d = mProgressDrawable;
        if (d != null) {
            mProgressDrawable = d.mutate();
            if (d instanceof LayerDrawable) layer = ((LayerDrawable) d).findDrawableByLayerId(layerId);
            if (shouldFallback && layer == null) layer = d;
        }
        return layer;
    }

    public void setProgressDrawableTiled(Drawable d) {
        if (d != null) d = tileify(d, false);
        setProgressDrawable(d);
    }

    public Drawable getCurrentDrawable() { return mCurrentDrawable; }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mProgressDrawable || who == mIndeterminateDrawable || super.verifyDrawable(who);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mProgressDrawable != null) mProgressDrawable.jumpToCurrentState();
        if (mIndeterminateDrawable != null) mIndeterminateDrawable.jumpToCurrentState();
    }

    @Override
    public void postInvalidate() {
        if (!mNoInvalidate) super.postInvalidate();
    }

    private class RefreshProgressRunnable implements Runnable {
        public void run() {
            synchronized (ProgressBar.this) {
                final int count = mRefreshData.size();
                for (int i = 0; i < count; i++) {
                    final RefreshData rd = mRefreshData.get(i);
                    doRefreshProgress(rd.id, rd.progress, rd.fromUser, true, rd.animate);
                }
                mRefreshData.clear();
                mRefreshIsPosted = false;
            }
        }
    }

    private static final class RefreshData {
        int id;
        int progress;
        boolean fromUser;
        boolean animate;
    }

    private synchronized void doRefreshProgress(int id, int progress, boolean fromUser, boolean callBackToApp,
            boolean animate) {
        int range = mMax - mMin;
        final float scale = range > 0 ? (progress - mMin) / (float) range : 0;
        final boolean isPrimary = id == android.R.id.progress;
        // Animated primary progress is not wired; the value jumps to the new level.
        setVisualProgress(id, scale);
        if (isPrimary && callBackToApp) onProgressRefresh(scale, fromUser, progress);
    }

    private CharSequence formatStateDescription(int progress) {
        int range = mMax - mMin;
        int percent = range > 0 ? (int) ((progress - mMin) * 100f / range) : 0;
        return percent + "%";
    }

    @Override
    public void setStateDescription(CharSequence stateDescription) {
        mCustomStateDescription = stateDescription;
        super.setStateDescription(stateDescription);
    }

    void onProgressRefresh(float scale, boolean fromUser, int progress) {
        if (mCustomStateDescription == null) super.setStateDescription(formatStateDescription(mProgress));
    }

    private void setVisualProgress(int id, float progress) {
        mVisualProgress = progress;
        Drawable d = mCurrentDrawable;
        if (d instanceof LayerDrawable) {
            d = ((LayerDrawable) d).findDrawableByLayerId(id);
            if (d == null) d = mCurrentDrawable;
        }
        if (d != null) {
            final int level = (int) (progress * MAX_LEVEL);
            d.setLevel(level);
        } else {
            invalidate();
        }
        onVisualProgressChanged(id, progress);
    }

    void onVisualProgressChanged(int id, float progress) {}

    private synchronized void refreshProgress(int id, int progress, boolean fromUser, boolean animate) {
        if (mUiThreadId == Thread.currentThread().getId()) {
            doRefreshProgress(id, progress, fromUser, true, animate);
        } else {
            if (mRefreshProgressRunnable == null) mRefreshProgressRunnable = new RefreshProgressRunnable();
            final RefreshData rd = new RefreshData();
            rd.id = id;
            rd.progress = progress;
            rd.fromUser = fromUser;
            rd.animate = animate;
            mRefreshData.add(rd);
            if (mAttached && !mRefreshIsPosted) {
                post(mRefreshProgressRunnable);
                mRefreshIsPosted = true;
            }
        }
    }

    public synchronized void setProgress(int progress) { setProgressInternal(progress, false, false); }

    public void setProgress(int progress, boolean animate) { setProgressInternal(progress, false, animate); }

    synchronized boolean setProgressInternal(int progress, boolean fromUser, boolean animate) {
        if (mIndeterminate) return false;
        progress = MathUtils.constrain(progress, mMin, mMax);
        if (progress == mProgress) return false;
        mProgress = progress;
        refreshProgress(android.R.id.progress, mProgress, fromUser, animate);
        return true;
    }

    public synchronized void setSecondaryProgress(int secondaryProgress) {
        if (mIndeterminate) return;
        if (secondaryProgress < mMin) secondaryProgress = mMin;
        if (secondaryProgress > mMax) secondaryProgress = mMax;
        if (secondaryProgress != mSecondaryProgress) {
            mSecondaryProgress = secondaryProgress;
            refreshProgress(android.R.id.secondaryProgress, mSecondaryProgress, false, false);
        }
    }

    public synchronized int getProgress() { return mIndeterminate ? 0 : mProgress; }

    public synchronized int getSecondaryProgress() { return mIndeterminate ? 0 : mSecondaryProgress; }

    public synchronized int getMin() { return mMin; }

    public synchronized int getMax() { return mMax; }

    public synchronized void setMin(int min) {
        if (mMaxInitialized && min > mMax) min = mMax;
        mMinInitialized = true;
        if (mMaxInitialized && min != mMin) {
            mMin = min;
            postInvalidate();
            if (mProgress < min) mProgress = min;
            refreshProgress(android.R.id.progress, mProgress, false, false);
        } else {
            mMin = min;
        }
    }

    public synchronized void setMax(int max) {
        if (mMinInitialized && max < mMin) max = mMin;
        mMaxInitialized = true;
        if (mMinInitialized && max != mMax) {
            mMax = max;
            postInvalidate();
            if (mProgress > max) mProgress = max;
            refreshProgress(android.R.id.progress, mProgress, false, false);
        } else {
            mMax = max;
        }
    }

    public final synchronized void incrementProgressBy(int diff) { setProgress(mProgress + diff); }

    public final synchronized void incrementSecondaryProgressBy(int diff) {
        setSecondaryProgress(mSecondaryProgress + diff);
    }

    void startAnimation() {
        if (getVisibility() != VISIBLE || getWindowVisibility() != VISIBLE) return;
        if (mIndeterminateDrawable instanceof Animatable) {
            mShouldStartAnimationDrawable = true;
            mHasAnimation = false;
        } else {
            mHasAnimation = true;
            if (mInterpolator == null) mInterpolator = new LinearInterpolator();
            if (mTransformation == null) mTransformation = new Transformation();
            else mTransformation.clear();
            if (mAnimation == null) mAnimation = new AlphaAnimation(0.0f, 1.0f);
            else mAnimation.reset();
            mAnimation.setRepeatMode(mBehavior);
            mAnimation.setRepeatCount(Animation.INFINITE);
            mAnimation.setDuration(mDuration);
            mAnimation.setInterpolator(mInterpolator);
            mAnimation.setStartTime(Animation.START_ON_FIRST_FRAME);
        }
        postInvalidate();
    }

    void stopAnimation() {
        mHasAnimation = false;
        if (mIndeterminateDrawable instanceof Animatable) {
            ((Animatable) mIndeterminateDrawable).stop();
            mShouldStartAnimationDrawable = false;
        }
        postInvalidate();
    }

    public void setInterpolator(Context context, int resID) {
        setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(context, resID));
    }

    public void setInterpolator(Interpolator interpolator) { mInterpolator = interpolator; }

    public Interpolator getInterpolator() { return mInterpolator; }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible != mAggregatedIsVisible) {
            mAggregatedIsVisible = isVisible;
            if (mIndeterminate) {
                if (isVisible) startAnimation();
                else stopAnimation();
            }
            if (mCurrentDrawable != null) mCurrentDrawable.setVisible(isVisible, false);
        }
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        if (!mInDrawing) {
            if (verifyDrawable(dr)) {
                final Rect dirty = dr.getBounds();
                final int scrollX = getScrollX() + mPaddingLeft;
                final int scrollY = getScrollY() + mPaddingTop;
                invalidate(dirty.left + scrollX, dirty.top + scrollY, dirty.right + scrollX, dirty.bottom + scrollY);
            } else {
                super.invalidateDrawable(dr);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) { updateDrawableBounds(w, h); }

    private void updateDrawableBounds(int w, int h) {
        w -= mPaddingRight + mPaddingLeft;
        h -= mPaddingTop + mPaddingBottom;
        int right = w;
        int bottom = h;
        int top = 0;
        int left = 0;
        if (mIndeterminateDrawable != null) {
            if (mOnlyIndeterminate && !(mIndeterminateDrawable instanceof AnimationDrawable)) {
                final int intrinsicWidth = mIndeterminateDrawable.getIntrinsicWidth();
                final int intrinsicHeight = mIndeterminateDrawable.getIntrinsicHeight();
                if (intrinsicWidth > 0 && intrinsicHeight > 0 && w > 0 && h > 0) {
                    final float intrinsicAspect = (float) intrinsicWidth / intrinsicHeight;
                    final float boundAspect = (float) w / h;
                    if (intrinsicAspect != boundAspect) {
                        if (boundAspect > intrinsicAspect) {
                            final int width = (int) (h * intrinsicAspect);
                            left = (w - width) / 2;
                            right = left + width;
                        } else {
                            final int height = (int) (w * (1 / intrinsicAspect));
                            top = (h - height) / 2;
                            bottom = top + height;
                        }
                    }
                }
            }
            if (isLayoutRtl() && mMirrorForRtl) {
                int tempLeft = left;
                left = w - right;
                right = w - tempLeft;
            }
            mIndeterminateDrawable.setBounds(left, top, right, bottom);
        }
        if (mProgressDrawable != null) mProgressDrawable.setBounds(0, 0, right, bottom);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawTrack(canvas);
    }

    void drawTrack(Canvas canvas) {
        final Drawable d = mCurrentDrawable;
        if (d != null) {
            final int saveCount = canvas.save();
            if (isLayoutRtl() && mMirrorForRtl) {
                canvas.translate(getWidth() - mPaddingRight, mPaddingTop);
                canvas.scale(-1.0f, 1.0f);
            } else {
                canvas.translate(mPaddingLeft, mPaddingTop);
            }
            final long time = getDrawingTime();
            if (mHasAnimation) {
                mAnimation.getTransformation(time, mTransformation);
                final float scale = mTransformation.getAlpha();
                try {
                    mInDrawing = true;
                    d.setLevel((int) (scale * MAX_LEVEL));
                } finally {
                    mInDrawing = false;
                }
                postInvalidateOnAnimation();
            }
            d.draw(canvas);
            canvas.restoreToCount(saveCount);
            if (mShouldStartAnimationDrawable && d instanceof Animatable) {
                ((Animatable) d).start();
                mShouldStartAnimationDrawable = false;
            }
        }
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int dw = 0;
        int dh = 0;
        final Drawable d = mCurrentDrawable;
        if (d != null) {
            dw = Math.max(mMinWidth, Math.min(mMaxWidth, d.getIntrinsicWidth()));
            dh = Math.max(mMinHeight, Math.min(mMaxHeight, d.getIntrinsicHeight()));
        }
        updateDrawableState();
        dw += mPaddingLeft + mPaddingRight;
        dh += mPaddingTop + mPaddingBottom;
        final int measuredWidth = resolveSizeAndState(dw, widthMeasureSpec, 0);
        final int measuredHeight = resolveSizeAndState(dh, heightMeasureSpec, 0);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateDrawableState();
    }

    private void updateDrawableState() {
        final int[] state = getDrawableState();
        boolean changed = false;
        final Drawable progressDrawable = mProgressDrawable;
        if (progressDrawable != null && progressDrawable.isStateful()) changed |= progressDrawable.setState(state);
        final Drawable indeterminateDrawable = mIndeterminateDrawable;
        if (indeterminateDrawable != null && indeterminateDrawable.isStateful()) {
            changed |= indeterminateDrawable.setState(state);
        }
        if (changed) invalidate();
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mProgressDrawable != null) mProgressDrawable.setHotspot(x, y);
        if (mIndeterminateDrawable != null) mIndeterminateDrawable.setHotspot(x, y);
    }

    static class SavedState extends BaseSavedState {
        int progress;
        int secondaryProgress;

        SavedState(Parcelable superState) { super(superState); }

        private SavedState(Parcel in) {
            super(in);
            progress = in.readInt();
            secondaryProgress = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(progress);
            out.writeInt(secondaryProgress);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.progress = mProgress;
        ss.secondaryProgress = mSecondaryProgress;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setProgress(ss.progress);
        setSecondaryProgress(ss.secondaryProgress);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mIndeterminate) startAnimation();
        synchronized (this) {
            final int count = mRefreshData.size();
            for (int i = 0; i < count; i++) {
                final RefreshData rd = mRefreshData.get(i);
                doRefreshProgress(rd.id, rd.progress, rd.fromUser, true, rd.animate);
            }
            mRefreshData.clear();
        }
        mAttached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mIndeterminate) stopAnimation();
        if (mRefreshProgressRunnable != null) {
            removeCallbacks(mRefreshProgressRunnable);
            mRefreshIsPosted = false;
        }
        super.onDetachedFromWindow();
        mAttached = false;
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ProgressBar.class.getName(); }

    public boolean isAnimating() {
        return isIndeterminate() && getWindowVisibility() == VISIBLE && isShown();
    }

    private static class ProgressTintInfo {
        ColorStateList mIndeterminateTintList;
        PorterDuff.Mode mIndeterminateTintMode;
        BlendMode mIndeterminateBlendMode;
        boolean mHasIndeterminateTint;
        boolean mHasIndeterminateTintMode;
        ColorStateList mProgressTintList;
        PorterDuff.Mode mProgressTintMode;
        BlendMode mProgressBlendMode;
        boolean mHasProgressTint;
        boolean mHasProgressTintMode;
        ColorStateList mProgressBackgroundTintList;
        PorterDuff.Mode mProgressBackgroundTintMode;
        BlendMode mProgressBackgroundBlendMode;
        boolean mHasProgressBackgroundTint;
        boolean mHasProgressBackgroundTintMode;
        ColorStateList mSecondaryProgressTintList;
        PorterDuff.Mode mSecondaryProgressTintMode;
        BlendMode mSecondaryProgressBlendMode;
        boolean mHasSecondaryProgressTint;
        boolean mHasSecondaryProgressTintMode;
    }
}
