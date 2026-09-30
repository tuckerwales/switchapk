package android.os;

public abstract class Vibrator {
    public abstract boolean hasVibrator();
    public boolean hasAmplitudeControl() { return false; }
    public void vibrate(long milliseconds) { vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)); }
    public void vibrate(long milliseconds, android.media.AudioAttributes attributes) { vibrate(milliseconds); }
    public void vibrate(long[] pattern, int repeat) { vibrate(VibrationEffect.createWaveform(pattern, repeat)); }
    public void vibrate(long[] pattern, int repeat, android.media.AudioAttributes attributes) { vibrate(pattern, repeat); }
    public void vibrate(VibrationEffect vibe) { vibrate(vibe, null); }
    public abstract void vibrate(VibrationEffect vibe, android.media.AudioAttributes attributes);
    public abstract void cancel();

    /** The Joy-Con rumble backed implementation returned by getSystemService(VIBRATOR_SERVICE). */
    public static final class SystemVibrator extends Vibrator {
        public boolean hasVibrator() { return true; }
        public void vibrate(VibrationEffect vibe, android.media.AudioAttributes attributes) {
            nativeVibrate((int) Math.min(vibe.getDuration(), 5000));
        }
        public void cancel() { nativeVibrate(0); }
        private static native void nativeVibrate(int ms);
    }
}
