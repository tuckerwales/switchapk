package android.os;

import android.content.Context;
import android.content.Intent;

/**
 * Battery state from the platform (psm on the Switch, the headless script's battery command on the host). The
 * framework part keeps the sticky ACTION_BATTERY_CHANGED current and sends the power and low-battery broadcasts
 * while someone listens for them (WS15).
 */
public class BatteryManager {
    public static final String ACTION_CHARGING = "android.os.action.CHARGING";
    public static final String ACTION_DISCHARGING = "android.os.action.DISCHARGING";
    public static final int BATTERY_HEALTH_COLD = 7;
    public static final int BATTERY_HEALTH_DEAD = 4;
    public static final int BATTERY_HEALTH_GOOD = 2;
    public static final int BATTERY_HEALTH_OVERHEAT = 3;
    public static final int BATTERY_HEALTH_OVER_VOLTAGE = 5;
    public static final int BATTERY_HEALTH_UNKNOWN = 1;
    public static final int BATTERY_HEALTH_UNSPECIFIED_FAILURE = 6;
    public static final int BATTERY_PLUGGED_AC = 1;
    public static final int BATTERY_PLUGGED_DOCK = 8;
    public static final int BATTERY_PLUGGED_USB = 2;
    public static final int BATTERY_PLUGGED_WIRELESS = 4;
    public static final int BATTERY_PROPERTY_CAPACITY = 4;
    public static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
    public static final int BATTERY_PROPERTY_CURRENT_AVERAGE = 3;
    public static final int BATTERY_PROPERTY_CURRENT_NOW = 2;
    public static final int BATTERY_PROPERTY_ENERGY_COUNTER = 5;
    public static final int BATTERY_PROPERTY_STATUS = 6;
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_DISCHARGING = 3;
    public static final int BATTERY_STATUS_FULL = 5;
    public static final int BATTERY_STATUS_NOT_CHARGING = 4;
    public static final int BATTERY_STATUS_UNKNOWN = 1;
    public static final String EXTRA_BATTERY_LOW = "battery_low";
    public static final String EXTRA_CHARGING_STATUS = "android.os.extra.CHARGING_STATUS";
    public static final String EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT";
    public static final String EXTRA_HEALTH = "health";
    public static final String EXTRA_ICON_SMALL = "icon-small";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_PLUGGED = "plugged";
    public static final String EXTRA_PRESENT = "present";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_TECHNOLOGY = "technology";
    public static final String EXTRA_TEMPERATURE = "temperature";
    public static final String EXTRA_VOLTAGE = "voltage";

    /** Switch battery capacity in microampere-hours (4310 mAh), for the charge counter. */
    private static final long CAPACITY_UAH = 4310000L;
    /** AOSP config_lowBatteryWarningLevel and the close level (warning + 5). */
    private static final int LOW_LEVEL = 15;
    private static final int OKAY_LEVEL = 20;
    private static final long POLL_MS = 2000;

    private static final Object sLock = new Object();
    private static Context sContext;
    private static Handler sHandler;
    private static int[] sLast;
    private static boolean sLow;

    public BatteryManager() {}

    /** level, plugged (platform), charging, voltage mV, temperature in 0.1 C. */
    static native void nGetState(int[] out);

    private static int[] state() {
        int[] s = new int[5];
        nGetState(s);
        return s;
    }

    private static int status(int[] s) {
        if (s[1] == 0) return BATTERY_STATUS_DISCHARGING;
        if (s[0] >= 100) return BATTERY_STATUS_FULL;
        return s[2] != 0 ? BATTERY_STATUS_CHARGING : BATTERY_STATUS_NOT_CHARGING;
    }

    private static int plugged(int[] s) {
        return s[1] == 1 ? BATTERY_PLUGGED_AC : s[1] == 2 ? BATTERY_PLUGGED_USB : 0;
    }

    public boolean isCharging() {
        int[] s = state();
        return s[1] != 0 && (s[2] != 0 || s[0] >= 100);
    }

    public int getIntProperty(int id) {
        int[] s = state();
        switch (id) {
            case BATTERY_PROPERTY_CAPACITY:
                return s[0];
            case BATTERY_PROPERTY_STATUS:
                return status(s);
            case BATTERY_PROPERTY_CHARGE_COUNTER:
                return (int) (CAPACITY_UAH * s[0] / 100);
            default:
                // not measured; AOSP answers Integer.MIN_VALUE for targetSdk 28+
                return Integer.MIN_VALUE;
        }
    }

    public long getLongProperty(int id) {
        if (id == BATTERY_PROPERTY_CHARGE_COUNTER) return CAPACITY_UAH * state()[0] / 100;
        if (id == BATTERY_PROPERTY_CAPACITY || id == BATTERY_PROPERTY_STATUS) return getIntProperty(id);
        return Long.MIN_VALUE;
    }

    public String getStringProperty(int id) { return null; }

    public long computeChargeTimeRemaining() { return -1; }

    private static Intent changedIntent(int[] s) {
        Intent i = new Intent(Intent.ACTION_BATTERY_CHANGED);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        i.putExtra(EXTRA_PRESENT, true);
        i.putExtra(EXTRA_LEVEL, s[0]);
        i.putExtra(EXTRA_SCALE, 100);
        i.putExtra(EXTRA_STATUS, status(s));
        i.putExtra(EXTRA_HEALTH, BATTERY_HEALTH_GOOD);
        i.putExtra(EXTRA_PLUGGED, plugged(s));
        i.putExtra(EXTRA_VOLTAGE, s[3]);
        i.putExtra(EXTRA_TEMPERATURE, s[4]);
        i.putExtra(EXTRA_TECHNOLOGY, "Li-ion");
        i.putExtra(EXTRA_BATTERY_LOW, s[1] == 0 && s[0] <= LOW_LEVEL);
        i.putExtra(EXTRA_ICON_SMALL, 0);
        i.putExtra(EXTRA_CYCLE_COUNT, 0);
        return i;
    }

    /**
     * framework-internal: the current sticky ACTION_BATTERY_CHANGED. With a context, the battery is watched from now
     * on and changes are broadcast; the broadcast queue calls this when a receiver registers for a battery action.
     */
    public static Intent stickyBatteryIntent(Context context) {
        int[] s = state();
        if (context != null) startWatching(context, s);
        return changedIntent(s);
    }

    private static void startWatching(Context context, int[] s) {
        synchronized (sLock) {
            if (sHandler != null) return;
            Context app = context.getApplicationContext();
            sContext = app != null ? app : context;
            sHandler = new Handler(Looper.getMainLooper());
            sLast = s;
            sLow = s[1] == 0 && s[0] <= LOW_LEVEL;
        }
        sHandler.postDelayed(sPoll, POLL_MS);
    }

    private static final Runnable sPoll = new Runnable() {
        public void run() {
            poll();
            sHandler.postDelayed(this, POLL_MS);
        }
    };

    /** Compares with the last state and sends what changed, in AOSP BatteryService order. */
    private static void poll() {
        int[] now = state();
        int[] before;
        synchronized (sLock) {
            before = sLast;
            sLast = now;
        }
        if (java.util.Arrays.equals(before, now)) return;
        Context c = sContext;
        c.sendStickyBroadcast(changedIntent(now));
        boolean wasPlugged = before[1] != 0, isPlugged = now[1] != 0;
        if (isPlugged != wasPlugged) {
            sendRegisteredOnly(c, isPlugged ? Intent.ACTION_POWER_CONNECTED : Intent.ACTION_POWER_DISCONNECTED);
            sendRegisteredOnly(c, isPlugged ? ACTION_CHARGING : ACTION_DISCHARGING);
        }
        boolean low = !isPlugged && now[0] <= LOW_LEVEL;
        if (low && !sLow) {
            sLow = true;
            sendRegisteredOnly(c, Intent.ACTION_BATTERY_LOW);
        } else if (sLow && (isPlugged || now[0] >= OKAY_LEVEL)) {
            sLow = false;
            sendRegisteredOnly(c, Intent.ACTION_BATTERY_OKAY);
        }
    }

    private static void sendRegisteredOnly(Context c, String action) {
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        c.sendBroadcast(i);
    }

    /** framework-internal: whether an action is one this class broadcasts. */
    public static boolean isBatteryAction(String action) {
        return Intent.ACTION_BATTERY_CHANGED.equals(action) || Intent.ACTION_POWER_CONNECTED.equals(action)
                || Intent.ACTION_POWER_DISCONNECTED.equals(action) || Intent.ACTION_BATTERY_LOW.equals(action)
                || Intent.ACTION_BATTERY_OKAY.equals(action) || ACTION_CHARGING.equals(action)
                || ACTION_DISCHARGING.equals(action);
    }
}
