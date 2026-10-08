package android.os;

/**
 * A vibration as a waveform: segment durations, their amplitudes (0 = off, 1..255, DEFAULT_AMPLITUDE) and the index
 * to repeat from (-1 = once). Predefined effects and compositions are turned into short waveforms here, since the
 * Switch rumble has no effect library.
 */
public abstract class VibrationEffect implements Parcelable {
    public static final int DEFAULT_AMPLITUDE = -1;
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_HEAVY_CLICK = 5;

    long[] mTimings;
    int[] mAmplitudes;
    int mRepeat;

    VibrationEffect() {}

    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        if (milliseconds <= 0) throw new IllegalArgumentException("timing must be positive");
        checkAmplitude(amplitude, false);
        return make(new long[] {milliseconds}, new int[] {amplitude}, -1);
    }

    /** Timings alternate off and on, starting with off, as in Vibrator.vibrate(long[], int). */
    public static VibrationEffect createWaveform(long[] timings, int repeat) {
        int[] amps = new int[timings.length];
        for (int i = 0; i < amps.length; i++) amps[i] = (i % 2 == 0) ? 0 : DEFAULT_AMPLITUDE;
        return createWaveform(timings, amps, repeat);
    }

    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        if (timings.length != amplitudes.length) {
            throw new IllegalArgumentException("timing and amplitude arrays must be of equal length");
        }
        if (timings.length == 0) throw new IllegalArgumentException("timing array must be non-empty");
        long total = 0;
        for (int i = 0; i < timings.length; i++) {
            if (timings[i] < 0) throw new IllegalArgumentException("timings must all be >= 0");
            checkAmplitude(amplitudes[i], true);
            total += timings[i];
        }
        if (total == 0) throw new IllegalArgumentException("at least one timing must be > 0");
        if (repeat < -1 || repeat >= timings.length) {
            throw new IllegalArgumentException("repeat index must be within the bounds of the timings array");
        }
        return make(timings.clone(), amplitudes.clone(), repeat);
    }

    public static VibrationEffect createPredefined(int effectId) {
        switch (effectId) {
            case EFFECT_TICK:
                return make(new long[] {10}, new int[] {120}, -1);
            case EFFECT_DOUBLE_CLICK:
                return make(new long[] {30, 100, 30}, new int[] {DEFAULT_AMPLITUDE, 0, DEFAULT_AMPLITUDE}, -1);
            case EFFECT_HEAVY_CLICK:
                return make(new long[] {60}, new int[] {255}, -1);
            case EFFECT_CLICK:
                return make(new long[] {30}, new int[] {DEFAULT_AMPLITUDE}, -1);
            default:
                throw new IllegalArgumentException("Unknown prebaked effect type (value=" + effectId + ")");
        }
    }

    public static Composition startComposition() {
        return new Composition();
    }

    private static void checkAmplitude(int a, boolean zeroOk) {
        if (a == DEFAULT_AMPLITUDE) return;
        if (a < (zeroOk ? 0 : 1) || a > 255) {
            throw new IllegalArgumentException("amplitude must either be DEFAULT_AMPLITUDE, or between "
                    + (zeroOk ? 0 : 1) + " and 255 inclusive (amplitude=" + a + ")");
        }
    }

    static VibrationEffect make(long[] timings, int[] amplitudes, int repeat) {
        VibrationEffect e = new VibrationEffect() {};
        e.mTimings = timings;
        e.mAmplitudes = amplitudes;
        e.mRepeat = repeat;
        return e;
    }

    /** framework-internal (hidden in AOSP): the total length in ms, Long.MAX_VALUE when it repeats. */
    public long getDuration() {
        if (mRepeat >= 0) return Long.MAX_VALUE;
        long s = 0;
        for (long t : mTimings) s += t;
        return s;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTimings.length);
        for (int i = 0; i < mTimings.length; i++) {
            dest.writeLong(mTimings[i]);
            dest.writeInt(mAmplitudes[i]);
        }
        dest.writeInt(mRepeat);
    }

    public static final Parcelable.Creator<VibrationEffect> CREATOR = new Parcelable.Creator<VibrationEffect>() {
        public VibrationEffect createFromParcel(Parcel in) {
            int n = in.readInt();
            long[] t = new long[n];
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                t[i] = in.readLong();
                a[i] = in.readInt();
            }
            return make(t, a, in.readInt());
        }

        public VibrationEffect[] newArray(int size) { return new VibrationEffect[size]; }
    };

    /** Primitives become short pulses of a fixed length and the requested scale. */
    public static final class Composition {
        public static final int PRIMITIVE_CLICK = 1;
        public static final int PRIMITIVE_THUD = 2;
        public static final int PRIMITIVE_SPIN = 3;
        public static final int PRIMITIVE_QUICK_RISE = 4;
        public static final int PRIMITIVE_SLOW_RISE = 5;
        public static final int PRIMITIVE_QUICK_FALL = 6;
        public static final int PRIMITIVE_TICK = 7;
        public static final int PRIMITIVE_LOW_TICK = 8;

        private final java.util.ArrayList<long[]> mParts = new java.util.ArrayList<long[]>();

        Composition() {}

        public Composition addPrimitive(int primitiveId) { return addPrimitive(primitiveId, 1f, 0); }

        public Composition addPrimitive(int primitiveId, float scale) { return addPrimitive(primitiveId, scale, 0); }

        public Composition addPrimitive(int primitiveId, float scale, int delay) {
            if (primitiveId < PRIMITIVE_CLICK || primitiveId > PRIMITIVE_LOW_TICK) {
                throw new IllegalArgumentException("Unknown primitive ID: " + primitiveId);
            }
            if (scale < 0f || scale > 1f) throw new IllegalArgumentException("scale must be between 0 and 1");
            if (delay < 0) throw new IllegalArgumentException("delay must be >= 0");
            mParts.add(new long[] {primitiveId, Math.round(scale * 255), delay});
            return this;
        }

        public VibrationEffect compose() {
            if (mParts.isEmpty()) throw new IllegalStateException("Composition must have at least one element to compose.");
            java.util.ArrayList<long[]> segs = new java.util.ArrayList<long[]>();
            for (long[] p : mParts) {
                if (p[2] > 0) segs.add(new long[] {p[2], 0});
                segs.add(new long[] {primitiveMs((int) p[0]), Math.max(1, p[1])});
            }
            long[] t = new long[segs.size()];
            int[] a = new int[segs.size()];
            for (int i = 0; i < t.length; i++) {
                t[i] = segs.get(i)[0];
                a[i] = (int) segs.get(i)[1];
            }
            return make(t, a, -1);
        }

        private static int primitiveMs(int id) {
            switch (id) {
                case PRIMITIVE_THUD: return 50;
                case PRIMITIVE_SPIN: return 100;
                case PRIMITIVE_QUICK_RISE: return 80;
                case PRIMITIVE_SLOW_RISE: return 200;
                case PRIMITIVE_QUICK_FALL: return 60;
                case PRIMITIVE_TICK:
                case PRIMITIVE_LOW_TICK: return 8;
                default: return 12;
            }
        }
    }
}
