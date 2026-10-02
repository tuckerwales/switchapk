package android.animation;

import java.util.ArrayList;

/**
 * Base of ValueAnimator and ObjectAnimator. Timing itself lives on ValueAnimator; this class owns
 * the listener lists and the start, end, cancel, pause and resume calls.
 */
public abstract class Animator implements Cloneable {
    public static final long DURATION_INFINITE = -1L;

    ArrayList<AnimatorListener> mListeners;
    ArrayList<AnimatorPauseListener> mPauseListeners;
    boolean mPaused;

    public Animator() {}

    public void start() {}

    public void cancel() {}

    public void end() {}

    public void pause() {
        if (isStarted() && !mPaused) {
            mPaused = true;
            notifyListeners(NOTIFY_PAUSE);
        }
    }

    public void resume() {
        if (mPaused) {
            mPaused = false;
            notifyListeners(NOTIFY_RESUME);
        }
    }

    public boolean isPaused() { return mPaused; }

    public abstract long getStartDelay();

    public abstract void setStartDelay(long startDelay);

    public abstract Animator setDuration(long duration);

    public abstract long getDuration();

    public long getTotalDuration() {
        long duration = getDuration();
        if (duration == DURATION_INFINITE) return DURATION_INFINITE;
        return getStartDelay() + duration;
    }

    public abstract void setInterpolator(TimeInterpolator value);

    public TimeInterpolator getInterpolator() { return null; }

    public abstract boolean isRunning();

    public boolean isStarted() { return isRunning(); }

    public void addListener(AnimatorListener listener) {
        if (listener == null) return;
        if (mListeners == null) mListeners = new ArrayList<AnimatorListener>();
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(AnimatorListener listener) {
        if (mListeners != null) mListeners.remove(listener);
    }

    public ArrayList<AnimatorListener> getListeners() { return mListeners; }

    public void addPauseListener(AnimatorPauseListener listener) {
        if (listener == null) return;
        if (mPauseListeners == null) mPauseListeners = new ArrayList<AnimatorPauseListener>();
        if (!mPauseListeners.contains(listener)) mPauseListeners.add(listener);
    }

    public void removePauseListener(AnimatorPauseListener listener) {
        if (mPauseListeners != null) mPauseListeners.remove(listener);
    }

    public void removeAllListeners() {
        if (mListeners != null) mListeners.clear();
    }

    public void setupStartValues() {}

    public void setupEndValues() {}

    public void setTarget(Object target) {}

    @Override
    public Animator clone() {
        try {
            Animator anim = (Animator) super.clone();
            if (mListeners != null) anim.mListeners = new ArrayList<AnimatorListener>(mListeners);
            if (mPauseListeners != null) {
                anim.mPauseListeners = new ArrayList<AnimatorPauseListener>(mPauseListeners);
            }
            anim.mPaused = false;
            return anim;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    static final int NOTIFY_START = 0;
    static final int NOTIFY_END = 1;
    static final int NOTIFY_CANCEL = 2;
    static final int NOTIFY_REPEAT = 3;
    static final int NOTIFY_PAUSE = 4;
    static final int NOTIFY_RESUME = 5;

    void notifyListeners(int kind) { notifyListeners(kind, false); }

    void notifyListeners(int kind, boolean reverse) {
        ArrayList<?> list = kind == NOTIFY_PAUSE || kind == NOTIFY_RESUME ? mPauseListeners : mListeners;
        if (list == null || list.isEmpty()) return;
        ArrayList<?> tmp = new ArrayList<Object>(list);
        for (int i = 0; i < tmp.size(); i++) {
            Object listener = tmp.get(i);
            switch (kind) {
                case NOTIFY_START:
                    ((AnimatorListener) listener).onAnimationStart(this, reverse);
                    break;
                case NOTIFY_END:
                    ((AnimatorListener) listener).onAnimationEnd(this, reverse);
                    break;
                case NOTIFY_CANCEL:
                    ((AnimatorListener) listener).onAnimationCancel(this);
                    break;
                case NOTIFY_REPEAT:
                    ((AnimatorListener) listener).onAnimationRepeat(this);
                    break;
                case NOTIFY_PAUSE:
                    ((AnimatorPauseListener) listener).onAnimationPause(this);
                    break;
                case NOTIFY_RESUME:
                    ((AnimatorPauseListener) listener).onAnimationResume(this);
                    break;
                default:
                    break;
            }
        }
    }

    public interface AnimatorListener {
        default void onAnimationStart(Animator animation, boolean isReverse) { onAnimationStart(animation); }

        default void onAnimationEnd(Animator animation, boolean isReverse) { onAnimationEnd(animation); }

        void onAnimationStart(Animator animation);

        void onAnimationEnd(Animator animation);

        void onAnimationCancel(Animator animation);

        void onAnimationRepeat(Animator animation);
    }

    public interface AnimatorPauseListener {
        void onAnimationPause(Animator animation);

        void onAnimationResume(Animator animation);
    }
}
