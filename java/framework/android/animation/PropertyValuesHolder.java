package android.animation;

import android.graphics.Path;
import android.graphics.PointF;
import android.util.Log;
import android.util.Property;
import java.util.Arrays;

/**
 * The values of one property across an animation. Float and int holders lerp with FloatEvaluator
 * and IntEvaluator. A single value means "end here", and the start is read from the object.
 * Multi-parameter holders are not applied: a two-property path animation is the supported path form.
 */
public class PropertyValuesHolder implements Cloneable {
    private static final String TAG = "PropertyValuesHolder";
    private static boolean sLoggedMulti;

    String mPropertyName;
    Property mProperty;
    Class mValueType;
    Keyframe[] mKeyframes;
    TypeEvaluator mEvaluator;
    TypeConverter mConverter;
    Object mAnimatedValue;

    PropertyValuesHolder(String name) {
        mPropertyName = name;
    }

    PropertyValuesHolder(Property property) {
        mProperty = property;
        if (property != null) {
            mPropertyName = property.getName();
            mValueType = property.getType();
        }
    }

    public static PropertyValuesHolder ofInt(String propertyName, int... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(propertyName);
        h.setIntValues(values);
        return h;
    }

    public static PropertyValuesHolder ofInt(Property<?, Integer> property, int... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(property);
        h.setIntValues(values);
        return h;
    }

    public static PropertyValuesHolder ofMultiInt(String propertyName, int[][] values) {
        return multi(propertyName);
    }

    public static PropertyValuesHolder ofMultiInt(String propertyName, Path path) {
        return multi(propertyName);
    }

    public static <V> PropertyValuesHolder ofMultiInt(String propertyName, TypeConverter<V, int[]> converter,
            TypeEvaluator<V> evaluator, V... values) {
        return multi(propertyName);
    }

    public static <T> PropertyValuesHolder ofMultiInt(String propertyName, TypeConverter<T, int[]> converter,
            TypeEvaluator<T> evaluator, Keyframe... values) {
        return multi(propertyName);
    }

    public static PropertyValuesHolder ofFloat(String propertyName, float... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(propertyName);
        h.setFloatValues(values);
        return h;
    }

    public static PropertyValuesHolder ofFloat(Property<?, Float> property, float... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(property);
        h.setFloatValues(values);
        return h;
    }

    public static PropertyValuesHolder ofMultiFloat(String propertyName, float[][] values) {
        return multi(propertyName);
    }

    public static PropertyValuesHolder ofMultiFloat(String propertyName, Path path) {
        return multi(propertyName);
    }

    public static <V> PropertyValuesHolder ofMultiFloat(String propertyName, TypeConverter<V, float[]> converter,
            TypeEvaluator<V> evaluator, V... values) {
        return multi(propertyName);
    }

    public static <T> PropertyValuesHolder ofMultiFloat(String propertyName, TypeConverter<T, float[]> converter,
            TypeEvaluator<T> evaluator, Keyframe... values) {
        return multi(propertyName);
    }

    public static PropertyValuesHolder ofObject(String propertyName, TypeEvaluator evaluator, Object... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(propertyName);
        h.setObjectValues(values);
        h.setEvaluator(evaluator);
        return h;
    }

    public static PropertyValuesHolder ofObject(String propertyName, TypeConverter<PointF, ?> converter, Path path) {
        PropertyValuesHolder h = ofKeyframe(propertyName, pointFrames(path));
        h.mConverter = converter;
        h.mEvaluator = POINT_EVALUATOR;
        return h;
    }

    public static <V> PropertyValuesHolder ofObject(Property property, TypeEvaluator<V> evaluator, V... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(property);
        h.setObjectValues(values);
        h.setEvaluator(evaluator);
        return h;
    }

    public static <T, V> PropertyValuesHolder ofObject(Property<?, V> property, TypeConverter<T, V> converter,
            TypeEvaluator<T> evaluator, T... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(property);
        h.setObjectValues(values);
        h.mConverter = converter;
        h.setEvaluator(evaluator);
        return h;
    }

    public static <V> PropertyValuesHolder ofObject(Property<?, V> property, TypeConverter<PointF, V> converter,
            Path path) {
        PropertyValuesHolder h = ofKeyframe(property, pointFrames(path));
        h.mConverter = converter;
        h.mEvaluator = POINT_EVALUATOR;
        return h;
    }

    public static PropertyValuesHolder ofKeyframe(String propertyName, Keyframe... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(propertyName);
        h.setKeyframes(values);
        return h;
    }

    public static PropertyValuesHolder ofKeyframe(Property property, Keyframe... values) {
        PropertyValuesHolder h = new PropertyValuesHolder(property);
        h.setKeyframes(values);
        return h;
    }

    /** Two float holders, x then y, sampled from a path. Used by ObjectAnimator.ofFloat/ofInt(..., Path). */
    static PropertyValuesHolder[] ofPath(String xName, String yName, Property xProperty, Property yProperty,
            Path path, boolean asInt) {
        float[] points = path == null ? new float[0] : path.approximate(0.5f);
        int n = points.length / 3;
        if (n == 0) n = 1;
        Keyframe[] x = new Keyframe[Math.max(n, 1)];
        Keyframe[] y = new Keyframe[Math.max(n, 1)];
        if (points.length < 3) {
            x[0] = asInt ? Keyframe.ofInt(0f, 0) : Keyframe.ofFloat(0f, 0f);
            y[0] = asInt ? Keyframe.ofInt(1f, 0) : Keyframe.ofFloat(1f, 0f);
        } else {
            for (int i = 0; i < n; i++) {
                float fraction = points[i * 3];
                float px = points[i * 3 + 1];
                float py = points[i * 3 + 2];
                if (asInt) {
                    x[i] = Keyframe.ofInt(fraction, Math.round(px));
                    y[i] = Keyframe.ofInt(fraction, Math.round(py));
                } else {
                    x[i] = Keyframe.ofFloat(fraction, px);
                    y[i] = Keyframe.ofFloat(fraction, py);
                }
            }
        }
        PropertyValuesHolder hx = xProperty != null ? ofKeyframe(xProperty, x) : ofKeyframe(xName, x);
        PropertyValuesHolder hy = yProperty != null ? ofKeyframe(yProperty, y) : ofKeyframe(yName, y);
        return new PropertyValuesHolder[] {hx, hy};
    }

    private static Keyframe[] pointFrames(Path path) {
        float[] points = path == null ? new float[0] : path.approximate(0.5f);
        int n = points.length / 3;
        if (n == 0) return new Keyframe[] {Keyframe.ofObject(0f, new PointF()), Keyframe.ofObject(1f, new PointF())};
        Keyframe[] frames = new Keyframe[n];
        for (int i = 0; i < n; i++) {
            frames[i] = Keyframe.ofObject(points[i * 3], new PointF(points[i * 3 + 1], points[i * 3 + 2]));
        }
        return frames;
    }

    private static final TypeEvaluator POINT_EVALUATOR = new TypeEvaluator() {
        public Object evaluate(float fraction, Object startValue, Object endValue) {
            PointF a = (PointF) startValue;
            PointF b = (PointF) endValue;
            return new PointF(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction);
        }
    };

    private static PropertyValuesHolder multi(String propertyName) {
        if (!sLoggedMulti) {
            sLoggedMulti = true;
            Log.w(TAG, "multi-value property animation is not applied");
        }
        PropertyValuesHolder h = new PropertyValuesHolder((String) null);
        h.setFloatValues(0f);
        h.mPropertyName = propertyName;
        h.mSkipSet = true;
        return h;
    }

    private boolean mSkipSet;

    public void setIntValues(int... values) {
        if (values == null || values.length == 0) return;
        mValueType = int.class;
        mEvaluator = new IntEvaluator();
        if (values.length == 1) {
            mKeyframes = new Keyframe[] {Keyframe.ofInt(0f), Keyframe.ofInt(1f, values[0])};
        } else {
            mKeyframes = new Keyframe[values.length];
            for (int i = 0; i < values.length; i++) {
                mKeyframes[i] = Keyframe.ofInt((float) i / (values.length - 1), values[i]);
            }
        }
    }

    public void setFloatValues(float... values) {
        if (values == null || values.length == 0) return;
        mValueType = float.class;
        mEvaluator = new FloatEvaluator();
        if (values.length == 1) {
            mKeyframes = new Keyframe[] {Keyframe.ofFloat(0f), Keyframe.ofFloat(1f, values[0])};
        } else {
            mKeyframes = new Keyframe[values.length];
            for (int i = 0; i < values.length; i++) {
                mKeyframes[i] = Keyframe.ofFloat((float) i / (values.length - 1), values[i]);
            }
        }
    }

    public void setKeyframes(Keyframe... values) {
        if (values == null || values.length == 0) return;
        mKeyframes = Arrays.copyOf(values, values.length);
        mValueType = values[0].getType();
        mKeyframes[0].setFraction(0f);
        mKeyframes[mKeyframes.length - 1].setFraction(1f);
        if (mValueType == float.class) mEvaluator = new FloatEvaluator();
        else if (mValueType == int.class) mEvaluator = new IntEvaluator();
    }

    public void setObjectValues(Object... values) {
        if (values == null || values.length == 0) return;
        mValueType = values[0] != null ? values[0].getClass() : Object.class;
        if (values.length == 1) {
            mKeyframes = new Keyframe[] {Keyframe.ofObject(0f), Keyframe.ofObject(1f, values[0])};
        } else {
            mKeyframes = new Keyframe[values.length];
            for (int i = 0; i < values.length; i++) {
                mKeyframes[i] = Keyframe.ofObject((float) i / (values.length - 1), values[i]);
            }
        }
    }

    public void setConverter(TypeConverter converter) { mConverter = converter; }

    public void setEvaluator(TypeEvaluator evaluator) { mEvaluator = evaluator; }

    public void setPropertyName(String propertyName) { mPropertyName = propertyName; }

    public void setProperty(Property property) {
        mProperty = property;
        if (property != null) mPropertyName = property.getName();
    }

    public String getPropertyName() { return mPropertyName; }

    void init() {
        if (mEvaluator == null) {
            if (mValueType == float.class || mValueType == Float.class) mEvaluator = new FloatEvaluator();
            else if (mValueType == int.class || mValueType == Integer.class) mEvaluator = new IntEvaluator();
        }
    }

    void setupSetter(Object target) {
        if (mProperty != null || target == null || mPropertyName == null || mSkipSet) return;
        Class boxed = Float.class;
        if (mValueType == int.class || mValueType == Integer.class) boxed = Integer.class;
        else if (mValueType != null && mValueType != float.class && mValueType != Float.class) boxed = mValueType;
        try {
            mProperty = Property.of(target.getClass(), boxed, mPropertyName);
        } catch (RuntimeException e) {
            Log.w(TAG, "No property " + mPropertyName + " on " + target.getClass().getSimpleName());
        }
    }

    void setupStartValue(Object target) { fillUnset(target, 0); }

    void setupEndValue(Object target) {
        if (mKeyframes != null && mKeyframes.length > 0) fillUnset(target, mKeyframes.length - 1);
    }

    private void fillUnset(Object target, int index) {
        if (mKeyframes == null || index < 0 || index >= mKeyframes.length) return;
        if (mKeyframes[index].hasValue() || target == null) return;
        Object value = read(target);
        if (value != null) mKeyframes[index].setValue(value);
    }

    private Object read(Object target) {
        if (mProperty != null) return mProperty.get(target);
        return null;
    }

    /**
     * A fraction outside 0..1 extrapolates the first or last interval. Overshoot and anticipate
     * return those values, and snapping to the endpoint would hide them.
     */
    void calculateValue(float fraction) {
        if (mKeyframes == null || mKeyframes.length == 0) return;
        int last = mKeyframes.length - 1;
        if (last == 0) {
            mAnimatedValue = mKeyframes[0].getValue();
            return;
        }
        int i;
        if (fraction < mKeyframes[0].getFraction()) i = 0;
        else if (fraction > mKeyframes[last].getFraction()) i = last - 1;
        else {
            i = 0;
            while (i < last - 1 && fraction >= mKeyframes[i + 1].getFraction()) i++;
        }
        Keyframe a = mKeyframes[i];
        Keyframe b = mKeyframes[i + 1];
        float span = b.getFraction() - a.getFraction();
        float interval = span <= 0f ? 0f : (fraction - a.getFraction()) / span;
        if (b.getInterpolator() != null) interval = b.getInterpolator().getInterpolation(interval);
        if (mEvaluator == null) init();
        if (mEvaluator != null) mAnimatedValue = mEvaluator.evaluate(interval, a.getValue(), b.getValue());
        else mAnimatedValue = interval < 0.5f ? a.getValue() : b.getValue();
    }

    @SuppressWarnings("unchecked")
    void setAnimatedValue(Object target) {
        if (mSkipSet || target == null) return;
        Object value = mAnimatedValue;
        if (mConverter != null) value = mConverter.convert(value);
        if (mProperty != null) mProperty.set(target, value);
    }

    Object getAnimatedValue() { return mAnimatedValue; }

    @Override
    public PropertyValuesHolder clone() {
        try {
            PropertyValuesHolder h = (PropertyValuesHolder) super.clone();
            if (mKeyframes != null) {
                h.mKeyframes = new Keyframe[mKeyframes.length];
                for (int i = 0; i < mKeyframes.length; i++) h.mKeyframes[i] = mKeyframes[i].clone();
            }
            return h;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        String s = mPropertyName + ": ";
        if (mKeyframes != null) for (int i = 0; i < mKeyframes.length; i++) s += mKeyframes[i].getValue() + " ";
        return s;
    }
}
