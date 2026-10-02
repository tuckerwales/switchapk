package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays several tween animations together. A duration, fill, repeat, start
 * offset or shared interpolator set on the set replaces the children's when
 * the set is initialized.
 */
public class AnimationSet extends Animation {
    private static final int PROPERTY_FILL_AFTER_MASK = 0x1;
    private static final int PROPERTY_FILL_BEFORE_MASK = 0x2;
    private static final int PROPERTY_REPEAT_MODE_MASK = 0x4;
    private static final int PROPERTY_START_OFFSET_MASK = 0x8;
    private static final int PROPERTY_SHARE_INTERPOLATOR_MASK = 0x10;
    private static final int PROPERTY_DURATION_MASK = 0x20;

    private int mFlags;
    private ArrayList<Animation> mAnimations = new ArrayList<Animation>();
    private Transformation mTemp = new Transformation();

    public AnimationSet(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The Animation constructor calls every setter, and those mark the property as set.
        // A property replaces the children only when this tag actually carries it.
        mFlags = 0;
        TypedArray setAttrs = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.shareInterpolator});
        setFlag(PROPERTY_SHARE_INTERPOLATOR_MASK, setAttrs.getBoolean(0, true));
        setAttrs.recycle();
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.duration, android.R.attr.startOffset, android.R.attr.fillBefore,
                android.R.attr.fillAfter, android.R.attr.repeatMode});
        if (a.hasValue(0)) mFlags |= PROPERTY_DURATION_MASK;
        if (a.hasValue(1)) mFlags |= PROPERTY_START_OFFSET_MASK;
        if (a.hasValue(2)) mFlags |= PROPERTY_FILL_BEFORE_MASK;
        if (a.hasValue(3)) mFlags |= PROPERTY_FILL_AFTER_MASK;
        if (a.hasValue(4)) mFlags |= PROPERTY_REPEAT_MODE_MASK;
        a.recycle();
    }

    public AnimationSet(boolean shareInterpolator) {
        setFlag(PROPERTY_SHARE_INTERPOLATOR_MASK, shareInterpolator);
    }

    private void setFlag(int mask, boolean value) {
        if (value) mFlags |= mask;
        else mFlags &= ~mask;
    }

    public void addAnimation(Animation a) {
        mAnimations.add(a);
    }

    @Override
    protected AnimationSet clone() throws CloneNotSupportedException {
        final AnimationSet animation = (AnimationSet) super.clone();
        animation.mTemp = new Transformation();
        animation.mAnimations = new ArrayList<Animation>();
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) animation.mAnimations.add(mAnimations.get(i).clone());
        return animation;
    }

    public List<Animation> getAnimations() { return mAnimations; }

    @Override
    public void setFillAfter(boolean fillAfter) {
        mFlags |= PROPERTY_FILL_AFTER_MASK;
        super.setFillAfter(fillAfter);
    }

    @Override
    public void setFillBefore(boolean fillBefore) {
        mFlags |= PROPERTY_FILL_BEFORE_MASK;
        super.setFillBefore(fillBefore);
    }

    @Override
    public void setRepeatMode(int repeatMode) {
        mFlags |= PROPERTY_REPEAT_MODE_MASK;
        super.setRepeatMode(repeatMode);
    }

    @Override
    public void setStartOffset(long startOffset) {
        mFlags |= PROPERTY_START_OFFSET_MASK;
        super.setStartOffset(startOffset);
    }

    @Override
    public void setDuration(long durationMillis) {
        mFlags |= PROPERTY_DURATION_MASK;
        super.setDuration(durationMillis);
    }

    @Override
    public void setStartTime(long startTimeMillis) {
        super.setStartTime(startTimeMillis);
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) mAnimations.get(i).setStartTime(startTimeMillis);
    }

    @Override
    public long getDuration() {
        if ((mFlags & PROPERTY_DURATION_MASK) == PROPERTY_DURATION_MASK) return mDuration;
        long duration = 0;
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) duration = Math.max(duration, mAnimations.get(i).getDuration());
        return duration;
    }

    @Override
    public long computeDurationHint() {
        long duration = 0;
        final int count = mAnimations.size();
        for (int i = count - 1; i >= 0; i--) duration = Math.max(duration, mAnimations.get(i).computeDurationHint());
        return duration;
    }

    @Override
    public void restrictDuration(long durationMillis) {
        super.restrictDuration(durationMillis);
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) mAnimations.get(i).restrictDuration(durationMillis);
    }

    @Override
    public void scaleCurrentDuration(float scale) {
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) mAnimations.get(i).scaleCurrentDuration(scale);
    }

    @Override
    public void reset() {
        super.reset();
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) mAnimations.get(i).reset();
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        final boolean durationSet = (mFlags & PROPERTY_DURATION_MASK) == PROPERTY_DURATION_MASK;
        final boolean fillAfterSet = (mFlags & PROPERTY_FILL_AFTER_MASK) == PROPERTY_FILL_AFTER_MASK;
        final boolean fillBeforeSet = (mFlags & PROPERTY_FILL_BEFORE_MASK) == PROPERTY_FILL_BEFORE_MASK;
        final boolean repeatModeSet = (mFlags & PROPERTY_REPEAT_MODE_MASK) == PROPERTY_REPEAT_MODE_MASK;
        final boolean shareInterpolator = (mFlags & PROPERTY_SHARE_INTERPOLATOR_MASK)
                == PROPERTY_SHARE_INTERPOLATOR_MASK;
        final boolean startOffsetSet = (mFlags & PROPERTY_START_OFFSET_MASK) == PROPERTY_START_OFFSET_MASK;
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) {
            Animation a = mAnimations.get(i);
            if (durationSet) a.setDuration(getDuration());
            if (fillAfterSet) a.setFillAfter(getFillAfter());
            if (fillBeforeSet) a.setFillBefore(getFillBefore());
            if (repeatModeSet) a.setRepeatMode(getRepeatMode());
            if (shareInterpolator) a.setInterpolator(getInterpolator());
            if (startOffsetSet) a.setStartOffset(a.getStartOffset() + getStartOffset());
            a.initialize(width, height, parentWidth, parentHeight);
        }
    }

    @Override
    public boolean getTransformation(long currentTime, Transformation t) {
        final int count = mAnimations.size();
        boolean more = false;
        boolean started = false;
        boolean ended = true;
        t.clear();
        for (int i = count - 1; i >= 0; i--) {
            Animation a = mAnimations.get(i);
            mTemp.clear();
            more = a.getTransformation(currentTime, mTemp, getScaleFactor()) || more;
            t.compose(mTemp);
            started = started || a.hasStarted();
            ended = a.hasEnded() && ended;
        }
        if (started && !mStarted) {
            if (mListener != null) mListener.onAnimationStart(this);
            mStarted = true;
        }
        if (ended != mEnded) {
            if (ended && mListener != null) mListener.onAnimationEnd(this);
            mEnded = ended;
        }
        return more;
    }

    @Override
    public boolean willChangeTransformationMatrix() {
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) if (mAnimations.get(i).willChangeTransformationMatrix()) return true;
        return false;
    }

    @Override
    public boolean willChangeBounds() {
        final int count = mAnimations.size();
        for (int i = 0; i < count; i++) if (mAnimations.get(i).willChangeBounds()) return true;
        return false;
    }
}
