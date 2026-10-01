package android.view;

import android.content.Context;
import android.os.Handler;

/** Detects pinch (and quick-scale double-tap-drag) gestures (port of AOSP ScaleGestureDetector). */
public class ScaleGestureDetector {
    public interface OnScaleGestureListener {
        boolean onScale(ScaleGestureDetector detector);
        boolean onScaleBegin(ScaleGestureDetector detector);
        void onScaleEnd(ScaleGestureDetector detector);
    }

    public static class SimpleOnScaleGestureListener implements OnScaleGestureListener {
        public SimpleOnScaleGestureListener() {}
        public boolean onScale(ScaleGestureDetector detector) { return false; }
        public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
        public void onScaleEnd(ScaleGestureDetector detector) {}
    }

    private static final long TOUCH_STABILIZE_TIME = 128;
    private static final float SCALE_FACTOR = .5f;
    private static final int ANCHORED_SCALE_MODE_NONE = 0;
    private static final int ANCHORED_SCALE_MODE_DOUBLE_TAP = 1;
    private static final int ANCHORED_SCALE_MODE_STYLUS = 2;

    private final Context mContext;
    private final OnScaleGestureListener mListener;
    private float mFocusX;
    private float mFocusY;
    private boolean mQuickScaleEnabled;
    private boolean mStylusScaleEnabled;
    private float mCurrSpan;
    private float mPrevSpan;
    private float mInitialSpan;
    private float mCurrSpanX;
    private float mCurrSpanY;
    private float mPrevSpanX;
    private float mPrevSpanY;
    private long mCurrTime;
    private long mPrevTime;
    private boolean mInProgress;
    private final int mSpanSlop;
    private final int mMinSpan;
    private final Handler mHandler;
    private float mAnchoredScaleStartX;
    private float mAnchoredScaleStartY;
    private int mAnchoredScaleMode = ANCHORED_SCALE_MODE_NONE;
    private GestureDetector mGestureDetector;
    private boolean mEventBeforeOrAboveStartingGestureEvent;

    public ScaleGestureDetector(Context context, OnScaleGestureListener listener) { this(context, listener, null); }

    public ScaleGestureDetector(Context context, OnScaleGestureListener listener, Handler handler) {
        mContext = context;
        mListener = listener;
        final ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mSpanSlop = viewConfiguration.getScaledTouchSlop() * 2;
        mMinSpan = viewConfiguration.getScaledMinimumScalingSpan();
        mHandler = handler;
        setQuickScaleEnabled(true);
        setStylusScaleEnabled(true);
    }

    public boolean onTouchEvent(MotionEvent event) {
        mCurrTime = event.getEventTime();
        final int action = event.getActionMasked();
        if (mQuickScaleEnabled) mGestureDetector.onTouchEvent(event);
        final int count = event.getPointerCount();
        final boolean isStylusButtonDown = (event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
        final boolean anchoredScaleCancelled = mAnchoredScaleMode == ANCHORED_SCALE_MODE_STYLUS && !isStylusButtonDown;
        final boolean streamComplete = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || anchoredScaleCancelled;

        if (action == MotionEvent.ACTION_DOWN || streamComplete) {
            if (mInProgress) {
                mListener.onScaleEnd(this);
                mInProgress = false;
                mInitialSpan = 0;
                mAnchoredScaleMode = ANCHORED_SCALE_MODE_NONE;
            } else if (inAnchoredScaleMode() && streamComplete) {
                mInProgress = false;
                mInitialSpan = 0;
                mAnchoredScaleMode = ANCHORED_SCALE_MODE_NONE;
            }
            if (streamComplete) return true;
        }

        if (!mInProgress && mStylusScaleEnabled && !inAnchoredScaleMode() && !streamComplete && isStylusButtonDown) {
            mAnchoredScaleStartX = event.getX();
            mAnchoredScaleStartY = event.getY();
            mAnchoredScaleMode = ANCHORED_SCALE_MODE_STYLUS;
            mInitialSpan = 0;
        }

        final boolean configChanged = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_POINTER_DOWN || anchoredScaleCancelled;
        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? event.getActionIndex() : -1;

        float sumX = 0, sumY = 0;
        final int div = pointerUp ? count - 1 : count;
        final float focusX;
        final float focusY;
        if (inAnchoredScaleMode()) {
            focusX = mAnchoredScaleStartX;
            focusY = mAnchoredScaleStartY;
            mEventBeforeOrAboveStartingGestureEvent = event.getY() < focusY;
        } else {
            for (int i = 0; i < count; i++) {
                if (skipIndex == i) continue;
                sumX += event.getX(i);
                sumY += event.getY(i);
            }
            focusX = sumX / div;
            focusY = sumY / div;
        }

        float devSumX = 0, devSumY = 0;
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            devSumX += Math.abs(event.getX(i) - focusX);
            devSumY += Math.abs(event.getY(i) - focusY);
        }
        final float devX = devSumX / div;
        final float devY = devSumY / div;
        final float spanX = devX * 2;
        final float spanY = devY * 2;
        final float span;
        if (inAnchoredScaleMode()) span = spanY;
        else span = (float) Math.hypot(spanX, spanY);

        final boolean wasInProgress = mInProgress;
        mFocusX = focusX;
        mFocusY = focusY;
        if (!inAnchoredScaleMode() && mInProgress && (span < mMinSpan || configChanged)) {
            mListener.onScaleEnd(this);
            mInProgress = false;
            mInitialSpan = span;
        }
        if (configChanged) {
            mPrevSpanX = mCurrSpanX = spanX;
            mPrevSpanY = mCurrSpanY = spanY;
            mInitialSpan = mPrevSpan = mCurrSpan = span;
        }
        final int minSpan = inAnchoredScaleMode() ? mSpanSlop : mMinSpan;
        if (!mInProgress && span >= minSpan && (wasInProgress || Math.abs(span - mInitialSpan) > mSpanSlop)) {
            mPrevSpanX = mCurrSpanX = spanX;
            mPrevSpanY = mCurrSpanY = spanY;
            mPrevSpan = mCurrSpan = span;
            mPrevTime = mCurrTime;
            mInProgress = mListener.onScaleBegin(this);
        }
        if (action == MotionEvent.ACTION_MOVE) {
            mCurrSpanX = spanX;
            mCurrSpanY = spanY;
            mCurrSpan = span;
            boolean updatePrev = true;
            if (mInProgress) updatePrev = mListener.onScale(this);
            if (updatePrev) {
                mPrevSpanX = mCurrSpanX;
                mPrevSpanY = mCurrSpanY;
                mPrevSpan = mCurrSpan;
                mPrevTime = mCurrTime;
            }
        }
        return true;
    }

    private boolean inAnchoredScaleMode() { return mAnchoredScaleMode != ANCHORED_SCALE_MODE_NONE; }

    public void setQuickScaleEnabled(boolean scales) {
        mQuickScaleEnabled = scales;
        if (mQuickScaleEnabled && mGestureDetector == null) {
            GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    mAnchoredScaleStartX = e.getX();
                    mAnchoredScaleStartY = e.getY();
                    mAnchoredScaleMode = ANCHORED_SCALE_MODE_DOUBLE_TAP;
                    return true;
                }
            };
            mGestureDetector = new GestureDetector(mContext, gestureListener, mHandler);
        }
    }

    public boolean isQuickScaleEnabled() { return mQuickScaleEnabled; }

    public void setStylusScaleEnabled(boolean scales) { mStylusScaleEnabled = scales; }

    public boolean isStylusScaleEnabled() { return mStylusScaleEnabled; }

    public boolean isInProgress() { return mInProgress; }

    public float getFocusX() { return mFocusX; }

    public float getFocusY() { return mFocusY; }

    public float getCurrentSpan() { return mCurrSpan; }

    public float getCurrentSpanX() { return mCurrSpanX; }

    public float getCurrentSpanY() { return mCurrSpanY; }

    public float getPreviousSpan() { return mPrevSpan; }

    public float getPreviousSpanX() { return mPrevSpanX; }

    public float getPreviousSpanY() { return mPrevSpanY; }

    public float getScaleFactor() {
        if (inAnchoredScaleMode()) {
            final boolean scaleUp = (mEventBeforeOrAboveStartingGestureEvent && (mCurrSpan < mPrevSpan))
                    || (!mEventBeforeOrAboveStartingGestureEvent && (mCurrSpan > mPrevSpan));
            final float spanDiff = (Math.abs(1 - (mCurrSpan / mPrevSpan)) * SCALE_FACTOR);
            return mPrevSpan <= mSpanSlop ? 1 : scaleUp ? (1 + spanDiff) : (1 - spanDiff);
        }
        return mPrevSpan > 0 ? mCurrSpan / mPrevSpan : 1;
    }

    public long getTimeDelta() { return mCurrTime - mPrevTime; }

    public long getEventTime() { return mCurrTime; }
}
