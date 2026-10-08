package android.os;

public abstract class Vibrator {
    public static final int VIBRATION_EFFECT_SUPPORT_UNKNOWN = 0;
    public static final int VIBRATION_EFFECT_SUPPORT_YES = 1;
    public static final int VIBRATION_EFFECT_SUPPORT_NO = 2;

    Vibrator() {}

    public int getId() { return -1; }
    public abstract boolean hasVibrator();
    public abstract boolean hasAmplitudeControl();
    public float getResonantFrequency() { return Float.NaN; }
    public float getQFactor() { return Float.NaN; }

    public void vibrate(long milliseconds) {
        vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    public void vibrate(long milliseconds, android.media.AudioAttributes attributes) { vibrate(milliseconds); }
    public void vibrate(long[] pattern, int repeat) { vibrate(VibrationEffect.createWaveform(pattern, repeat)); }

    public void vibrate(long[] pattern, int repeat, android.media.AudioAttributes attributes) {
        vibrate(pattern, repeat);
    }

    public void vibrate(VibrationEffect vibe) { vibrate(vibe, (android.media.AudioAttributes) null); }
    public abstract void vibrate(VibrationEffect vibe, android.media.AudioAttributes attributes);

    public int[] areEffectsSupported(int... effectIds) {
        int[] r = new int[effectIds.length];
        for (int i = 0; i < r.length; i++) {
            int id = effectIds[i];
            boolean ours = id == VibrationEffect.EFFECT_CLICK || id == VibrationEffect.EFFECT_DOUBLE_CLICK
                    || id == VibrationEffect.EFFECT_TICK || id == VibrationEffect.EFFECT_HEAVY_CLICK;
            r[i] = ours && hasVibrator() ? VIBRATION_EFFECT_SUPPORT_YES : VIBRATION_EFFECT_SUPPORT_NO;
        }
        return r;
    }

    public final int areAllEffectsSupported(int... effectIds) {
        int[] r = areEffectsSupported(effectIds);
        int all = VIBRATION_EFFECT_SUPPORT_YES;
        for (int v : r) {
            if (v == VIBRATION_EFFECT_SUPPORT_NO) return VIBRATION_EFFECT_SUPPORT_NO;
            if (v == VIBRATION_EFFECT_SUPPORT_UNKNOWN) all = VIBRATION_EFFECT_SUPPORT_UNKNOWN;
        }
        return all;
    }

    public boolean[] arePrimitivesSupported(int... primitiveIds) { return new boolean[primitiveIds.length]; }

    public final boolean areAllPrimitivesSupported(int... primitiveIds) {
        for (boolean b : arePrimitivesSupported(primitiveIds)) {
            if (!b) return false;
        }
        return true;
    }

    public int[] getPrimitiveDurations(int... primitiveIds) { return new int[primitiveIds.length]; }

    public abstract void cancel();

    /**
     * The controller rumble returned by getSystemService(VIBRATOR_SERVICE). One waveform plays at a time for the whole
     * process; each segment is sent to the platform as a rumble of that length, timed on a "vibrator" thread.
     */
    public static final class SystemVibrator extends Vibrator {
        private static final Object sLock = new Object();
        private static Handler sHandler;
        private static Player sCurrent;

        public SystemVibrator() {}

        @Override public int getId() { return 0; }
        @Override public boolean hasVibrator() { return true; }
        @Override public boolean hasAmplitudeControl() { return true; }

        @Override
        public void vibrate(VibrationEffect vibe, android.media.AudioAttributes attributes) {
            if (vibe == null) throw new IllegalArgumentException("vibe must not be null");
            synchronized (sLock) {
                if (sHandler == null) {
                    HandlerThread t = new HandlerThread("vibrator");
                    t.start();
                    sHandler = new Handler(t.getLooper());
                }
                if (sCurrent != null) sCurrent.stop();
                sCurrent = new Player(vibe);
                sHandler.post(sCurrent);
            }
        }

        @Override
        public void cancel() {
            synchronized (sLock) {
                if (sCurrent == null) return;
                sCurrent.stop();
                sCurrent = null;
            }
            nativeVibrate(0, 0);
        }

        /** Steps through the waveform; each run plays one segment and schedules the next. */
        private static final class Player implements Runnable {
            private final VibrationEffect mEffect;
            private int mIndex;
            private boolean mStopped;

            Player(VibrationEffect effect) {
                mEffect = effect;
            }

            void stop() {
                mStopped = true;
                sHandler.removeCallbacks(this);
            }

            @Override
            public void run() {
                long ms;
                int amp;
                synchronized (sLock) {
                    if (mStopped) return;
                    long[] t = mEffect.mTimings;
                    if (mIndex >= t.length) {
                        if (mEffect.mRepeat < 0) {
                            if (sCurrent == this) sCurrent = null;
                            return;
                        }
                        mIndex = mEffect.mRepeat;
                    }
                    ms = t[mIndex];
                    amp = mEffect.mAmplitudes[mIndex];
                    mIndex++;
                }
                if (ms > 0) {
                    nativeVibrate(amp == 0 ? 0 : (int) Math.min(ms, Integer.MAX_VALUE), amp);
                }
                sHandler.postDelayed(this, ms);
            }
        }

        private static native void nativeVibrate(int ms, int amplitude);
    }
}
