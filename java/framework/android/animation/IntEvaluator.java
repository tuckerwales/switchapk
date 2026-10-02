package android.animation;

public class IntEvaluator implements TypeEvaluator<Integer> {
    public IntEvaluator() {}

    public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
        int start = startValue.intValue();
        return (int) (start + fraction * (endValue.intValue() - start));
    }
}
