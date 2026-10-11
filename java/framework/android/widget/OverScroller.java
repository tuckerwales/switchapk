package android.widget;

import android.content.Context;
import android.os.SystemClock;
import android.view.ViewConfiguration;

/**
 * Scroller that can overshoot and spring back (AOSP OverScroller).
 * A null interpolator means the viscous-fluid scroll; the Context constructor uses it and the
 * flywheel fling.
 */
public class OverScroller {
    private static final int DEFAULT_DURATION = 250;
    private static final int SCROLL_MODE = 0;
    private static final int FLING_MODE = 1;

    private int mMode;
    private final SplineOverScroller mScrollerX;
    private final SplineOverScroller mScrollerY;
    private final boolean mFlywheel;
    private android.view.animation.Interpolator mInterpolator;

    public OverScroller(Context context) {
        this(context, null);
    }

    public OverScroller(Context context, android.view.animation.Interpolator interpolator) {
        this(context, interpolator, true);
    }

    /** @hide AOSP's own (public in source, hidden from the SDK). */
    public OverScroller(Context context, android.view.animation.Interpolator interpolator, boolean flywheel) {
        mInterpolator = interpolator;
        mFlywheel = flywheel;
        mScrollerX = new SplineOverScroller(context);
        mScrollerY = new SplineOverScroller(context);
    }

    @Deprecated
    public OverScroller(Context context, android.view.animation.Interpolator interpolator, float bounceCoefficientX,
            float bounceCoefficientY) {
        this(context, interpolator, true);
    }

    @Deprecated
    public OverScroller(Context context, android.view.animation.Interpolator interpolator, float bounceCoefficientX,
            float bounceCoefficientY, boolean flywheel) {
        this(context, interpolator, flywheel);
    }

    void setInterpolator(android.view.animation.Interpolator interpolator) {
        mInterpolator = interpolator;
    }

    public final void setFriction(float friction) {
        mScrollerX.setFriction(friction);
        mScrollerY.setFriction(friction);
    }

    public final boolean isFinished() { return mScrollerX.mFinished && mScrollerY.mFinished; }

    public final void forceFinished(boolean finished) { mScrollerX.mFinished = mScrollerY.mFinished = finished; }

    public final int getCurrX() { return mScrollerX.mCurrentPosition; }

    public final int getCurrY() { return mScrollerY.mCurrentPosition; }

    public float getCurrVelocity() {
        return (float) Math.hypot(mScrollerX.mCurrVelocity, mScrollerY.mCurrVelocity);
    }

    public final int getStartX() { return mScrollerX.mStart; }

    public final int getStartY() { return mScrollerY.mStart; }

    public final int getFinalX() { return mScrollerX.mFinal; }

    public final int getFinalY() { return mScrollerY.mFinal; }

    public boolean computeScrollOffset() {
        if (isFinished()) return false;
        switch (mMode) {
            case SCROLL_MODE: {
                long elapsed = SystemClock.uptimeMillis() - mScrollerX.mStartTime;
                int duration = mScrollerX.mDuration;
                if (elapsed < duration) {
                    float x = elapsed / (float) duration;
                    float q = mInterpolator == null ? FlingMath.viscous(x) : mInterpolator.getInterpolation(x);
                    mScrollerX.updateScroll(q);
                    mScrollerY.updateScroll(q);
                } else {
                    abortAnimation();
                }
                break;
            }
            case FLING_MODE:
                if (!mScrollerX.mFinished && !mScrollerX.update() && !mScrollerX.continueWhenFinished()) {
                    mScrollerX.finish();
                }
                if (!mScrollerY.mFinished && !mScrollerY.update() && !mScrollerY.continueWhenFinished()) {
                    mScrollerY.finish();
                }
                break;
            default:
                break;
        }
        return true;
    }

    public void startScroll(int startX, int startY, int dx, int dy) {
        startScroll(startX, startY, dx, dy, DEFAULT_DURATION);
    }

    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mMode = SCROLL_MODE;
        mScrollerX.startScroll(startX, dx, duration);
        mScrollerY.startScroll(startY, dy, duration);
    }

    public boolean springBack(int startX, int startY, int minX, int maxX, int minY, int maxY) {
        mMode = FLING_MODE;
        boolean springX = mScrollerX.springback(startX, minX, maxX);
        boolean springY = mScrollerY.springback(startY, minY, maxY);
        return springX || springY;
    }

    public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
        fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, 0, 0);
    }

    public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY,
            int overX, int overY) {
        if (mFlywheel && !isFinished()) {
            float oldX = mScrollerX.mCurrVelocity;
            float oldY = mScrollerY.mCurrVelocity;
            if (Math.signum(velocityX) == Math.signum(oldX) && Math.signum(velocityY) == Math.signum(oldY)) {
                velocityX += oldX;
                velocityY += oldY;
            }
        }
        mMode = FLING_MODE;
        mScrollerX.fling(startX, velocityX, minX, maxX, overX);
        mScrollerY.fling(startY, velocityY, minY, maxY, overY);
    }

    public void notifyHorizontalEdgeReached(int startX, int finalX, int overX) {
        mScrollerX.notifyEdgeReached(startX, finalX, overX);
    }

    public void notifyVerticalEdgeReached(int startY, int finalY, int overY) {
        mScrollerY.notifyEdgeReached(startY, finalY, overY);
    }

    public boolean isOverScrolled() {
        return (!mScrollerX.mFinished && mScrollerX.mState != SplineOverScroller.SPLINE)
                || (!mScrollerY.mFinished && mScrollerY.mState != SplineOverScroller.SPLINE);
    }

    public void abortAnimation() {
        mScrollerX.finish();
        mScrollerY.finish();
    }

    /** One axis of an overscroller (AOSP SplineOverScroller). */
    static class SplineOverScroller {
        static final int SPLINE = 0;
        static final int CUBIC = 1;
        static final int BALLISTIC = 2;
        private static final float GRAVITY = 2000.0f;

        private int mStart;
        private int mCurrentPosition;
        private int mFinal;
        private int mVelocity;
        private float mCurrVelocity;
        private float mDeceleration;
        private long mStartTime;
        private int mDuration;
        private int mSplineDuration;
        private int mSplineDistance;
        private boolean mFinished = true;
        private int mOver;
        private float mFlingFriction = ViewConfiguration.getScrollFriction();
        private int mState = SPLINE;
        private final float mPhysicalCoeff;

        SplineOverScroller(Context context) {
            float ppi = FlingMath.pixelsPerInch(context.getResources().getDisplayMetrics().density);
            mPhysicalCoeff = FlingMath.deceleration(ppi, FlingMath.LOOK);
        }

        void setFriction(float friction) { mFlingFriction = friction; }

        void updateScroll(float q) { mCurrentPosition = mStart + Math.round(q * (mFinal - mStart)); }

        private static float getDeceleration(int velocity) { return velocity > 0 ? -GRAVITY : GRAVITY; }

        void startScroll(int start, int distance, int duration) {
            mFinished = false;
            mCurrentPosition = mStart = start;
            mFinal = start + distance;
            mStartTime = SystemClock.uptimeMillis();
            mDuration = duration;
            mDeceleration = 0;
            mVelocity = 0;
            mCurrVelocity = 0;
            mState = SPLINE;
        }

        void finish() {
            mCurrentPosition = mFinal;
            mFinished = true;
        }

        boolean springback(int start, int min, int max) {
            mFinished = true;
            mCurrentPosition = mStart = mFinal = start;
            mVelocity = 0;
            mStartTime = SystemClock.uptimeMillis();
            mDuration = 0;
            if (start < min) startSpringback(start, min, 0);
            else if (start > max) startSpringback(start, max, 0);
            return !mFinished;
        }

        private void startSpringback(int start, int end, int velocity) {
            mFinished = false;
            mState = CUBIC;
            mCurrentPosition = mStart = start;
            mFinal = end;
            int delta = start - end;
            mDeceleration = getDeceleration(delta);
            mVelocity = -delta;
            mOver = Math.abs(delta);
            mDuration = (int) (1000.0 * Math.sqrt(-2.0 * delta / mDeceleration));
        }

        void fling(int start, int velocity, int min, int max, int over) {
            mOver = over;
            mFinished = false;
            mCurrVelocity = mVelocity = velocity;
            mDuration = mSplineDuration = 0;
            mStartTime = SystemClock.uptimeMillis();
            mCurrentPosition = mStart = start;
            if (start > max || start < min) {
                startAfterEdge(start, min, max, velocity);
                return;
            }
            mState = SPLINE;
            double total = 0;
            if (velocity != 0) {
                mDuration = mSplineDuration = FlingMath.flingDuration(velocity, mFlingFriction, mPhysicalCoeff);
                total = FlingMath.flingDistance(velocity, mFlingFriction, mPhysicalCoeff);
            }
            mSplineDistance = (int) (total * Math.signum(velocity));
            mFinal = start + mSplineDistance;
            if (mFinal < min) {
                mDuration = FlingMath.adjustDuration(mStart, mFinal, min, mDuration);
                mFinal = min;
            }
            if (mFinal > max) {
                mDuration = FlingMath.adjustDuration(mStart, mFinal, max, mDuration);
                mFinal = max;
            }
        }

        private void fitOnBounceCurve(int start, int end, int velocity) {
            float durationToApex = -velocity / mDeceleration;
            float distanceToApex = (float) velocity * velocity / 2.0f / Math.abs(mDeceleration);
            float distanceToEdge = Math.abs(end - start);
            float totalDuration = (float) Math.sqrt(2.0 * (distanceToApex + distanceToEdge) / Math.abs(mDeceleration));
            mStartTime -= (int) (1000.0f * (totalDuration - durationToApex));
            mCurrentPosition = mStart = end;
            mVelocity = (int) (-mDeceleration * totalDuration);
        }

        private void startBounceAfterEdge(int start, int end, int velocity) {
            mDeceleration = getDeceleration(velocity == 0 ? start - end : velocity);
            fitOnBounceCurve(start, end, velocity);
            onEdgeReached();
        }

        private void startAfterEdge(int start, int min, int max, int velocity) {
            if (start > min && start < max) {
                mFinished = true;
                return;
            }
            boolean positive = start > max;
            int edge = positive ? max : min;
            int overDistance = start - edge;
            boolean keepIncreasing = overDistance * (long) velocity >= 0;
            if (keepIncreasing) {
                startBounceAfterEdge(start, edge, velocity);
            } else {
                double total = FlingMath.flingDistance(velocity, mFlingFriction, mPhysicalCoeff);
                if (total > Math.abs(overDistance)) {
                    fling(start, velocity, positive ? min : start, positive ? start : max, mOver);
                } else {
                    startSpringback(start, edge, velocity);
                }
            }
        }

        void notifyEdgeReached(int start, int end, int over) {
            if (mState == SPLINE) {
                mOver = over;
                mStartTime = SystemClock.uptimeMillis();
                startAfterEdge(start, end, end, (int) mCurrVelocity);
            }
        }

        private void onEdgeReached() {
            float velocitySquared = (float) mVelocity * mVelocity;
            float distance = velocitySquared / (2.0f * Math.abs(mDeceleration));
            float sign = Math.signum(mVelocity);
            if (distance > mOver) {
                mDeceleration = -sign * velocitySquared / (2.0f * Math.max(mOver, 1));
                distance = mOver;
            }
            mOver = (int) distance;
            mState = BALLISTIC;
            mFinal = mStart + (int) (mVelocity > 0 ? distance : -distance);
            mDuration = -(int) (1000.0f * mVelocity / mDeceleration);
        }

        boolean continueWhenFinished() {
            switch (mState) {
                case SPLINE:
                    if (mDuration < mSplineDuration) {
                        mCurrentPosition = mStart = mFinal;
                        mVelocity = (int) mCurrVelocity;
                        mDeceleration = getDeceleration(mVelocity);
                        mStartTime += mDuration;
                        onEdgeReached();
                    } else {
                        return false;
                    }
                    break;
                case BALLISTIC:
                    mStartTime += mDuration;
                    startSpringback(mFinal, mStart, 0);
                    break;
                case CUBIC:
                default:
                    return false;
            }
            update();
            return true;
        }

        boolean update() {
            long currentTime = SystemClock.uptimeMillis() - mStartTime;
            if (currentTime == 0) return mDuration > 0;
            if (currentTime > mDuration) return false;
            double distance = 0;
            switch (mState) {
                case SPLINE: {
                    float t = mSplineDuration > 0 ? (float) currentTime / mSplineDuration : 1f;
                    float[] velocityCoef = new float[1];
                    float distanceCoef = FlingMath.distanceCoef(t, velocityCoef);
                    distance = distanceCoef * mSplineDistance;
                    mCurrVelocity = mSplineDuration > 0
                            ? velocityCoef[0] * mSplineDistance / mSplineDuration * 1000.0f : 0;
                    break;
                }
                case BALLISTIC: {
                    float t = currentTime / 1000.0f;
                    mCurrVelocity = mVelocity + mDeceleration * t;
                    distance = mVelocity * t + mDeceleration * t * t / 2.0f;
                    break;
                }
                case CUBIC: {
                    float t = (float) currentTime / mDuration;
                    float t2 = t * t;
                    float sign = Math.signum(mVelocity);
                    distance = sign * mOver * (3.0f * t2 - 2.0f * t * t2);
                    mCurrVelocity = sign * mOver * 6.0f * (-t + t2);
                    break;
                }
                default:
                    break;
            }
            mCurrentPosition = mStart + (int) Math.round(distance);
            return true;
        }
    }
}
