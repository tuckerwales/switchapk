package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.android.internal.util.InternalRes;
import java.util.List;

/** Port of AOSP AbsSeekBar: a ProgressBar with a draggable thumb, tick marks and D-pad steps. */
public abstract class AbsSeekBar extends ProgressBar {
    private static final int NO_ALPHA = 0xFF;
    private static final int[] ATTRS = {
        android.R.attr.thumb, android.R.attr.thumbOffset, android.R.attr.splitTrack, android.R.attr.thumbTint,
        android.R.attr.thumbTintMode, android.R.attr.tickMark, android.R.attr.tickMarkTint,
        android.R.attr.tickMarkTintMode,
    };

    private final Rect mTempRect = new Rect();

    private Drawable mThumb;
    private ColorStateList mThumbTintList;
    private PorterDuff.Mode mThumbTintMode;
    private BlendMode mThumbBlendMode;
    private boolean mHasThumbTint;
    private boolean mHasThumbTintMode;

    private Drawable mTickMark;
    private ColorStateList mTickMarkTintList;
    private PorterDuff.Mode mTickMarkTintMode;
    private BlendMode mTickMarkBlendMode;
    private boolean mHasTickMarkTint;
    private boolean mHasTickMarkTintMode;

    private int mThumbOffset;
    private boolean mSplitTrack;

    /** Added to the touch position, in progress units (RatingBar rounds a touch up to the next step). */
    float mTouchProgressOffset;
    boolean mIsUserSeekable = true;
    private int mKeyProgressIncrement = 1;
    private float mDisabledAlpha;
    private final int mScaledTouchSlop;
    private float mTouchDownX;
    private boolean mIsDragging;

    public AbsSeekBar(Context context) { super(context); mScaledTouchSlop = 8; }

    public AbsSeekBar(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public AbsSeekBar(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public AbsSeekBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        final Drawable thumb = a.getDrawable(0);
        setThumb(thumb);
        if (a.hasValue(4)) {
            mThumbTintMode = parseTintMode(a.getInt(4, -1));
            mHasThumbTintMode = true;
        }
        if (a.hasValue(3)) {
            mThumbTintList = a.getColorStateList(3);
            mHasThumbTint = true;
        }
        final Drawable tickMark = a.getDrawable(5);
        setTickMark(tickMark);
        if (a.hasValue(7)) {
            mTickMarkTintMode = parseTintMode(a.getInt(7, -1));
            mHasTickMarkTintMode = true;
        }
        if (a.hasValue(6)) {
            mTickMarkTintList = a.getColorStateList(6);
            mHasTickMarkTint = true;
        }
        mSplitTrack = a.getBoolean(2, false);
        final int thumbOffset = a.getDimensionPixelOffset(1, getThumbOffset());
        setThumbOffset(thumbOffset);
        a.recycle();

        boolean useDisabledAlpha = true;
        int useDisabledAlphaAttr = InternalRes.attr("useDisabledAlpha");
        if (useDisabledAlphaAttr != 0) {
            TypedArray u = context.obtainStyledAttributes(attrs, new int[] {useDisabledAlphaAttr}, defStyleAttr,
                    defStyleRes);
            useDisabledAlpha = u.getBoolean(0, true);
            u.recycle();
        }
        if (useDisabledAlpha) {
            final TypedArray ta = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.disabledAlpha}, 0, 0);
            mDisabledAlpha = ta.getFloat(0, 0.5f);
            ta.recycle();
        } else {
            mDisabledAlpha = 1.0f;
        }
        applyThumbTint();
        applyTickMarkTint();
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setThumb(Drawable thumb) {
        final boolean needUpdate;
        if (mThumb != null && thumb != mThumb) {
            mThumb.setCallback(null);
            needUpdate = true;
        } else {
            needUpdate = false;
        }
        if (thumb != null) {
            thumb.setCallback(this);
            thumb.setLayoutDirection(getLayoutDirection());
            mThumbOffset = thumb.getIntrinsicWidth() / 2;
            if (needUpdate && (thumb.getIntrinsicWidth() != mThumb.getIntrinsicWidth()
                    || thumb.getIntrinsicHeight() != mThumb.getIntrinsicHeight())) {
                requestLayout();
            }
        }
        mThumb = thumb;
        applyThumbTint();
        invalidate();
        if (needUpdate) {
            updateThumbAndTrackPos(getWidth(), getHeight());
            if (thumb != null && thumb.isStateful()) thumb.setState(getDrawableState());
        }
    }

    public Drawable getThumb() { return mThumb; }

    public void setThumbTintList(ColorStateList tint) {
        mThumbTintList = tint;
        mHasThumbTint = true;
        applyThumbTint();
    }

    public ColorStateList getThumbTintList() { return mThumbTintList; }

    public void setThumbTintMode(PorterDuff.Mode tintMode) {
        mThumbTintMode = tintMode;
        mHasThumbTintMode = true;
        applyThumbTint();
    }

    public void setThumbTintBlendMode(BlendMode blendMode) {
        mThumbBlendMode = blendMode;
        mHasThumbTintMode = true;
        applyThumbTint();
    }

    public PorterDuff.Mode getThumbTintMode() { return mThumbTintMode; }

    public BlendMode getThumbTintBlendMode() { return mThumbBlendMode; }

    private void applyThumbTint() {
        if (mThumb != null && (mHasThumbTint || mHasThumbTintMode)) {
            mThumb = mThumb.mutate();
            if (mHasThumbTint) mThumb.setTintList(mThumbTintList);
            if (mHasThumbTintMode && mThumbTintMode != null) mThumb.setTintMode(mThumbTintMode);
            if (mThumb.isStateful()) mThumb.setState(getDrawableState());
        }
    }

    public int getThumbOffset() { return mThumbOffset; }

    public void setThumbOffset(int thumbOffset) {
        mThumbOffset = thumbOffset;
        invalidate();
    }

    public void setSplitTrack(boolean splitTrack) {
        mSplitTrack = splitTrack;
        invalidate();
    }

    public boolean getSplitTrack() { return mSplitTrack; }

    public void setTickMark(Drawable tickMark) {
        if (mTickMark != null) mTickMark.setCallback(null);
        mTickMark = tickMark;
        if (tickMark != null) {
            tickMark.setCallback(this);
            tickMark.setLayoutDirection(getLayoutDirection());
            if (tickMark.isStateful()) tickMark.setState(getDrawableState());
            applyTickMarkTint();
        }
        invalidate();
    }

    public Drawable getTickMark() { return mTickMark; }

    public void setTickMarkTintList(ColorStateList tint) {
        mTickMarkTintList = tint;
        mHasTickMarkTint = true;
        applyTickMarkTint();
    }

    public ColorStateList getTickMarkTintList() { return mTickMarkTintList; }

    public void setTickMarkTintMode(PorterDuff.Mode tintMode) {
        mTickMarkTintMode = tintMode;
        mHasTickMarkTintMode = true;
        applyTickMarkTint();
    }

    public void setTickMarkTintBlendMode(BlendMode blendMode) {
        mTickMarkBlendMode = blendMode;
        mHasTickMarkTintMode = true;
        applyTickMarkTint();
    }

    public PorterDuff.Mode getTickMarkTintMode() { return mTickMarkTintMode; }

    public BlendMode getTickMarkTintBlendMode() { return mTickMarkBlendMode; }

    private void applyTickMarkTint() {
        if (mTickMark != null && (mHasTickMarkTint || mHasTickMarkTintMode)) {
            mTickMark = mTickMark.mutate();
            if (mHasTickMarkTint) mTickMark.setTintList(mTickMarkTintList);
            if (mHasTickMarkTintMode && mTickMarkTintMode != null) mTickMark.setTintMode(mTickMarkTintMode);
            if (mTickMark.isStateful()) mTickMark.setState(getDrawableState());
        }
    }

    public void setKeyProgressIncrement(int increment) {
        mKeyProgressIncrement = increment < 0 ? -increment : increment;
    }

    public int getKeyProgressIncrement() { return mKeyProgressIncrement; }

    @Override
    public synchronized void setMin(int min) {
        super.setMin(min);
        int range = getMax() - getMin();
        if ((mKeyProgressIncrement == 0) || (range / mKeyProgressIncrement > 20)) {
            setKeyProgressIncrement(Math.max(1, Math.round((float) range / 20)));
        }
    }

    @Override
    public synchronized void setMax(int max) {
        super.setMax(max);
        int range = getMax() - getMin();
        if ((mKeyProgressIncrement == 0) || (range / mKeyProgressIncrement > 20)) {
            setKeyProgressIncrement(Math.max(1, Math.round((float) range / 20)));
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mThumb || who == mTickMark || super.verifyDrawable(who);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mThumb != null) mThumb.jumpToCurrentState();
        if (mTickMark != null) mTickMark.jumpToCurrentState();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable progressDrawable = getProgressDrawable();
        if (progressDrawable != null && mDisabledAlpha < 1.0f) {
            progressDrawable.setAlpha(isEnabled() ? NO_ALPHA : (int) (NO_ALPHA * mDisabledAlpha));
        }
        final Drawable thumb = mThumb;
        if (thumb != null && thumb.isStateful() && thumb.setState(getDrawableState())) invalidateDrawable(thumb);
        final Drawable tickMark = mTickMark;
        if (tickMark != null && tickMark.isStateful() && tickMark.setState(getDrawableState())) {
            invalidateDrawable(tickMark);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mThumb != null) mThumb.setHotspot(x, y);
    }

    @Override
    void onVisualProgressChanged(int id, float scale) {
        super.onVisualProgressChanged(id, scale);
        if (id == android.R.id.progress) {
            final Drawable thumb = mThumb;
            if (thumb != null) {
                setThumbPos(getWidth(), thumb, scale, Integer.MIN_VALUE);
                invalidate();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateThumbAndTrackPos(w, h);
    }

    private void updateThumbAndTrackPos(int w, int h) {
        final int paddedHeight = h - mPaddingTop - mPaddingBottom;
        final Drawable track = getCurrentDrawable();
        final Drawable thumb = mThumb;
        final int trackHeight = Math.min(mMaxHeight, paddedHeight);
        final int thumbHeight = thumb == null ? 0 : thumb.getIntrinsicHeight();
        final int trackOffset;
        final int thumbOffset;
        if (thumbHeight > trackHeight) {
            final int offsetHeight = (paddedHeight - thumbHeight) / 2;
            trackOffset = offsetHeight + (thumbHeight - trackHeight) / 2;
            thumbOffset = offsetHeight;
        } else {
            final int offsetHeight = (paddedHeight - trackHeight) / 2;
            trackOffset = offsetHeight;
            thumbOffset = offsetHeight + (trackHeight - thumbHeight) / 2;
        }
        if (track != null) {
            final int trackWidth = w - mPaddingRight - mPaddingLeft;
            track.setBounds(0, trackOffset, trackWidth, trackOffset + trackHeight);
        }
        if (thumb != null) setThumbPos(w, thumb, getScale(), thumbOffset);
    }

    private float getScale() {
        int min = getMin();
        int max = getMax();
        int range = max - min;
        return range > 0 ? (getProgress() - min) / (float) range : 0;
    }

    private void setThumbPos(int w, Drawable thumb, float scale, int offset) {
        int available = w - mPaddingLeft - mPaddingRight;
        final int thumbWidth = thumb.getIntrinsicWidth();
        final int thumbHeight = thumb.getIntrinsicHeight();
        available -= thumbWidth;
        available += mThumbOffset * 2;
        final int thumbPos = (int) (scale * available + 0.5f);
        final int top;
        final int bottom;
        if (offset == Integer.MIN_VALUE) {
            final Rect oldBounds = thumb.getBounds();
            top = oldBounds.top;
            bottom = oldBounds.bottom;
        } else {
            top = offset;
            bottom = offset + thumbHeight;
        }
        final int left = (isLayoutRtl() && mMirrorForRtl) ? available - thumbPos : thumbPos;
        final int right = left + thumbWidth;
        final Drawable background = getBackground();
        if (background != null) {
            final int offsetX = mPaddingLeft - mThumbOffset;
            final int offsetY = mPaddingTop;
            background.setHotspotBounds(left + offsetX, top + offsetY, right + offsetX, bottom + offsetY);
        }
        thumb.setBounds(left, top, right, bottom);
    }

    @Override
    public void setSystemGestureExclusionRects(List<Rect> rects) { super.setSystemGestureExclusionRects(rects); }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        final Drawable thumb = mThumb;
        if (thumb != null) {
            setThumbPos(getWidth(), thumb, getScale(), Integer.MIN_VALUE);
            invalidate();
        }
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawThumb(canvas);
    }

    @Override
    void drawTrack(Canvas canvas) {
        final Drawable thumbDrawable = mThumb;
        if (thumbDrawable != null && mSplitTrack) {
            final Insets insets = thumbDrawable.getOpticalInsets();
            final Rect tempRect = mTempRect;
            thumbDrawable.copyBounds(tempRect);
            tempRect.offset(mPaddingLeft - mThumbOffset, mPaddingTop);
            tempRect.left += insets.left;
            tempRect.right -= insets.right;
            final int saveCount = canvas.save();
            canvas.clipRect(tempRect, Region.Op.DIFFERENCE);
            super.drawTrack(canvas);
            drawTickMarks(canvas);
            canvas.restoreToCount(saveCount);
        } else {
            super.drawTrack(canvas);
            drawTickMarks(canvas);
        }
    }

    protected void drawTickMarks(Canvas canvas) {
        if (mTickMark != null) {
            final int count = getMax() - getMin();
            if (count > 1) {
                final int w = mTickMark.getIntrinsicWidth();
                final int h = mTickMark.getIntrinsicHeight();
                final int halfW = w >= 0 ? w / 2 : 1;
                final int halfH = h >= 0 ? h / 2 : 1;
                mTickMark.setBounds(-halfW, -halfH, halfW, halfH);
                final float spacing = (getWidth() - mPaddingLeft - mPaddingRight) / (float) count;
                final int saveCount = canvas.save();
                canvas.translate(mPaddingLeft, getHeight() / 2);
                for (int i = 0; i <= count; i++) {
                    mTickMark.draw(canvas);
                    canvas.translate(spacing, 0);
                }
                canvas.restoreToCount(saveCount);
            }
        }
    }

    void drawThumb(Canvas canvas) {
        if (mThumb != null) {
            final int saveCount = canvas.save();
            canvas.translate(mPaddingLeft - mThumbOffset, mPaddingTop);
            mThumb.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Drawable d = getCurrentDrawable();
        int thumbHeight = mThumb == null ? 0 : mThumb.getIntrinsicHeight();
        int dw = 0;
        int dh = 0;
        if (d != null) {
            dw = Math.max(mMinWidth, Math.min(mMaxWidth, d.getIntrinsicWidth()));
            dh = Math.max(mMinHeight, Math.min(mMaxHeight, d.getIntrinsicHeight()));
            dh = Math.max(thumbHeight, dh);
        }
        dw += mPaddingLeft + mPaddingRight;
        dh += mPaddingTop + mPaddingBottom;
        setMeasuredDimension(resolveSizeAndState(dw, widthMeasureSpec, 0),
                resolveSizeAndState(dh, heightMeasureSpec, 0));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mIsUserSeekable || !isEnabled()) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isInScrollingContainer()) mTouchDownX = event.getX();
                else startDrag(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsDragging) {
                    trackTouchEvent(event);
                } else {
                    final float x = event.getX();
                    if (Math.abs(x - mTouchDownX) > mScaledTouchSlop) startDrag(event);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsDragging) {
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                    setPressed(false);
                } else {
                    onStartTrackingTouch();
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                }
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsDragging) {
                    onStopTrackingTouch();
                    setPressed(false);
                }
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    private void setHotspot(float x, float y) {
        final Drawable bg = getBackground();
        if (bg != null) bg.setHotspot(x, y);
    }

    private void startDrag(MotionEvent event) {
        setPressed(true);
        if (mThumb != null) invalidate(mThumb.getBounds());
        onStartTrackingTouch();
        trackTouchEvent(event);
        attemptClaimDrag();
    }

    private void trackTouchEvent(MotionEvent event) {
        final int x = Math.round(event.getX());
        final int y = Math.round(event.getY());
        final int width = getWidth();
        final int availableWidth = width - mPaddingLeft - mPaddingRight;
        final float scale;
        float progress = 0.0f;
        if (isLayoutRtl() && mMirrorForRtl) {
            if (x > width - mPaddingRight) {
                scale = 0.0f;
            } else if (x < mPaddingLeft) {
                scale = 1.0f;
            } else {
                scale = (availableWidth - x + mPaddingLeft) / (float) availableWidth;
                progress = mTouchProgressOffset;
            }
        } else {
            if (x < mPaddingLeft) {
                scale = 0.0f;
            } else if (x > width - mPaddingRight) {
                scale = 1.0f;
            } else {
                scale = (x - mPaddingLeft) / (float) availableWidth;
                progress = mTouchProgressOffset;
            }
        }
        final int range = getMax() - getMin();
        progress += scale * range + getMin();
        setHotspot(x, y);
        setProgressInternal(Math.round(progress), true, false);
    }

    private void attemptClaimDrag() {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    void onStartTrackingTouch() { mIsDragging = true; }

    void onStopTrackingTouch() { mIsDragging = false; }

    void onKeyChange() {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isEnabled()) {
            int increment = mKeyProgressIncrement;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MINUS:
                    increment = -increment;
                    // fallthrough
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_EQUALS:
                    increment = isLayoutRtl() ? -increment : increment;
                    if (setProgressInternal(getProgress() + increment, true, true)) {
                        onKeyChange();
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return AbsSeekBar.class.getName(); }
}
