package android.os;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The screen stays on and the app is never put to sleep while it runs, so wake locks only count; power save mode,
 * idle modes and thermal throttling never engage (WS15).
 */
public final class PowerManager {
  public static final int ACQUIRE_CAUSES_WAKEUP = 268435456;
  public static final String ACTION_DEVICE_IDLE_MODE_CHANGED = "android.os.action.DEVICE_IDLE_MODE_CHANGED";
  public static final String ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED = "android.os.action.LIGHT_DEVICE_IDLE_MODE_CHANGED";
  public static final String ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED = "android.os.action.LOW_POWER_STANDBY_ENABLED_CHANGED";
  public static final String ACTION_LOW_POWER_STANDBY_POLICY_CHANGED = "android.os.action.LOW_POWER_STANDBY_POLICY_CHANGED";
  public static final String ACTION_POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED";
  public static final String FEATURE_WAKE_ON_LAN_IN_LOW_POWER_STANDBY = "com.android.lowpowerstandby.WAKE_ON_LAN";
  public static final int FULL_WAKE_LOCK = 26;
  public static final int LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF = 2;
  public static final int LOCATION_MODE_FOREGROUND_ONLY = 3;
  public static final int LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF = 1;
  public static final int LOCATION_MODE_NO_CHANGE = 0;
  public static final int LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF = 4;
  public static final int LOW_POWER_STANDBY_ALLOWED_REASON_ONGOING_CALL = 4;
  public static final int LOW_POWER_STANDBY_ALLOWED_REASON_TEMP_POWER_SAVE_ALLOWLIST = 2;
  public static final int LOW_POWER_STANDBY_ALLOWED_REASON_VOICE_INTERACTION = 1;
  public static final int ON_AFTER_RELEASE = 536870912;
  public static final int PARTIAL_WAKE_LOCK = 1;
  public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
  public static final int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 1;
  public static final int SCREEN_BRIGHT_WAKE_LOCK = 10;
  public static final int SCREEN_DIM_WAKE_LOCK = 6;
  public static final int THERMAL_STATUS_CRITICAL = 4;
  public static final int THERMAL_STATUS_EMERGENCY = 5;
  public static final int THERMAL_STATUS_LIGHT = 1;
  public static final int THERMAL_STATUS_MODERATE = 2;
  public static final int THERMAL_STATUS_NONE = 0;
  public static final int THERMAL_STATUS_SEVERE = 3;
  public static final int THERMAL_STATUS_SHUTDOWN = 6;

    private final ArrayList<OnThermalStatusChangedListener> mThermalListeners =
            new ArrayList<OnThermalStatusChangedListener>();

    /** framework-internal: the instance behind Context.POWER_SERVICE. */
    public PowerManager() {}

    public WakeLock newWakeLock(int levelAndFlags, String tag) {
        if (tag == null) throw new IllegalArgumentException("The tag must not be null.");
        return new WakeLock(levelAndFlags, tag);
    }

    public boolean isWakeLockLevelSupported(int level) { return true; }
    public boolean isScreenOn() { return true; }
    public boolean isInteractive() { return true; }
    public boolean isRebootingUserspaceSupported() { return false; }

    public void reboot(String reason) {
        throw new SecurityException("Neither user nor current process has android.permission.REBOOT.");
    }

    public boolean isPowerSaveMode() { return false; }
    public boolean isBatteryDischargePredictionPersonalized() { return false; }
    public int getLocationPowerSaveMode() { return LOCATION_MODE_NO_CHANGE; }
    public boolean isDeviceIdleMode() { return false; }
    public boolean isDeviceLightIdleMode() { return false; }
    public boolean isLowPowerStandbyEnabled() { return false; }
    public boolean isExemptFromLowPowerStandby() { return true; }
    public boolean isAllowedInLowPowerStandby(int reason) { return true; }
    public boolean isAllowedInLowPowerStandby(String feature) { return true; }
    public boolean isIgnoringBatteryOptimizations(String packageName) { return true; }
    public boolean isSustainedPerformanceModeSupported() { return false; }
    public int getCurrentThermalStatus() { return THERMAL_STATUS_NONE; }

    public void addThermalStatusListener(OnThermalStatusChangedListener listener) {
        addThermalStatusListener(null, listener);
    }

    /** The status never changes, so the listener only hears the current one, as AOSP does on registration. */
    public void addThermalStatusListener(Executor executor, final OnThermalStatusChangedListener listener) {
        if (listener == null) throw new NullPointerException("listener cannot be null");
        synchronized (mThermalListeners) {
            if (mThermalListeners.contains(listener)) {
                throw new IllegalArgumentException("Listener already registered: " + listener);
            }
            mThermalListeners.add(listener);
        }
        Runnable r = new Runnable() {
            public void run() {
                listener.onThermalStatusChanged(THERMAL_STATUS_NONE);
            }
        };
        if (executor != null) {
            executor.execute(r);
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }

    public void removeThermalStatusListener(OnThermalStatusChangedListener listener) {
        if (listener == null) throw new NullPointerException("listener cannot be null");
        synchronized (mThermalListeners) {
            if (!mThermalListeners.remove(listener)) {
                throw new IllegalArgumentException("Listener was not added: " + listener);
            }
        }
    }

    /** No thermal model: AOSP answers NaN when headroom is unknown. */
    public float getThermalHeadroom(int forecastSeconds) { return Float.NaN; }

    public Map<Integer, Float> getThermalHeadroomThresholds() { return new java.util.HashMap<Integer, Float>(); }

    public interface OnThermalStatusChangedListener {
        void onThermalStatusChanged(int status);
    }

    public interface WakeLockStateListener {
        void onStateChanged(boolean enabled);
    }

    public final class WakeLock {
        private final int mFlags;
        private final String mTag;
        private int mCount;
        private boolean mRefCounted = true;
        private boolean mHeld;
        private Handler mTimeoutHandler;
        private final Runnable mReleaser = new Runnable() {
            public void run() {
                release(RELEASE_FLAG_TIMEOUT);
            }
        };

        WakeLock(int flags, String tag) {
            mFlags = flags;
            mTag = tag;
        }

        public void setReferenceCounted(boolean value) {
            synchronized (this) {
                mRefCounted = value;
            }
        }

        public void acquire() {
            synchronized (this) {
                acquireLocked();
            }
        }

        public void acquire(long timeout) {
            synchronized (this) {
                acquireLocked();
                if (mTimeoutHandler == null) mTimeoutHandler = new Handler(Looper.getMainLooper());
                mTimeoutHandler.removeCallbacks(mReleaser);
                mTimeoutHandler.postDelayed(mReleaser, timeout);
            }
        }

        private void acquireLocked() {
            if (!mRefCounted || mCount++ == 0) mHeld = true;
        }

        public void release() {
            release(0);
        }

        public void release(int flags) {
            synchronized (this) {
                if (mRefCounted && (flags & RELEASE_FLAG_TIMEOUT) == 0 && mCount == 0 && !mHeld) {
                    throw new RuntimeException("WakeLock under-locked " + mTag);
                }
                if (!mRefCounted || --mCount <= 0 || (flags & RELEASE_FLAG_TIMEOUT) != 0) {
                    mCount = 0;
                    if (mTimeoutHandler != null) mTimeoutHandler.removeCallbacks(mReleaser);
                    mHeld = false;
                }
            }
        }

        public boolean isHeld() {
            synchronized (this) {
                return mHeld;
            }
        }

        public void setWorkSource(WorkSource ws) {}

        public void setStateListener(Executor executor, WakeLockStateListener listener) {}

        @Override
        public String toString() {
            synchronized (this) {
                return "WakeLock{" + Integer.toHexString(System.identityHashCode(this)) + " held=" + mHeld + ", refCount="
                        + mCount + "}";
            }
        }
    }

    /** AOSP's hidden RELEASE_FLAG_TIMEOUT: a timed acquire expired. */
    private static final int RELEASE_FLAG_TIMEOUT = 1 << 16;
}
