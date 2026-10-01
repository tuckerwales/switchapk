package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/**
 * Port of AOSP ViewFlipper: a ViewAnimator that shows the next child every flipInterval ms while it
 * is started (or autoStart), attached and visible. There is no screen-off broadcast here, so the
 * user is always considered present.
 */
public class ViewFlipper extends ViewAnimator {
    private static final int DEFAULT_INTERVAL = 3000;

    private int mFlipInterval = DEFAULT_INTERVAL;
    private boolean mAutoStart = false;
    private boolean mRunning = false;
    private boolean mStarted = false;
    private boolean mVisible = false;
    private boolean mUserPresent = true;

    public ViewFlipper(Context context) { super(context); }

    public ViewFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] {android.R.attr.flipInterval, android.R.attr.autoStart});
        mFlipInterval = a.getInt(0, DEFAULT_INTERVAL);
        mAutoStart = a.getBoolean(1, false);
        a.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAutoStart) {
            // Automatically start when requested.
            startFlipping();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning(false);
    }

    public void setFlipInterval(int milliseconds) { mFlipInterval = milliseconds; }

    public int getFlipInterval() { return mFlipInterval; }

    public void startFlipping() {
        mStarted = true;
        updateRunning();
    }

    public void stopFlipping() {
        mStarted = false;
        updateRunning();
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ViewFlipper.class.getName(); }

    private void updateRunning() { updateRunning(true); }

    /** Starts or stops the flip timer to match the visible, started and user-present state. */
    private void updateRunning(boolean flipNow) {
        boolean running = mVisible && mStarted && mUserPresent;
        if (running != mRunning) {
            if (running) {
                showOnly(mWhichChild, flipNow);
                postDelayed(mFlipRunnable, mFlipInterval);
            } else {
                removeCallbacks(mFlipRunnable);
            }
            mRunning = running;
        }
    }

    public boolean isFlipping() { return mStarted; }

    public void setAutoStart(boolean autoStart) { mAutoStart = autoStart; }

    public boolean isAutoStart() { return mAutoStart; }

    private final Runnable mFlipRunnable = new Runnable() {
        public void run() {
            if (mRunning) {
                showNext();
                postDelayed(mFlipRunnable, mFlipInterval);
            }
        }
    };
}
