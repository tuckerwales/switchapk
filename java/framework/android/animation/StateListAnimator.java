package android.animation;

import android.util.StateSet;
import android.view.View;
import java.util.ArrayList;

/**
 * Runs the first animator whose state spec matches the view's drawable state.
 * The view is held strongly: this VM clears weak references on every GC, which
 * would drop the target mid-animation. {@code setTarget} and {@code setState}
 * are hidden in the SDK jar; View calls them.
 */
public class StateListAnimator implements Cloneable {
    private ArrayList<Tuple> mTuples = new ArrayList<Tuple>();
    private AnimatorListenerAdapter mListener;
    private Tuple mLastMatch;
    private Animator mRunningAnimator;
    /** Strong reference. AOSP uses a weak one; this VM would clear that every GC. */
    private View mView;

    public StateListAnimator() { initListener(); }

    private void initListener() {
        mListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mRunningAnimator == animation) mRunningAnimator = null;
            }
        };
    }

    public void addState(int[] specs, Animator animator) {
        if (animator == null) throw new NullPointerException("Animator cannot be null");
        if (specs == null) throw new NullPointerException("specs cannot be null");
        animator.addListener(mListener);
        animator.setTarget(mView);
        mTuples.add(new Tuple(specs.clone(), animator));
        mLastMatch = null;
    }

    /** Hidden in the SDK. View calls this when the drawable state changes. */
    public void setState(int[] state) {
        Tuple match = null;
        for (int i = 0; i < mTuples.size(); i++) {
            Tuple tuple = mTuples.get(i);
            if (StateSet.stateSetMatches(tuple.mSpecs, state)) {
                match = tuple;
                break;
            }
        }
        if (match == mLastMatch) return;
        if (mRunningAnimator != null) {
            mRunningAnimator.cancel();
            mRunningAnimator = null;
        }
        mLastMatch = match;
        if (match == null || mView == null) return;
        match.mAnimator.setTarget(mView);
        mRunningAnimator = match.mAnimator;
        mRunningAnimator.start();
    }

    /** Hidden in the SDK. */
    public void setTarget(View view) {
        if (mView == view) return;
        if (mRunningAnimator != null) {
            mRunningAnimator.cancel();
            mRunningAnimator = null;
        }
        mView = view;
        mLastMatch = null;
        for (int i = 0; i < mTuples.size(); i++) mTuples.get(i).mAnimator.setTarget(view);
    }

    /** Hidden in the SDK. */
    public View getTarget() { return mView; }

    public void jumpToCurrentState() {
        if (mRunningAnimator != null) mRunningAnimator.end();
    }

    @Override
    public StateListAnimator clone() {
        try {
            StateListAnimator out = (StateListAnimator) super.clone();
            out.mTuples = new ArrayList<Tuple>();
            out.mLastMatch = null;
            out.mRunningAnimator = null;
            out.mView = null;
            out.initListener();
            for (int i = 0; i < mTuples.size(); i++) {
                Tuple tuple = mTuples.get(i);
                Animator anim = tuple.mAnimator.clone();
                anim.removeListener(mListener);
                anim.addListener(out.mListener);
                anim.setTarget(null);
                out.mTuples.add(new Tuple(tuple.mSpecs.clone(), anim));
            }
            return out;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Tuple {
        final int[] mSpecs;
        final Animator mAnimator;

        Tuple(int[] specs, Animator animator) {
            mSpecs = specs;
            mAnimator = animator;
        }
    }
}
