package android.os;

public class BatteryManager {
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_HEALTH = "health";
    public static final String EXTRA_PRESENT = "present";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_PLUGGED = "plugged";
    public static final String EXTRA_VOLTAGE = "voltage";
    public static final String EXTRA_TEMPERATURE = "temperature";
    public static final String EXTRA_TECHNOLOGY = "technology";
    public static final int BATTERY_STATUS_UNKNOWN = 1;
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_DISCHARGING = 3;
    public static final int BATTERY_STATUS_NOT_CHARGING = 4;
    public static final int BATTERY_STATUS_FULL = 5;
    public static final int BATTERY_HEALTH_GOOD = 2;
    public static final int BATTERY_PLUGGED_AC = 1;
    public static final int BATTERY_PLUGGED_USB = 2;
    public static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
    public static final int BATTERY_PROPERTY_CURRENT_NOW = 2;
    public static final int BATTERY_PROPERTY_CURRENT_AVERAGE = 3;
    public static final int BATTERY_PROPERTY_CAPACITY = 4;
    public static final int BATTERY_PROPERTY_ENERGY_COUNTER = 5;
    public static final int BATTERY_PROPERTY_STATUS = 6;

    public int getIntProperty(int id) { return id == BATTERY_PROPERTY_CAPACITY ? 100 : 0; }
    public long getLongProperty(int id) { return getIntProperty(id); }
    public boolean isCharging() { return false; }
    public long computeChargeTimeRemaining() { return -1; }
}
