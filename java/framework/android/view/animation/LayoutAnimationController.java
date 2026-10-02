package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.Random;

/**
 * Staggers one animation across the children of a ViewGroup. The delay is a fraction of the
 * animation duration, spread by the interpolator. {@code getDelayForView} and
 * {@code getTransformedIndex} are protected so a grid controller can replace them.
 */
public class LayoutAnimationController {
    public static final int ORDER_NORMAL = 0;
    public static final int ORDER_REVERSE = 1;
    public static final int ORDER_RANDOM = 2;

    protected Animation mAnimation;
    protected Interpolator mInterpolator;
    protected Random mRandomizer;

    private float mDelay = 0.5f;
    private int mOrder = ORDER_NORMAL;
    private long mStartTime = -1;
    private long mMaxDuration;

    public LayoutAnimationController(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.delay, android.R.attr.animationOrder, android.R.attr.animation,
                android.R.attr.interpolator});
        mDelay = a.getFloat(0, mDelay);
        mOrder = a.getInt(1, ORDER_NORMAL);
        int anim = a.getResourceId(2, 0);
        int interp = a.getResourceId(3, 0);
        a.recycle();
        if (anim != 0) setAnimation(context, anim);
        if (interp != 0) setInterpolator(context, interp);
    }

    public LayoutAnimationController(Animation animation) { this(animation, 0.5f); }

    public LayoutAnimationController(Animation animation, float delay) {
        mAnimation = animation;
        mDelay = delay;
    }

    public int getOrder() { return mOrder; }

    public void setOrder(int order) { mOrder = order; }

    public void setAnimation(Context context, int resourceID) {
        setAnimation(AnimationUtils.loadAnimation(context, resourceID));
    }

    public void setAnimation(Animation animation) {
        mAnimation = animation;
        mMaxDuration = 0;
    }

    public Animation getAnimation() { return mAnimation; }

    public void setInterpolator(Context context, int resourceID) {
        setInterpolator(AnimationUtils.loadInterpolator(context, resourceID));
    }

    public void setInterpolator(Interpolator interpolator) { mInterpolator = interpolator; }

    public Interpolator getInterpolator() { return mInterpolator; }

    public float getDelay() { return mDelay; }

    public void setDelay(float delay) { mDelay = delay; }

    public boolean willOverlap() { return mDelay < 1.0f; }

    public void start() { mStartTime = AnimationUtils.currentAnimationTimeMillis(); }

    public final Animation getAnimationForView(View view) {
        if (mAnimation == null) return null;
        long delay = getDelayForView(view);
        Animation anim;
        try {
            anim = mAnimation.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
        anim.setStartOffset(mAnimation.getStartOffset() + delay);
        long span = delay + anim.getDuration() * (long) (anim.getRepeatCount() < 0 ? 1 : anim.getRepeatCount() + 1);
        if (span > mMaxDuration) mMaxDuration = span;
        return anim;
    }

    public boolean isDone() {
        if (mStartTime < 0) return false;
        return AnimationUtils.currentAnimationTimeMillis() > mStartTime + (mMaxDuration == 0 ? mAnimation != null
                ? mAnimation.getDuration() : 0 : mMaxDuration);
    }

    protected long getDelayForView(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        AnimationParameters params = lp == null ? null : lp.layoutAnimationParameters;
        if (params == null || mAnimation == null) return 0;
        float delay = mDelay * mAnimation.getDuration();
        long viewDelay = (long) (getTransformedIndex(params) * delay);
        float totalDelay = delay * params.count;
        if (totalDelay <= 0f) return viewDelay;
        Interpolator interp = mInterpolator != null ? mInterpolator : new LinearInterpolator();
        float normalized = interp.getInterpolation((float) viewDelay / totalDelay);
        return (long) (normalized * totalDelay);
    }

    protected int getTransformedIndex(AnimationParameters params) {
        switch (mOrder) {
            case ORDER_REVERSE:
                return params.count - 1 - params.index;
            case ORDER_RANDOM:
                if (mRandomizer == null) mRandomizer = new Random();
                return (int) (params.count * mRandomizer.nextFloat());
            case ORDER_NORMAL:
            default:
                return params.index;
        }
    }

    public static class AnimationParameters {
        public int count;
        public int index;

        public AnimationParameters() {}
    }
}
