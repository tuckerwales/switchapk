package android.animation;

/** One point on an animator timeline. A keyframe with no value is filled from the property at start. */
public abstract class Keyframe implements Cloneable {
    float mFraction;
    Class mValueType;
    private TimeInterpolator mInterpolator;
    boolean mHasValue;

    public Keyframe() {}

    public static Keyframe ofInt(float fraction, int value) { return new IntKeyframe(fraction, value); }

    public static Keyframe ofInt(float fraction) { return new IntKeyframe(fraction); }

    public static Keyframe ofFloat(float fraction, float value) { return new FloatKeyframe(fraction, value); }

    public static Keyframe ofFloat(float fraction) { return new FloatKeyframe(fraction); }

    public static Keyframe ofObject(float fraction, Object value) { return new ObjectKeyframe(fraction, value); }

    public static Keyframe ofObject(float fraction) { return new ObjectKeyframe(fraction, null); }

    public boolean hasValue() { return mHasValue; }

    public abstract Object getValue();

    public abstract void setValue(Object value);

    public float getFraction() { return mFraction; }

    public void setFraction(float fraction) { mFraction = fraction; }

    public TimeInterpolator getInterpolator() { return mInterpolator; }

    public void setInterpolator(TimeInterpolator interpolator) { mInterpolator = interpolator; }

    public Class getType() { return mValueType; }

    @Override
    public abstract Keyframe clone();

    static final class FloatKeyframe extends Keyframe {
        float mValue;

        FloatKeyframe(float fraction, float value) {
            mFraction = fraction;
            mValue = value;
            mValueType = float.class;
            mHasValue = true;
        }

        FloatKeyframe(float fraction) {
            mFraction = fraction;
            mValueType = float.class;
        }

        @Override
        public Object getValue() { return Float.valueOf(mValue); }

        @Override
        public void setValue(Object value) {
            if (value != null && value instanceof Number) {
                mValue = ((Number) value).floatValue();
                mHasValue = true;
            }
        }

        @Override
        public FloatKeyframe clone() {
            FloatKeyframe k = mHasValue ? new FloatKeyframe(getFraction(), mValue) : new FloatKeyframe(getFraction());
            k.setInterpolator(getInterpolator());
            return k;
        }
    }

    static final class IntKeyframe extends Keyframe {
        int mValue;

        IntKeyframe(float fraction, int value) {
            mFraction = fraction;
            mValue = value;
            mValueType = int.class;
            mHasValue = true;
        }

        IntKeyframe(float fraction) {
            mFraction = fraction;
            mValueType = int.class;
        }

        @Override
        public Object getValue() { return Integer.valueOf(mValue); }

        @Override
        public void setValue(Object value) {
            if (value != null && value instanceof Number) {
                mValue = ((Number) value).intValue();
                mHasValue = true;
            }
        }

        @Override
        public IntKeyframe clone() {
            IntKeyframe k = mHasValue ? new IntKeyframe(getFraction(), mValue) : new IntKeyframe(getFraction());
            k.setInterpolator(getInterpolator());
            return k;
        }
    }

    static final class ObjectKeyframe extends Keyframe {
        Object mValue;

        ObjectKeyframe(float fraction, Object value) {
            mFraction = fraction;
            mValue = value;
            mHasValue = value != null;
            mValueType = value != null ? value.getClass() : Object.class;
        }

        @Override
        public Object getValue() { return mValue; }

        @Override
        public void setValue(Object value) {
            mValue = value;
            mHasValue = value != null;
            if (value != null) mValueType = value.getClass();
        }

        @Override
        public ObjectKeyframe clone() {
            ObjectKeyframe k = new ObjectKeyframe(getFraction(), mHasValue ? mValue : null);
            if (!mHasValue) k.mHasValue = false;
            k.mValueType = mValueType;
            k.setInterpolator(getInterpolator());
            return k;
        }
    }
}
