package android.animation;

public class FloatEvaluator implements TypeEvaluator<Number> {
    public FloatEvaluator() {}

    public Float evaluate(float fraction, Number startValue, Number endValue) {
        float start = startValue.floatValue();
        return start + fraction * (endValue.floatValue() - start);
    }
}
