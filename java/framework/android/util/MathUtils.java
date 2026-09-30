package android.util;

public final class MathUtils {
    private MathUtils() {}
    public static float abs(float v) { return v > 0 ? v : -v; }
    public static int constrain(int amount, int low, int high) { return amount < low ? low : (amount > high ? high : amount); }
    public static long constrain(long amount, long low, long high) { return amount < low ? low : (amount > high ? high : amount); }
    public static float constrain(float amount, float low, float high) { return amount < low ? low : (amount > high ? high : amount); }
    public static float lerp(float start, float stop, float amount) { return start + (stop - start) * amount; }
    public static float dist(float x1, float y1, float x2, float y2) { return (float) Math.hypot(x2 - x1, y2 - y1); }
    public static float sq(float v) { return v * v; }
    public static float map(float minStart, float minStop, float maxStart, float maxStop, float value) {
        return maxStart + (maxStop - maxStart) * ((value - minStart) / (minStop - minStart));
    }
}
