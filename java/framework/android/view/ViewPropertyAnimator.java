package android.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import java.util.ArrayList;

/**
 * Batches property changes posted in one frame onto a single ValueAnimator. The from value and
 * the delta are captured when the property method is called. The animator is started on the next
 * animation frame, or immediately from start().
 */
public class ViewPropertyAnimator {
    private static final long UNSET = -1;

    private final View mView;
    private final ArrayList<Pending> mPending = new ArrayList<Pending>();
    private long mDuration = UNSET;
    private long mStartDelay;
    private boolean mStartDelaySet;
    private TimeInterpolator mInterpolator;
    private boolean mInterpolatorSet;
    private Animator.AnimatorListener mListener;
    private ValueAnimator.AnimatorUpdateListener mUpdateListener;
    private Runnable mStarter;
    private ValueAnimator mAnimator;
    private Runnable mStartAction;
    private Runnable mEndAction;
    private boolean mWithLayer;

    ViewPropertyAnimator(View view) { mView = view; }

    public ViewPropertyAnimator setDuration(long duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("Animators cannot have negative duration: " + duration);
        }
        mDuration = duration;
        if (mAnimator != null) mAnimator.setDuration(duration);
        return this;
    }

    public long getDuration() { return mDuration >= 0 ? mDuration : 300; }

    public long getStartDelay() { return mStartDelay; }

    public ViewPropertyAnimator setStartDelay(long startDelay) {
        if (startDelay < 0) throw new IllegalArgumentException("Animators cannot have negative start delay");
        mStartDelay = startDelay;
        mStartDelaySet = true;
        if (mAnimator != null) mAnimator.setStartDelay(startDelay);
        return this;
    }

    public ViewPropertyAnimator setInterpolator(TimeInterpolator interpolator) {
        mInterpolator = interpolator;
        mInterpolatorSet = true;
        if (mAnimator != null) mAnimator.setInterpolator(interpolator);
        return this;
    }

    public TimeInterpolator getInterpolator() { return mInterpolator; }

    public ViewPropertyAnimator setListener(Animator.AnimatorListener listener) {
        mListener = listener;
        return this;
    }

    public ViewPropertyAnimator setUpdateListener(ValueAnimator.AnimatorUpdateListener listener) {
        mUpdateListener = listener;
        return this;
    }

    public void start() {
        if (mStarter != null) {
            mView.removeCallbacks(mStarter);
            Runnable starter = mStarter;
            mStarter = null;
            starter.run();
        }
    }

    public void cancel() {
        if (mStarter != null) {
            mView.removeCallbacks(mStarter);
            mStarter = null;
        }
        mPending.clear();
        if (mAnimator != null) mAnimator.cancel();
    }

    public ViewPropertyAnimator x(float value) { return queue("x", mView.getX(), value - mView.getX()); }

    public ViewPropertyAnimator xBy(float value) { return queue("x", mView.getX(), value); }

    public ViewPropertyAnimator y(float value) { return queue("y", mView.getY(), value - mView.getY()); }

    public ViewPropertyAnimator yBy(float value) { return queue("y", mView.getY(), value); }

    public ViewPropertyAnimator z(float value) { return queue("z", mView.getZ(), value - mView.getZ()); }

    public ViewPropertyAnimator zBy(float value) { return queue("z", mView.getZ(), value); }

    public ViewPropertyAnimator rotation(float value) {
        return queue("rotation", mView.getRotation(), value - mView.getRotation());
    }

    public ViewPropertyAnimator rotationBy(float value) { return queue("rotation", mView.getRotation(), value); }

    public ViewPropertyAnimator rotationX(float value) {
        return queue("rotationX", mView.getRotationX(), value - mView.getRotationX());
    }

    public ViewPropertyAnimator rotationXBy(float value) { return queue("rotationX", mView.getRotationX(), value); }

    public ViewPropertyAnimator rotationY(float value) {
        return queue("rotationY", mView.getRotationY(), value - mView.getRotationY());
    }

    public ViewPropertyAnimator rotationYBy(float value) { return queue("rotationY", mView.getRotationY(), value); }

    public ViewPropertyAnimator translationX(float value) {
        return queue("translationX", mView.getTranslationX(), value - mView.getTranslationX());
    }

    public ViewPropertyAnimator translationXBy(float value) {
        return queue("translationX", mView.getTranslationX(), value);
    }

    public ViewPropertyAnimator translationY(float value) {
        return queue("translationY", mView.getTranslationY(), value - mView.getTranslationY());
    }

    public ViewPropertyAnimator translationYBy(float value) {
        return queue("translationY", mView.getTranslationY(), value);
    }

    public ViewPropertyAnimator translationZ(float value) {
        return queue("translationZ", mView.getTranslationZ(), value - mView.getTranslationZ());
    }

    public ViewPropertyAnimator translationZBy(float value) {
        return queue("translationZ", mView.getTranslationZ(), value);
    }

    public ViewPropertyAnimator scaleX(float value) {
        return queue("scaleX", mView.getScaleX(), value - mView.getScaleX());
    }

    public ViewPropertyAnimator scaleXBy(float value) { return queue("scaleX", mView.getScaleX(), value); }

    public ViewPropertyAnimator scaleY(float value) {
        return queue("scaleY", mView.getScaleY(), value - mView.getScaleY());
    }

    public ViewPropertyAnimator scaleYBy(float value) { return queue("scaleY", mView.getScaleY(), value); }

    public ViewPropertyAnimator alpha(float value) {
        return queue("alpha", mView.getAlpha(), value - mView.getAlpha());
    }

    public ViewPropertyAnimator alphaBy(float value) { return queue("alpha", mView.getAlpha(), value); }

    public ViewPropertyAnimator withLayer() {
        mWithLayer = true;
        return this;
    }

    public ViewPropertyAnimator withStartAction(Runnable runnable) {
        mStartAction = runnable;
        return this;
    }

    public ViewPropertyAnimator withEndAction(Runnable runnable) {
        mEndAction = runnable;
        return this;
    }

    private ViewPropertyAnimator queue(String name, float from, float delta) {
        for (int i = 0; i < mPending.size(); i++) {
            if (name.equals(mPending.get(i).name)) {
                mPending.set(i, new Pending(name, from, delta));
                post();
                return this;
            }
        }
        mPending.add(new Pending(name, from, delta));
        post();
        return this;
    }

    private void post() {
        if (mStarter != null) return;
        mStarter = new Runnable() {
            @Override
            public void run() { startAnimation(); }
        };
        mView.postOnAnimation(mStarter);
    }

    private void startAnimation() {
        mStarter = null;
        if (mPending.isEmpty()) return;
        final Pending[] pending = mPending.toArray(new Pending[mPending.size()]);
        mPending.clear();
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        if (mDuration >= 0) anim.setDuration(mDuration);
        if (mStartDelaySet) anim.setStartDelay(mStartDelay);
        if (mInterpolatorSet) anim.setInterpolator(mInterpolator);
        final Animator.AnimatorListener listener = mListener;
        final ValueAnimator.AnimatorUpdateListener update = mUpdateListener;
        final Runnable startAction = mStartAction;
        final Runnable endAction = mEndAction;
        final boolean withLayer = mWithLayer;
        final int oldLayer = mView.getLayerType();
        mStartAction = null;
        mEndAction = null;
        mWithLayer = false;
        if (update != null) anim.addUpdateListener(update);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                for (int i = 0; i < pending.length; i++) {
                    Pending p = pending[i];
                    apply(p.name, p.from + p.delta * fraction);
                }
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (withLayer) mView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                if (startAction != null) startAction.run();
                if (listener != null) listener.onAnimationStart(animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (withLayer) mView.setLayerType(oldLayer, null);
                if (listener != null) listener.onAnimationEnd(animation);
                if (endAction != null) endAction.run();
                if (mAnimator == anim) mAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (listener != null) listener.onAnimationCancel(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                if (listener != null) listener.onAnimationRepeat(animation);
            }
        });
        mAnimator = anim;
        anim.start();
    }

    private void apply(String name, float value) {
        if ("x".equals(name)) mView.setX(value);
        else if ("y".equals(name)) mView.setY(value);
        else if ("z".equals(name)) mView.setZ(value);
        else if ("rotation".equals(name)) mView.setRotation(value);
        else if ("rotationX".equals(name)) mView.setRotationX(value);
        else if ("rotationY".equals(name)) mView.setRotationY(value);
        else if ("translationX".equals(name)) mView.setTranslationX(value);
        else if ("translationY".equals(name)) mView.setTranslationY(value);
        else if ("translationZ".equals(name)) mView.setTranslationZ(value);
        else if ("scaleX".equals(name)) mView.setScaleX(value);
        else if ("scaleY".equals(name)) mView.setScaleY(value);
        else if ("alpha".equals(name)) mView.setAlpha(value);
    }

    private static final class Pending {
        final String name;
        final float from;
        final float delta;

        Pending(String name, float from, float delta) {
            this.name = name;
            this.from = from;
            this.delta = delta;
        }
    }
}
