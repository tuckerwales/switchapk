package android.os;

public abstract class VibratorManager {
    VibratorManager() {}

    public abstract int[] getVibratorIds();
    public abstract Vibrator getVibrator(int vibratorId);
    public abstract Vibrator getDefaultVibrator();

    public final void vibrate(CombinedVibration effect) {
        if (effect == null) throw new IllegalArgumentException("effect must not be null");
        // A single rumble motor pair: play the first effect.
        VibrationEffect e = effect.firstEffect();
        if (e != null) getDefaultVibrator().vibrate(e);
    }

    public abstract void cancel();

    /** framework-internal: the instance behind Context.VIBRATOR_MANAGER_SERVICE. */
    public static final class SystemVibratorManager extends VibratorManager {
        private final Vibrator.SystemVibrator mVibrator = new Vibrator.SystemVibrator();

        @Override public int[] getVibratorIds() { return new int[] {0}; }
        @Override public Vibrator getVibrator(int vibratorId) { return vibratorId == 0 ? mVibrator : null; }
        @Override public Vibrator getDefaultVibrator() { return mVibrator; }
        @Override public void cancel() { mVibrator.cancel(); }
    }
}
