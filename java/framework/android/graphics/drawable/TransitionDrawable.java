package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.SystemClock;

public class TransitionDrawable extends LayerDrawable implements Drawable.Callback {
    private static final int TRANSITION_STARTING = 0;
    private static final int TRANSITION_RUNNING = 1;
    private static final int TRANSITION_NONE = 2;

    private int mTransitionState = TRANSITION_NONE;
    private boolean mReverse;
    private long mStartTimeMillis;
    private int mFrom;
    private int mTo;
    private int mDuration;
    private int mOriginalDuration;
    private int mAlpha = 0;
    private boolean mCrossFade;

    public TransitionDrawable(Drawable[] layers) { super(layers); }

    TransitionDrawable() { super(); }

    public void startTransition(int durationMillis) {
        mFrom = 0;
        mTo = 255;
        mAlpha = 0;
        mDuration = mOriginalDuration = durationMillis;
        mReverse = false;
        mTransitionState = TRANSITION_STARTING;
        invalidateSelf();
    }

    public void showSecondLayer() {
        mAlpha = 255;
        mReverse = false;
        mTransitionState = TRANSITION_NONE;
        invalidateSelf();
    }

    public void resetTransition() {
        mAlpha = 0;
        mTransitionState = TRANSITION_NONE;
        invalidateSelf();
    }

    public void reverseTransition(int duration) {
        final long time = SystemClock.uptimeMillis();
        if (time - mStartTimeMillis > mDuration) {
            if (mTo == 0) {
                mFrom = 0;
                mTo = 255;
                mAlpha = 0;
                mReverse = false;
            } else {
                mFrom = 255;
                mTo = 0;
                mAlpha = 255;
                mReverse = true;
            }
            mDuration = mOriginalDuration = duration;
            mTransitionState = TRANSITION_STARTING;
            invalidateSelf();
            return;
        }
        mReverse = !mReverse;
        mFrom = mAlpha;
        mTo = mReverse ? 0 : 255;
        mDuration = (int) (mReverse ? time - mStartTimeMillis : mOriginalDuration - (time - mStartTimeMillis));
        mTransitionState = TRANSITION_STARTING;
    }

    @Override
    public void draw(Canvas canvas) {
        boolean done = true;
        switch (mTransitionState) {
            case TRANSITION_STARTING:
                mStartTimeMillis = SystemClock.uptimeMillis();
                done = false;
                mTransitionState = TRANSITION_RUNNING;
                break;
            case TRANSITION_RUNNING:
                if (mStartTimeMillis >= 0) {
                    float normalized = (float) (SystemClock.uptimeMillis() - mStartTimeMillis) / Math.max(1, mDuration);
                    done = normalized >= 1.0f;
                    normalized = Math.min(normalized, 1.0f);
                    mAlpha = (int) (mFrom + (mTo - mFrom) * normalized);
                }
                break;
        }
        final int alpha = mAlpha;
        final boolean crossFade = mCrossFade;
        Drawable d = getNumberOfLayers() > 0 ? getDrawable(0) : null;
        if (d != null) {
            if (crossFade) d.setAlpha(255 - alpha);
            d.draw(canvas);
            if (crossFade) d.setAlpha(0xFF);
        }
        if (alpha > 0 && getNumberOfLayers() > 1) {
            d = getDrawable(1);
            if (d != null) {
                d.setAlpha(alpha);
                d.draw(canvas);
                d.setAlpha(0xFF);
            }
        }
        if (!done) invalidateSelf();
    }

    public void setCrossFadeEnabled(boolean enabled) { mCrossFade = enabled; }
    public boolean isCrossFadeEnabled() { return mCrossFade; }
}
