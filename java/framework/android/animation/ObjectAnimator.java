package android.animation;

import android.graphics.Path;
import android.graphics.PointF;
import android.os.Looper;
import android.util.Property;
import java.util.ArrayList;

/**
 * Animates named properties on a target. The target is held strongly: this VM clears weak
 * references on every GC, which would drop the target mid-animation.
 * ofMultiInt and ofMultiFloat log once and do not call the setter.
 */
public final class ObjectAnimator extends ValueAnimator {
    private static final ArrayList<ObjectAnimator> sAnimators = new ArrayList<ObjectAnimator>();

    Object mTarget;
    String mPropertyName;
    Property mProperty;
    boolean mAutoCancel;

    public ObjectAnimator() {}

    public void setPropertyName(String propertyName) {
        if (mValues != null && mValues.length > 0) mValues[0].setPropertyName(propertyName);
        mPropertyName = propertyName;
        mInitialized = false;
    }

    public void setProperty(Property property) {
        mProperty = property;
        if (property != null) mPropertyName = property.getName();
        if (mValues != null && mValues.length > 0) mValues[0].setProperty(property);
        mInitialized = false;
    }

    public String getPropertyName() {
        if (mPropertyName != null) return mPropertyName;
        if (mValues != null && mValues.length > 0) return mValues[0].getPropertyName();
        return null;
    }

    public static ObjectAnimator ofInt(Object target, String propertyName, int... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setPropertyName(propertyName);
        anim.setIntValues(values);
        return anim;
    }

    public static ObjectAnimator ofInt(Object target, String xPropertyName, String yPropertyName, Path path) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofPath(xPropertyName, yPropertyName, null, null, path, true));
        return anim;
    }

    public static <T> ObjectAnimator ofInt(T target, Property<T, Integer> property, int... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.mProperty = property;
        anim.setIntValues(values);
        return anim;
    }

    public static <T> ObjectAnimator ofInt(T target, Property<T, Integer> xProperty, Property<T, Integer> yProperty,
            Path path) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofPath(nameOf(xProperty), nameOf(yProperty), xProperty, yProperty, path,
                true));
        return anim;
    }

    public static ObjectAnimator ofMultiInt(Object target, String propertyName, int[][] values) {
        return multi(target, PropertyValuesHolder.ofMultiInt(propertyName, values));
    }

    public static ObjectAnimator ofMultiInt(Object target, String propertyName, Path path) {
        return multi(target, PropertyValuesHolder.ofMultiInt(propertyName, path));
    }

    public static <T> ObjectAnimator ofMultiInt(Object target, String propertyName, TypeConverter<T, int[]> converter,
            TypeEvaluator<T> evaluator, T... values) {
        return multi(target, PropertyValuesHolder.ofMultiInt(propertyName, converter, evaluator, values));
    }

    public static ObjectAnimator ofArgb(Object target, String propertyName, int... values) {
        ObjectAnimator anim = ofInt(target, propertyName, values);
        anim.setEvaluator(new ArgbEvaluator());
        return anim;
    }

    public static <T> ObjectAnimator ofArgb(T target, Property<T, Integer> property, int... values) {
        ObjectAnimator anim = ofInt(target, property, values);
        anim.setEvaluator(new ArgbEvaluator());
        return anim;
    }

    public static ObjectAnimator ofFloat(Object target, String propertyName, float... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setPropertyName(propertyName);
        anim.setFloatValues(values);
        return anim;
    }

    public static ObjectAnimator ofFloat(Object target, String xPropertyName, String yPropertyName, Path path) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofPath(xPropertyName, yPropertyName, null, null, path, false));
        return anim;
    }

    public static <T> ObjectAnimator ofFloat(T target, Property<T, Float> property, float... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.mProperty = property;
        anim.setFloatValues(values);
        return anim;
    }

    public static <T> ObjectAnimator ofFloat(T target, Property<T, Float> xProperty, Property<T, Float> yProperty,
            Path path) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofPath(nameOf(xProperty), nameOf(yProperty), xProperty, yProperty, path,
                false));
        return anim;
    }

    public static ObjectAnimator ofMultiFloat(Object target, String propertyName, float[][] values) {
        return multi(target, PropertyValuesHolder.ofMultiFloat(propertyName, values));
    }

    public static ObjectAnimator ofMultiFloat(Object target, String propertyName, Path path) {
        return multi(target, PropertyValuesHolder.ofMultiFloat(propertyName, path));
    }

    public static <T> ObjectAnimator ofMultiFloat(Object target, String propertyName,
            TypeConverter<T, float[]> converter, TypeEvaluator<T> evaluator, T... values) {
        return multi(target, PropertyValuesHolder.ofMultiFloat(propertyName, converter, evaluator, values));
    }

    public static ObjectAnimator ofObject(Object target, String propertyName, TypeEvaluator evaluator,
            Object... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setPropertyName(propertyName);
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    public static ObjectAnimator ofObject(Object target, String propertyName, TypeConverter<PointF, ?> converter,
            Path path) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofObject(propertyName, converter, path));
        return anim;
    }

    public static <T, V> ObjectAnimator ofObject(T target, Property<T, V> property, TypeEvaluator<V> evaluator,
            V... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.mProperty = property;
        anim.setObjectValues(values);
        anim.setEvaluator(evaluator);
        return anim;
    }

    public static <T, V, P> ObjectAnimator ofObject(T target, Property<T, P> property, TypeConverter<V, P> converter,
            TypeEvaluator<V> evaluator, V... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofObject(property, converter, evaluator, values));
        return anim;
    }

    public static <T, V> ObjectAnimator ofObject(T target, Property<T, V> property,
            TypeConverter<PointF, V> converter, Path path) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(PropertyValuesHolder.ofObject(property, converter, path));
        return anim;
    }

    public static ObjectAnimator ofPropertyValuesHolder(Object target, PropertyValuesHolder... values) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(values);
        return anim;
    }

    private static ObjectAnimator multi(Object target, PropertyValuesHolder holder) {
        ObjectAnimator anim = new ObjectAnimator();
        anim.setTarget(target);
        anim.setValues(holder);
        return anim;
    }

    private static String nameOf(Property property) { return property != null ? property.getName() : ""; }

    @Override
    public void setIntValues(int... values) {
        if (mValues == null || mValues.length == 0) {
            if (mProperty != null) setValues(PropertyValuesHolder.ofInt(mProperty, values));
            else setValues(PropertyValuesHolder.ofInt(mPropertyName != null ? mPropertyName : "", values));
        } else {
            super.setIntValues(values);
        }
    }

    @Override
    public void setFloatValues(float... values) {
        if (mValues == null || mValues.length == 0) {
            if (mProperty != null) setValues(PropertyValuesHolder.ofFloat(mProperty, values));
            else setValues(PropertyValuesHolder.ofFloat(mPropertyName != null ? mPropertyName : "", values));
        } else {
            super.setFloatValues(values);
        }
    }

    @Override
    public void setObjectValues(Object... values) {
        if (mValues == null || mValues.length == 0) {
            String name = mPropertyName != null ? mPropertyName : "";
            setValues(PropertyValuesHolder.ofObject(name, null, values));
            if (mProperty != null && mValues != null && mValues.length > 0) mValues[0].setProperty(mProperty);
        } else {
            super.setObjectValues(values);
        }
    }

    public void setAutoCancel(boolean cancel) { mAutoCancel = cancel; }

    @Override
    public void start() {
        if (Looper.myLooper() == null) {
            super.start();
            return;
        }
        if (mAutoCancel) cancelOthers();
        if (!sAnimators.contains(this)) sAnimators.add(this);
        super.start();
        if (!isStarted()) sAnimators.remove(this);
    }

    @Override
    public ObjectAnimator setDuration(long duration) {
        super.setDuration(duration);
        return this;
    }

    public Object getTarget() { return mTarget; }

    @Override
    public void setTarget(Object target) {
        if (mTarget == target) return;
        mTarget = target;
        mInitialized = false;
    }

    @Override
    public void setupStartValues() {
        initAnimation();
        if (mTarget == null || mValues == null) return;
        for (int i = 0; i < mValues.length; i++) mValues[i].setupStartValue(mTarget);
    }

    @Override
    public void setupEndValues() {
        initAnimation();
        if (mTarget == null || mValues == null) return;
        for (int i = 0; i < mValues.length; i++) mValues[i].setupEndValue(mTarget);
    }

    @Override
    void prepareHolders() {
        if (mTarget == null || mValues == null) return;
        for (int i = 0; i < mValues.length; i++) {
            mValues[i].setupSetter(mTarget);
            mValues[i].setupStartValue(mTarget);
        }
    }

    @Override
    void onAnimatedValue() {
        if (mTarget == null || mValues == null) return;
        for (int i = 0; i < mValues.length; i++) mValues[i].setAnimatedValue(mTarget);
    }

    @Override
    void onAnimationFinished() { sAnimators.remove(this); }

    @Override
    public ObjectAnimator clone() { return (ObjectAnimator) super.clone(); }

    @Override
    public String toString() {
        String s = "ObjectAnimator@" + Integer.toHexString(hashCode()) + ", target " + mTarget;
        if (mValues != null) for (int i = 0; i < mValues.length; i++) s += "\n    " + mValues[i].toString();
        return s;
    }

    private void cancelOthers() {
        ObjectAnimator[] copy = sAnimators.toArray(new ObjectAnimator[sAnimators.size()]);
        for (int i = 0; i < copy.length; i++) {
            ObjectAnimator other = copy[i];
            if (other != this && other.isStarted() && sameProperties(other)) other.cancel();
        }
    }

    private boolean sameProperties(ObjectAnimator other) {
        if (getTarget() != other.getTarget()) return false;
        PropertyValuesHolder[] a = getValues();
        PropertyValuesHolder[] b = other.getValues();
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            String na = a[i].getPropertyName();
            String nb = b[i].getPropertyName();
            if (na == null || !na.equals(nb)) return false;
        }
        return true;
    }
}
