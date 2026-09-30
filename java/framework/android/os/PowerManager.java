package android.os;

public final class PowerManager {
    public static final int PARTIAL_WAKE_LOCK = 0x00000001;
    public static final int SCREEN_DIM_WAKE_LOCK = 0x00000006;
    public static final int SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a;
    public static final int FULL_WAKE_LOCK = 0x0000001a;
    public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 0x00000020;
    public static final int ACQUIRE_CAUSES_WAKEUP = 0x10000000;
    public static final int ON_AFTER_RELEASE = 0x20000000;
    public static final int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 1;
    public static final String ACTION_POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED";
    public static final String ACTION_DEVICE_IDLE_MODE_CHANGED = "android.os.action.DEVICE_IDLE_MODE_CHANGED";
    public static final int THERMAL_STATUS_NONE = 0;

    public PowerManager() {}

    public WakeLock newWakeLock(int levelAndFlags, String tag) { return new WakeLock(tag); }
    public boolean isScreenOn() { return true; }
    public boolean isInteractive() { return true; }
    public boolean isPowerSaveMode() { return false; }
    public boolean isDeviceIdleMode() { return false; }
    public boolean isIgnoringBatteryOptimizations(String packageName) { return true; }
    public boolean isSustainedPerformanceModeSupported() { return false; }
    public int getCurrentThermalStatus() { return THERMAL_STATUS_NONE; }
    public boolean isWakeLockLevelSupported(int level) { return true; }

    public final class WakeLock {
        private int mCount;
        private boolean mRefCounted = true;
        private final String mTag;

        WakeLock(String tag) { mTag = tag; }

        public void setReferenceCounted(boolean value) { mRefCounted = value; }
        public void acquire() { mCount++; }
        public void acquire(long timeout) { mCount++; }
        public void release() { release(0); }
        public void release(int flags) {
            if (!mRefCounted) mCount = 0;
            else if (mCount > 0) mCount--;
        }
        public boolean isHeld() { return mCount > 0; }
        public void setWorkSource(Object ws) {}
        @Override public String toString() { return "WakeLock{" + mTag + " held=" + isHeld() + "}"; }
    }
}
