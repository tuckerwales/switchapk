package android.animation;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Fades a child in when it is added and out when it is removed. Changing the position of the
 * siblings is not animated: the change types are remembered and reported, and they do not run.
 */
public class LayoutTransition {
    public static final int CHANGE_APPEARING = 0;
    public static final int CHANGE_DISAPPEARING = 1;
    public static final int APPEARING = 2;
    public static final int DISAPPEARING = 3;
    public static final int CHANGING = 4;

    private static final int TYPE_COUNT = 5;
    private static final int FLAG_CHANGE_APPEARING = 1 << CHANGE_APPEARING;
    private static final int FLAG_CHANGE_DISAPPEARING = 1 << CHANGE_DISAPPEARING;
    private static final int FLAG_APPEARING = 1 << APPEARING;
    private static final int FLAG_DISAPPEARING = 1 << DISAPPEARING;
    private static final int FLAG_CHANGING = 1 << CHANGING;
    private static final int DEFAULT_ENABLE = FLAG_CHANGE_APPEARING | FLAG_CHANGE_DISAPPEARING | FLAG_APPEARING
            | FLAG_DISAPPEARING;

    private int mEnabled = DEFAULT_ENABLE;
    private boolean mAnimateParentHierarchy = true;
    private final long[] mDurations = new long[] {300, 300, 300, 300, 300};
    private final long[] mDelays = new long[TYPE_COUNT];
    private final long[] mStaggers = new long[TYPE_COUNT];
    private final TimeInterpolator[] mInterps = new TimeInterpolator[TYPE_COUNT];
    private final Animator[] mAnimators = new Animator[TYPE_COUNT];
    private final HashMap<View, Animator> mPending = new HashMap<View, Animator>();
    private ArrayList<TransitionListener> mListeners;

    public LayoutTransition() {
        mInterps[APPEARING] = new AccelerateDecelerateInterpolator();
        mInterps[DISAPPEARING] = new DecelerateInterpolator();
        mInterps[CHANGE_APPEARING] = new DecelerateInterpolator();
        mInterps[CHANGE_DISAPPEARING] = new DecelerateInterpolator();
        mInterps[CHANGING] = new DecelerateInterpolator();
        mAnimators[APPEARING] = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
        mAnimators[DISAPPEARING] = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
        mAnimators[CHANGE_APPEARING] = defaultChange();
        mAnimators[CHANGE_DISAPPEARING] = defaultChange();
        mAnimators[CHANGING] = defaultChange();
    }

    private static Animator defaultChange() {
        // Sibling layout moves are not applied. The animator exists so getAnimator matches AOSP.
        return ObjectAnimator.ofFloat(null, "left", 0f, 1f);
    }

    public void setDuration(long duration) {
        for (int i = 0; i < TYPE_COUNT; i++) mDurations[i] = duration;
    }

    public void enableTransitionType(int transitionType) {
        int flag = flagOf(transitionType);
        if (flag != 0) mEnabled |= flag;
    }

    public void disableTransitionType(int transitionType) {
        int flag = flagOf(transitionType);
        if (flag != 0) mEnabled &= ~flag;
    }

    public boolean isTransitionTypeEnabled(int transitionType) {
        int flag = flagOf(transitionType);
        return flag != 0 && (mEnabled & flag) != 0;
    }

    public void setStartDelay(int transitionType, long delay) {
        if (valid(transitionType)) mDelays[transitionType] = delay;
    }

    public long getStartDelay(int transitionType) { return valid(transitionType) ? mDelays[transitionType] : 0; }

    public void setDuration(int transitionType, long duration) {
        if (valid(transitionType)) mDurations[transitionType] = duration;
    }

    public long getDuration(int transitionType) { return valid(transitionType) ? mDurations[transitionType] : 0; }

    public void setStagger(int transitionType, long duration) {
        if (valid(transitionType)) mStaggers[transitionType] = duration;
    }

    public long getStagger(int transitionType) { return valid(transitionType) ? mStaggers[transitionType] : 0; }

    public void setInterpolator(int transitionType, TimeInterpolator interpolator) {
        if (valid(transitionType)) mInterps[transitionType] = interpolator;
    }

    public TimeInterpolator getInterpolator(int transitionType) {
        return valid(transitionType) ? mInterps[transitionType] : null;
    }

    public void setAnimator(int transitionType, Animator animator) {
        if (valid(transitionType)) mAnimators[transitionType] = animator;
    }

    public Animator getAnimator(int transitionType) {
        return valid(transitionType) ? mAnimators[transitionType] : null;
    }

    public void setAnimateParentHierarchy(boolean animateParentHierarchy) {
        mAnimateParentHierarchy = animateParentHierarchy;
    }

    /** Changing layout of the other children is not running. Appearing and disappearing still are. */
    public boolean isChangingLayout() { return false; }

    public boolean isRunning() { return !mPending.isEmpty(); }

    public void addChild(ViewGroup parent, View child) { run(parent, child, APPEARING, false); }

    public void showChild(ViewGroup parent, View child) { showChild(parent, child, View.GONE); }

    /** {@code oldVisibility} is the child's previous visibility, not a transition type. */
    public void showChild(ViewGroup parent, View child, int oldVisibility) {
        if (oldVisibility == View.GONE || oldVisibility == View.INVISIBLE) {
            run(parent, child, APPEARING, false);
        }
    }

    public void removeChild(ViewGroup parent, View child) { hideChild(parent, child, View.GONE); }

    public void hideChild(ViewGroup parent, View child) { hideChild(parent, child, View.GONE); }

    public void hideChild(ViewGroup parent, View child, int newVisibility) {
        if (newVisibility == View.GONE || newVisibility == View.INVISIBLE) {
            run(parent, child, DISAPPEARING, true);
        }
    }

    public void addTransitionListener(TransitionListener listener) {
        if (listener == null) return;
        if (mListeners == null) mListeners = new ArrayList<TransitionListener>();
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeTransitionListener(TransitionListener listener) {
        if (mListeners != null) mListeners.remove(listener);
    }

    public List<TransitionListener> getTransitionListeners() { return mListeners; }

    private void run(final ViewGroup parent, final View child, final int type, final boolean disappearing) {
        Animator previous = mPending.remove(child);
        if (previous != null) previous.cancel();
        if (!isTransitionTypeEnabled(type) || mAnimators[type] == null || parent == null || child == null) {
            if (disappearing && parent != null) parent.endViewTransition(child);
            return;
        }
        final Animator anim = mAnimators[type].clone();
        anim.setTarget(child);
        anim.setDuration(mDurations[type]);
        if (mInterps[type] != null) anim.setInterpolator(mInterps[type]);
        if (mDelays[type] != 0) anim.setStartDelay(mDelays[type]);
        if (anim instanceof ValueAnimator) {
            ((ValueAnimator) anim).addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) { parent.invalidate(); }
            });
        }
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) { notifyStart(parent, child, type); }

            @Override
            public void onAnimationEnd(Animator animation) {
                // A cancel of a replaced animator must not detach the child. The
                // replacement is already in mPending, or this one still is.
                if (mPending.get(child) != animation) return;
                mPending.remove(child);
                if (disappearing) parent.endViewTransition(child);
                notifyEnd(parent, child, type);
            }
        });
        if (disappearing) parent.startViewTransition(child);
        mPending.put(child, anim);
        anim.start();
    }

    private void notifyStart(ViewGroup parent, View child, int type) {
        if (mListeners == null) return;
        ArrayList<TransitionListener> copy = new ArrayList<TransitionListener>(mListeners);
        for (int i = 0; i < copy.size(); i++) copy.get(i).startTransition(this, parent, child, type);
    }

    private void notifyEnd(ViewGroup parent, View child, int type) {
        if (mListeners == null) return;
        ArrayList<TransitionListener> copy = new ArrayList<TransitionListener>(mListeners);
        for (int i = 0; i < copy.size(); i++) copy.get(i).endTransition(this, parent, child, type);
    }

    private static boolean valid(int type) { return type >= 0 && type < TYPE_COUNT; }

    private static int flagOf(int type) {
        switch (type) {
            case CHANGE_APPEARING: return FLAG_CHANGE_APPEARING;
            case CHANGE_DISAPPEARING: return FLAG_CHANGE_DISAPPEARING;
            case APPEARING: return FLAG_APPEARING;
            case DISAPPEARING: return FLAG_DISAPPEARING;
            case CHANGING: return FLAG_CHANGING;
            default: return 0;
        }
    }

    public interface TransitionListener {
        void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType);

        void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType);
    }
}
