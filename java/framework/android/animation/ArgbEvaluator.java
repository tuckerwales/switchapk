package android.animation;

/** Linear per-channel blend. A later Android release blends in gamma space; this is the earlier form. */
public class ArgbEvaluator implements TypeEvaluator {
    public ArgbEvaluator() {}

    public Object evaluate(float fraction, Object startValue, Object endValue) {
        int start = ((Integer) startValue).intValue();
        int end = ((Integer) endValue).intValue();
        int a = channel(start, end, 24, fraction);
        int r = channel(start, end, 16, fraction);
        int g = channel(start, end, 8, fraction);
        int b = channel(start, end, 0, fraction);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int start, int end, int shift, float fraction) {
        int s = (start >> shift) & 0xff;
        int e = (end >> shift) & 0xff;
        return Math.round(s + (e - s) * fraction) & 0xff;
    }
}
