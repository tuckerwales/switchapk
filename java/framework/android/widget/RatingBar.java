package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.util.AttributeSet;

/** Port of AOSP RatingBar: stars are the tiled progress drawable, steps follow stepSize. */
public class RatingBar extends AbsSeekBar {
    public interface OnRatingBarChangeListener {
        void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser);
    }

    private int mNumStars = 5;
    private int mProgressOnStartTracking;
    private OnRatingBarChangeListener mOnRatingBarChangeListener;

    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.numStars, android.R.attr.isIndicator, android.R.attr.rating,
                android.R.attr.stepSize}, defStyleAttr, defStyleRes);
        final int numStars = a.getInt(0, mNumStars);
        setIsIndicator(a.getBoolean(1, !mIsUserSeekable));
        final float rating = a.getFloat(2, -1);
        final float stepSize = a.getFloat(3, -1);
        a.recycle();
        if (numStars > 0 && numStars != mNumStars) setNumStars(numStars);
        if (stepSize >= 0) setStepSize(stepSize);
        else setStepSize(0.5f);
        if (rating >= 0) setRating(rating);
        // A touch inside a star fills up to that fractional area (slightly more than 1 so boundaries round up).
        mTouchProgressOffset = 1.1f;
    }

    public RatingBar(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.ratingBarStyle); }

    public RatingBar(Context context) { this(context, null); }

    public void setOnRatingBarChangeListener(OnRatingBarChangeListener listener) {
        mOnRatingBarChangeListener = listener;
    }

    public OnRatingBarChangeListener getOnRatingBarChangeListener() { return mOnRatingBarChangeListener; }

    public void setIsIndicator(boolean isIndicator) {
        mIsUserSeekable = !isIndicator;
        if (isIndicator) setFocusable(FOCUSABLE_AUTO);
        else setFocusable(FOCUSABLE);
    }

    public boolean isIndicator() { return !mIsUserSeekable; }

    public void setNumStars(final int numStars) {
        if (numStars <= 0) return;
        mNumStars = numStars;
        requestLayout();
    }

    public int getNumStars() { return mNumStars; }

    public void setRating(float rating) { setProgress(Math.round(rating * getProgressPerStar())); }

    public float getRating() { return getProgress() / getProgressPerStar(); }

    public void setStepSize(float stepSize) {
        if (stepSize <= 0) return;
        final float newMax = mNumStars / stepSize;
        int newProgress = (int) (newMax / getMax() * getProgress());
        setMax((int) newMax);
        setProgress(newProgress);
    }

    public float getStepSize() { return (float) getNumStars() / getMax(); }

    private float getProgressPerStar() {
        if (mNumStars > 0) return 1f * getMax() / mNumStars;
        return 1;
    }

    @Override
    Shape getDrawableShape() { return new RectShape(); }

    @Override
    void onProgressRefresh(float scale, boolean fromUser, int progress) {
        super.onProgressRefresh(scale, fromUser, progress);
        updateSecondaryProgress(progress);
        if (!fromUser) dispatchRatingChange(false);
    }

    private void updateSecondaryProgress(int progress) {
        final float ratio = getProgressPerStar();
        if (ratio > 0) {
            final float progressInStars = progress / ratio;
            final int secondaryProgress = (int) (Math.ceil(progressInStars) * ratio);
            setSecondaryProgress(secondaryProgress);
        }
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mSampleWidth > 0) {
            final int width = mSampleWidth * mNumStars;
            setMeasuredDimension(resolveSizeAndState(width, widthMeasureSpec, 0), getMeasuredHeight());
        }
    }

    @Override
    void onStartTrackingTouch() {
        mProgressOnStartTracking = getProgress();
        super.onStartTrackingTouch();
    }

    @Override
    void onStopTrackingTouch() {
        super.onStopTrackingTouch();
        if (getProgress() != mProgressOnStartTracking) dispatchRatingChange(true);
    }

    @Override
    void onKeyChange() {
        super.onKeyChange();
        dispatchRatingChange(true);
    }

    void dispatchRatingChange(boolean fromUser) {
        if (mOnRatingBarChangeListener != null) {
            mOnRatingBarChangeListener.onRatingChanged(this, getRating(), fromUser);
        }
    }

    @Override
    public synchronized void setMax(int max) {
        if (max <= 0) return;
        super.setMax(max);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return RatingBar.class.getName(); }
}
