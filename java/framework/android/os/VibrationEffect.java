package android.os;

public abstract class VibrationEffect implements Parcelable {
    public static final int DEFAULT_AMPLITUDE = -1;
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_HEAVY_CLICK = 5;

    long mDuration;

    public static VibrationEffect createOneShot(long milliseconds, int amplitude) { return make(milliseconds); }
    public static VibrationEffect createWaveform(long[] timings, int repeat) { return make(sum(timings)); }
    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) { return make(sum(timings)); }
    public static VibrationEffect createPredefined(int effectId) { return make(30); }

    private static long sum(long[] t) {
        long s = 0;
        for (long v : t) s += v;
        return s;
    }

    private static VibrationEffect make(long d) {
        VibrationEffect e = new VibrationEffect() {};
        e.mDuration = d;
        return e;
    }

    public long getDuration() { return mDuration; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeLong(mDuration); }
}
