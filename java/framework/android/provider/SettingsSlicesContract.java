package android.provider;

import android.net.Uri;

/** Constants and the calls apps actually make. Values match android.jar. */
public class SettingsSlicesContract {
    public static final String AUTHORITY = "android.settings.slices";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    public static final String KEY_AIRPLANE_MODE = "airplane_mode";
    public static final String KEY_BATTERY_SAVER = "battery_saver";
    public static final String KEY_BLUETOOTH = "bluetooth";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_WIFI = "wifi";
    public static final String PATH_SETTING_ACTION = "action";
    public static final String PATH_SETTING_INTENT = "intent";
    private SettingsSlicesContract() {}
}
