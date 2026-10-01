package android.widget;

import android.content.Context;
import android.os.SystemClock;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

/**
 * Scroll animation for a start position and a distance (AOSP Scroller).
 * Fling uses the spline from {@link FlingMath}; a null interpolator means
 * the viscous-fluid curve.
 */
public class Scroller {
    private static final int DEFAULT_DURATION = 250;
    private static final int SCROLL_MODE = 0;
    private static final int FLING_MODE = 1;

    private int mMode;
    private int mStartX;
    private int mStartY;
    private int mFinalX;
    private int mFinalY;
    private int mMinX;
    private int mMaxX;
    private int mMinY;
    private int mMaxY;
    private int mCurrX;
    private int mCurrY;
    private long mStartTime;
    private int mDuration;
    private float mDurationReciprocal;
    private float mDeltaX;
    private float mDeltaY;
    private boolean mFinished = true;
    private boolean mFlywheel = true;
    private final Interpolator mInterpolator;
    private float mVelocity;
    private float mCurrVelocity;
    private int mDistance;
    private float mPhysicalCoeff;
    private float mFlingFriction = ViewConfiguration.getScrollFriction();

    public Scroller(Context context) {
        // Apps targeting Honeycomb and later get flywheel flings. SDK here is 29.
        this(context, null, true);
    }

    public Scroller(Context context, Interpolator interpolator) { this(context, interpolator, true); }

    public Scroller(Context context, Interpolator interpolator, boolean flywheel) {
        mFinished = true;
        mInterpolator = interpolator;
        float ppi = FlingMath.pixelsPerInch(context.getResources().getDisplayMetrics().density);
        mPhysicalCoeff = FlingMath.deceleration(ppi, FlingMath.LOOK);
        mFlywheel = flywheel;
    }

    public final void setFriction(float friction) { mFlingFriction = friction; }

    public final boolean isFinished() { return mFinished; }

    public final void forceFinished(boolean finished) { mFinished = finished; }

    public final int getDuration() { return mDuration; }

    public final int getCurrX() { return mCurrX; }

    public final int getCurrY() { return mCurrY; }

    public float getCurrVelocity() { return mCurrVelocity; }

    public final int getStartX() { return mStartX; }

    public final int getStartY() { return mStartY; }

    public final int getFinalX() { return mFinalX; }

    public final int getFinalY() { return mFinalY; }

    public boolean computeScrollOffset() {
        if (mFinished) return false;
        int timePassed = (int) (SystemClock.uptimeMillis() - mStartTime);
        if (timePassed < mDuration) {
            if (mMode == SCROLL_MODE) {
                float t = timePassed * mDurationReciprocal;
                float x = mInterpolator == null ? FlingMath.viscous(t) : mInterpolator.getInterpolation(t);
                mCurrX = mStartX + Math.round(x * mDeltaX);
                mCurrY = mStartY + Math.round(x * mDeltaY);
            } else {
                float t = (float) timePassed / mDuration;
                float[] velocityCoef = new float[1];
                float distanceCoef = FlingMath.distanceCoef(t, velocityCoef);
                mCurrVelocity = velocityCoef[0] * mDistance / mDuration * 1000.0f;
                mCurrX = mStartX + Math.round(distanceCoef * (mFinalX - mStartX));
                mCurrX = Math.min(mCurrX, mMaxX);
                mCurrX = Math.max(mCurrX, mMinX);
                mCurrY = mStartY + Math.round(distanceCoef * (mFinalY - mStartY));
                mCurrY = Math.min(mCurrY, mMaxY);
                mCurrY = Math.max(mCurrY, mMinY);
                if (mCurrX == mFinalX && mCurrY == mFinalY) mFinished = true;
            }
        } else {
            mCurrX = mFinalX;
            mCurrY = mFinalY;
            mFinished = true;
        }
        return true;
    }

    public void startScroll(int startX, int startY, int dx, int dy) {
        startScroll(startX, startY, dx, dy, DEFAULT_DURATION);
    }

    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mMode = SCROLL_MODE;
        mFinished = false;
        mDuration = duration;
        mStartTime = SystemClock.uptimeMillis();
        mStartX = startX;
        mStartY = startY;
        mFinalX = startX + dx;
        mFinalY = startY + dy;
        mDeltaX = dx;
        mDeltaY = dy;
        mDurationReciprocal = duration > 0 ? 1.0f / duration : 1.0f;
        mCurrX = startX;
        mCurrY = startY;
        mCurrVelocity = 0;
    }

    public void fling(int startX, int startY, int velocityX, int velocityY, int minX, int maxX, int minY, int maxY) {
        if (mFlywheel && !mFinished) {
            float old = getCurrVelocity();
            float dx = mFinalX - mStartX;
            float dy = mFinalY - mStartY;
            float hyp = (float) Math.hypot(dx, dy);
            if (hyp > 0) {
                float oldVelocityX = dx / hyp * old;
                float oldVelocityY = dy / hyp * old;
                if (Math.signum(velocityX) == Math.signum(oldVelocityX)
                        && Math.signum(velocityY) == Math.signum(oldVelocityY)) {
                    velocityX += oldVelocityX;
                    velocityY += oldVelocityY;
                }
            }
        }
        mMode = FLING_MODE;
        mFinished = false;
        float velocity = (float) Math.hypot(velocityX, velocityY);
        mVelocity = velocity;
        mCurrVelocity = velocity;
        mDuration = velocity == 0 ? 0 : FlingMath.flingDuration(velocity, mFlingFriction, mPhysicalCoeff);
        mStartTime = SystemClock.uptimeMillis();
        mStartX = startX;
        mStartY = startY;
        mCurrX = startX;
        mCurrY = startY;
        float coeffX = velocity == 0 ? 1.0f : velocityX / velocity;
        float coeffY = velocity == 0 ? 1.0f : velocityY / velocity;
        double total = velocity == 0 ? 0 : FlingMath.flingDistance(velocity, mFlingFriction, mPhysicalCoeff);
        mDistance = (int) (total * Math.signum(velocity));
        mMinX = minX;
        mMaxX = maxX;
        mMinY = minY;
        mMaxY = maxY;
        mFinalX = startX + (int) Math.round(total * coeffX);
        mFinalX = Math.min(mFinalX, mMaxX);
        mFinalX = Math.max(mFinalX, mMinX);
        mFinalY = startY + (int) Math.round(total * coeffY);
        mFinalY = Math.min(mFinalY, mMaxY);
        mFinalY = Math.max(mFinalY, mMinY);
    }

    public void abortAnimation() {
        mCurrX = mFinalX;
        mCurrY = mFinalY;
        mFinished = true;
        mCurrVelocity = 0;
    }

    public void extendDuration(int extend) {
        int passed = timePassed();
        mDuration = passed + extend;
        mDurationReciprocal = mDuration > 0 ? 1.0f / mDuration : 1.0f;
        mFinished = false;
    }

    public int timePassed() { return (int) (SystemClock.uptimeMillis() - mStartTime); }

    public void setFinalX(int newX) {
        mFinalX = newX;
        mDeltaX = mFinalX - mStartX;
        mFinished = false;
    }

    public void setFinalY(int newY) {
        mFinalY = newY;
        mDeltaY = mFinalY - mStartY;
        mFinished = false;
    }
}
