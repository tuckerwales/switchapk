package android.animation;

import android.os.Looper;
import android.view.Choreographer;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import java.util.ArrayList;

/**
 * Drives a fraction from 0 to 1 on Choreographer frames and evaluates its PropertyValuesHolders.
 * A duration scale of 0 (animations disabled) jumps to the end value inside start().
 */
public class ValueAnimator extends Animator implements Choreographer.FrameCallback {
    public static final int INFINITE = -1;
    public static final int RESTART = 1;
    public static final int REVERSE = 2;

    private static final TimeInterpolator sDefaultInterpolator = new AccelerateDecelerateInterpolator();
    private static float sDurationScale = 1f;
    private static long sFrameDelay = 10;
    private static final ArrayList<DurationScaleChangeListener> sScaleListeners =
            new ArrayList<DurationScaleChangeListener>();
    private static final ThreadLocal<AnimationHandler> sHandlers = new ThreadLocal<AnimationHandler>() {
        @Override
        protected AnimationHandler initialValue() { return new AnimationHandler(); }
    };

    PropertyValuesHolder[] mValues;
    long mDuration = 300;
    long mStartDelay;
    int mRepeatCount;
    int mRepeatMode = RESTART;
    TimeInterpolator mInterpolator;
    long mStartTime = -1;
    boolean mRunning;
    boolean mStarted;
    boolean mStartNotified;
    boolean mReversing;
    boolean mInitialized;
    float mCurrentFraction;
    int mLastCycle;
    long mPauseTime;
    ArrayList<AnimatorUpdateListener> mUpdateListeners;

    public ValueAnimator() {}

    public static float getDurationScale() { return sDurationScale; }

    /** framework-internal. Apps read the scale through getDurationScale; nothing in the framework changes it yet. */
    public static void setDurationScale(float scale) {
        if (scale < 0) scale = 0;
        if (scale == sDurationScale) return;
        sDurationScale = scale;
        for (int i = 0; i < sScaleListeners.size(); i++) sScaleListeners.get(i).onChanged(scale);
    }

    public static boolean registerDurationScaleChangeListener(DurationScaleChangeListener listener) {
        if (listener == null || sScaleListeners.contains(listener)) return false;
        sScaleListeners.add(listener);
        return true;
    }

    public static boolean unregisterDurationScaleChangeListener(DurationScaleChangeListener listener) {
        return sScaleListeners.remove(listener);
    }

    public static boolean areAnimatorsEnabled() { return sDurationScale != 0f; }

    public static ValueAnimator ofInt(int... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setIntValues(values);
        return anim;
    }

    public static ValueAnimator ofArgb(int... values) {
        ValueAnimator anim = ofInt(values);
        anim.setEvaluator(new ArgbEvaluator());
        return anim;
    }

    public static ValueAnimator ofFloat(float... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setFloatValues(values);
        return anim;
    }

    public static ValueAnimator ofPropertyValuesHolder(PropertyValuesHolder... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setValues(values);
        return anim;
    }

    public static ValueAnimator ofObject(TypeEvaluator evaluator, Object... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    public void setIntValues(int... values) {
        if (mValues == null || mValues.length == 0) setValues(PropertyValuesHolder.ofInt("", values));
        else mValues[0].setIntValues(values);
        mInitialized = false;
    }

    public void setFloatValues(float... values) {
        if (mValues == null || mValues.length == 0) setValues(PropertyValuesHolder.ofFloat("", values));
        else mValues[0].setFloatValues(values);
        mInitialized = false;
    }

    public void setObjectValues(Object... values) {
        if (mValues == null || mValues.length == 0) setValues(PropertyValuesHolder.ofObject("", null, values));
        else mValues[0].setObjectValues(values);
        mInitialized = false;
    }

    public void setValues(PropertyValuesHolder... values) {
        mValues = values == null ? null : values.clone();
        mInitialized = false;
    }

    public PropertyValuesHolder[] getValues() { return mValues; }

    @Override
    public ValueAnimator setDuration(long duration) {
        if (duration < 0) throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        mDuration = duration;
        return this;
    }

    @Override
    public long getDuration() { return mDuration; }

    @Override
    public long getTotalDuration() {
        if (mRepeatCount == INFINITE) return DURATION_INFINITE;
        if (mDuration == DURATION_INFINITE) return DURATION_INFINITE;
        return mStartDelay + mDuration * ((long) mRepeatCount + 1);
    }

    public void setCurrentPlayTime(long playTime) {
        float fraction = mDuration > 0 ? (float) playTime / (float) mDuration : 1f;
        setCurrentFraction(fraction);
    }

    public void setCurrentFraction(float fraction) {
        initAnimation();
        if (fraction < 0f) fraction = 0f;
        float overall = fraction;
        if (mRepeatCount != INFINITE && overall > mRepeatCount + 1) overall = mRepeatCount + 1;
        animateValue(iterationFraction(overall));
        long scaled = scaledDuration();
        mStartTime = AnimationUtils.currentAnimationTimeMillis() - (long) (scaled * overall);
    }

    public long getCurrentPlayTime() {
        if (!mStarted || mStartTime < 0) return 0;
        if (mPaused) return Math.max(0, mPauseTime - mStartTime);
        return Math.max(0, AnimationUtils.currentAnimationTimeMillis() - mStartTime);
    }

    @Override
    public long getStartDelay() { return mStartDelay; }

    @Override
    public void setStartDelay(long startDelay) {
        if (startDelay < 0) throw new IllegalArgumentException("Animators cannot have negative start delay");
        mStartDelay = startDelay;
    }

    public static long getFrameDelay() { return sFrameDelay; }

    public static void setFrameDelay(long frameDelay) { sFrameDelay = frameDelay; }

    public Object getAnimatedValue() {
        return mValues != null && mValues.length > 0 ? mValues[0].getAnimatedValue() : null;
    }

    public Object getAnimatedValue(String propertyName) {
        if (mValues == null) return null;
        for (int i = 0; i < mValues.length; i++) {
            if (propertyName.equals(mValues[i].getPropertyName())) return mValues[i].getAnimatedValue();
        }
        return null;
    }

    public void setRepeatCount(int value) { mRepeatCount = value; }

    public int getRepeatCount() { return mRepeatCount; }

    public void setRepeatMode(int value) { mRepeatMode = value; }

    public int getRepeatMode() { return mRepeatMode; }

    public void addUpdateListener(AnimatorUpdateListener listener) {
        if (listener == null) return;
        if (mUpdateListeners == null) mUpdateListeners = new ArrayList<AnimatorUpdateListener>();
        if (!mUpdateListeners.contains(listener)) mUpdateListeners.add(listener);
    }

    public void removeAllUpdateListeners() {
        if (mUpdateListeners != null) mUpdateListeners.clear();
    }

    public void removeUpdateListener(AnimatorUpdateListener listener) {
        if (mUpdateListeners != null) mUpdateListeners.remove(listener);
    }

    @Override
    public void setInterpolator(TimeInterpolator value) {
        mInterpolator = value != null ? value : sDefaultInterpolator;
    }

    @Override
    public TimeInterpolator getInterpolator() {
        return mInterpolator != null ? mInterpolator : sDefaultInterpolator;
    }

    public void setEvaluator(TypeEvaluator value) {
        if (mValues != null) for (int i = 0; i < mValues.length; i++) mValues[i].setEvaluator(value);
    }

    @Override
    public void start() { start(false); }

    private void start(boolean reversing) {
        if (Looper.myLooper() == null) throw new IllegalStateException("Animators may only be run on Looper threads");
        mReversing = reversing;
        if (sDurationScale == 0f) {
            initAnimation();
            mStarted = true;
            mRunning = true;
            notifyListeners(NOTIFY_START, mReversing);
            animateValue(mReversing ? 0f : 1f);
            mStarted = false;
            mRunning = false;
            notifyListeners(NOTIFY_END, mReversing);
            return;
        }
        mStarted = true;
        mRunning = false;
        mStartNotified = false;
        mPaused = false;
        mLastCycle = 0;
        long now = AnimationUtils.currentAnimationTimeMillis();
        mStartTime = now + scaledDelay();
        sHandlers.get().add(this);
        if (mStartDelay == 0) {
            mRunning = true;
            mStartNotified = true;
            initAnimation();
            notifyListeners(NOTIFY_START, mReversing);
            animateValue(mReversing ? 1f : 0f);
        }
    }

    @Override
    public void cancel() {
        if (!mStarted) return;
        notifyListeners(NOTIFY_CANCEL);
        finish(true);
    }

    @Override
    public void end() {
        if (!mStarted) {
            initAnimation();
            mStarted = true;
            notifyListeners(NOTIFY_START, mReversing);
        }
        animateValue(mReversing ? 0f : 1f);
        finish(true);
    }

    @Override
    public void resume() {
        if (!mPaused || !mStarted) return;
        mPaused = false;
        long delta = AnimationUtils.currentAnimationTimeMillis() - mPauseTime;
        mStartTime += delta;
        sHandlers.get().add(this);
        notifyListeners(NOTIFY_RESUME);
    }

    @Override
    public void pause() {
        if (!mStarted || mPaused) return;
        mPaused = true;
        mPauseTime = AnimationUtils.currentAnimationTimeMillis();
        sHandlers.get().remove(this);
        notifyListeners(NOTIFY_PAUSE);
    }

    @Override
    public boolean isRunning() { return mRunning; }

    @Override
    public boolean isStarted() { return mStarted; }

    public void reverse() {
        mReversing = !mReversing;
        if (!mStarted) {
            start(mReversing);
            return;
        }
        long scaled = scaledDuration();
        long now = AnimationUtils.currentAnimationTimeMillis();
        long played = now - mStartTime;
        if (played < 0) played = 0;
        if (played > scaled) played = scaled;
        mStartTime = now - (scaled - played);
    }

    public float getAnimatedFraction() { return mCurrentFraction; }

    void initAnimation() {
        if (mInitialized) return;
        if (mInterpolator == null) mInterpolator = sDefaultInterpolator;
        if (mValues != null) for (int i = 0; i < mValues.length; i++) mValues[i].init();
        prepareHolders();
        mInitialized = true;
    }

    /** ObjectAnimator resolves setters and missing start values here. */
    void prepareHolders() {}

    /** ObjectAnimator writes the current holder values onto its target here. */
    void onAnimatedValue() {}

    void doAnimationFrame(long frameTime) {
        if (!mStarted || mPaused) return;
        if (!mRunning) {
            if (frameTime < mStartTime) return;
            mRunning = true;
            mStartNotified = true;
            initAnimation();
            notifyListeners(NOTIFY_START, mReversing);
        }
        long scaled = scaledDuration();
        float overall = scaled <= 0 ? (mRepeatCount == INFINITE ? 0f : mRepeatCount + 1f)
                : (float) (frameTime - mStartTime) / (float) scaled;
        if (overall < 0f) overall = 0f;
        boolean done = mRepeatCount != INFINITE && overall >= mRepeatCount + 1;
        int cycle = (int) overall;
        if (done) cycle = mRepeatCount;
        if (cycle > mLastCycle && !done) {
            for (int c = mLastCycle; c < cycle; c++) notifyListeners(NOTIFY_REPEAT);
        }
        mLastCycle = cycle;
        animateValue(iterationFraction(done ? mRepeatCount + 1f : overall));
        if (done || scaled <= 0) finish(true);
    }

    private float iterationFraction(float overall) {
        if (mRepeatCount != INFINITE && overall >= mRepeatCount + 1) {
            float end = (mRepeatMode == REVERSE && (mRepeatCount % 2) == 1) ? 0f : 1f;
            return mReversing ? 1f - end : end;
        }
        int cycle = (int) overall;
        float frac = overall - cycle;
        if (frac == 0f && overall > 0f) frac = 1f;
        if (mRepeatMode == REVERSE && (cycle % 2) == 1) frac = 1f - frac;
        if (mReversing) frac = 1f - frac;
        return frac;
    }

    void animateValue(float fraction) {
        if (mInterpolator == null) mInterpolator = sDefaultInterpolator;
        float interpolated = mInterpolator.getInterpolation(fraction);
        mCurrentFraction = interpolated;
        if (mValues != null) {
            for (int i = 0; i < mValues.length; i++) mValues[i].calculateValue(interpolated);
        }
        onAnimatedValue();
        if (mUpdateListeners != null) {
            ArrayList<AnimatorUpdateListener> tmp = new ArrayList<AnimatorUpdateListener>(mUpdateListeners);
            for (int i = 0; i < tmp.size(); i++) tmp.get(i).onAnimationUpdate(this);
        }
    }

    private void finish(boolean notify) {
        if (!mStarted && !notify) return;
        mStarted = false;
        mRunning = false;
        mPaused = false;
        AnimationHandler handler = sHandlers.get();
        if (handler != null) handler.remove(this);
        if (notify) notifyListeners(NOTIFY_END, mReversing);
        onAnimationFinished();
    }

    /** ObjectAnimator drops itself from the auto-cancel list here. */
    void onAnimationFinished() {}

    private long scaledDuration() { return (long) (mDuration * sDurationScale); }

    private long scaledDelay() { return (long) (mStartDelay * sDurationScale); }

    @Override
    public void doFrame(long frameTimeNanos) { doAnimationFrame(frameTimeNanos / 1000000L); }

    @Override
    public ValueAnimator clone() {
        ValueAnimator anim = (ValueAnimator) super.clone();
        if (mValues != null) {
            anim.mValues = new PropertyValuesHolder[mValues.length];
            for (int i = 0; i < mValues.length; i++) anim.mValues[i] = mValues[i].clone();
        }
        anim.mUpdateListeners = null;
        anim.mRunning = false;
        anim.mStarted = false;
        anim.mStartNotified = false;
        anim.mInitialized = false;
        anim.mStartTime = -1;
        anim.mCurrentFraction = 0;
        return anim;
    }

    @Override
    public String toString() {
        String s = "ValueAnimator@" + Integer.toHexString(hashCode());
        if (mValues != null) for (int i = 0; i < mValues.length; i++) s += "\n    " + mValues[i].toString();
        return s;
    }

    public interface AnimatorUpdateListener {
        void onAnimationUpdate(ValueAnimator animation);
    }

    public interface DurationScaleChangeListener {
        void onChanged(float scale);
    }

    private static final class AnimationHandler {
        private final ArrayList<ValueAnimator> mAnimations = new ArrayList<ValueAnimator>();
        private boolean mScheduled;
        private final Choreographer.FrameCallback mCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                mScheduled = false;
                long time = frameTimeNanos / 1000000L;
                ValueAnimator[] copy = mAnimations.toArray(new ValueAnimator[mAnimations.size()]);
                for (int i = 0; i < copy.length; i++) {
                    if (mAnimations.contains(copy[i])) copy[i].doAnimationFrame(time);
                }
                if (!mAnimations.isEmpty()) schedule();
            }
        };

        void add(ValueAnimator animator) {
            if (!mAnimations.contains(animator)) mAnimations.add(animator);
            schedule();
        }

        void remove(ValueAnimator animator) { mAnimations.remove(animator); }

        private void schedule() {
            if (mScheduled || mAnimations.isEmpty()) return;
            mScheduled = true;
            Choreographer.getInstance().postFrameCallback(mCallback);
        }
    }
}
