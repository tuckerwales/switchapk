package android.content;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.Display;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

public abstract class Context {
    public static final int MODE_PRIVATE = 0x0000;
    @Deprecated public static final int MODE_WORLD_READABLE = 0x0001;
    @Deprecated public static final int MODE_WORLD_WRITEABLE = 0x0002;
    public static final int MODE_APPEND = 0x8000;
    @Deprecated public static final int MODE_MULTI_PROCESS = 0x0004;
    public static final int MODE_ENABLE_WRITE_AHEAD_LOGGING = 0x0008;
    public static final int MODE_NO_LOCALIZED_COLLATORS = 0x0010;
    public static final int BIND_AUTO_CREATE = 0x0001;
    public static final int BIND_DEBUG_UNBIND = 0x0002;
    public static final int BIND_NOT_FOREGROUND = 0x0004;
    public static final int BIND_ABOVE_CLIENT = 0x0008;
    public static final int BIND_ALLOW_OOM_MANAGEMENT = 0x0010;
    public static final int BIND_WAIVE_PRIORITY = 0x0020;
    public static final int BIND_IMPORTANT = 0x0040;
    public static final int BIND_ADJUST_WITH_ACTIVITY = 0x0080;
    public static final int BIND_NOT_PERCEPTIBLE = 0x00000100;
    public static final int BIND_INCLUDE_CAPABILITIES = 0x000001000;
    public static final int BIND_EXTERNAL_SERVICE = 0x80000000;
    public static final int RECEIVER_VISIBLE_TO_INSTANT_APPS = 0x1;
    public static final int RECEIVER_EXPORTED = 0x2;
    public static final int RECEIVER_NOT_EXPORTED = 0x4;
    public static final int CONTEXT_INCLUDE_CODE = 0x00000001;
    public static final int CONTEXT_IGNORE_SECURITY = 0x00000002;
    public static final int CONTEXT_RESTRICTED = 0x00000004;

    public static final String POWER_SERVICE = "power";
    public static final String WINDOW_SERVICE = "window";
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";
    public static final String ACCOUNT_SERVICE = "account";
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String ALARM_SERVICE = "alarm";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String ACCESSIBILITY_SERVICE = "accessibility";
    public static final String CAPTIONING_SERVICE = "captioning";
    public static final String KEYGUARD_SERVICE = "keyguard";
    public static final String LOCATION_SERVICE = "location";
    public static final String SEARCH_SERVICE = "search";
    public static final String SENSOR_SERVICE = "sensor";
    public static final String STORAGE_SERVICE = "storage";
    public static final String STORAGE_STATS_SERVICE = "storagestats";
    public static final String WALLPAPER_SERVICE = "wallpaper";
    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String VIBRATOR_MANAGER_SERVICE = "vibrator_manager";
    public static final String STATUS_BAR_SERVICE = "statusbar";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String IPSEC_SERVICE = "ipsec";
    public static final String NETWORK_STATS_SERVICE = "netstats";
    public static final String WIFI_SERVICE = "wifi";
    public static final String WIFI_P2P_SERVICE = "wifip2p";
    public static final String NSD_SERVICE = "servicediscovery";
    public static final String AUDIO_SERVICE = "audio";
    public static final String MEDIA_ROUTER_SERVICE = "media_router";
    public static final String MEDIA_SESSION_SERVICE = "media_session";
    public static final String TELEPHONY_SERVICE = "phone";
    public static final String TELEPHONY_SUBSCRIPTION_SERVICE = "telephony_subscription_service";
    public static final String TELECOM_SERVICE = "telecom";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String INPUT_METHOD_SERVICE = "input_method";
    public static final String TEXT_SERVICES_MANAGER_SERVICE = "textservices";
    public static final String APPWIDGET_SERVICE = "appwidget";
    public static final String DROPBOX_SERVICE = "dropbox";
    public static final String DEVICE_POLICY_SERVICE = "device_policy";
    public static final String UI_MODE_SERVICE = "uimode";
    public static final String DOWNLOAD_SERVICE = "download";
    public static final String BATTERY_SERVICE = "batterymanager";
    public static final String NFC_SERVICE = "nfc";
    public static final String BLUETOOTH_SERVICE = "bluetooth";
    public static final String USB_SERVICE = "usb";
    public static final String INPUT_SERVICE = "input";
    public static final String DISPLAY_SERVICE = "display";
    public static final String USER_SERVICE = "user";
    public static final String LAUNCHER_APPS_SERVICE = "launcherapps";
    public static final String RESTRICTIONS_SERVICE = "restrictions";
    public static final String APP_OPS_SERVICE = "appops";
    public static final String CAMERA_SERVICE = "camera";
    public static final String PRINT_SERVICE = "print";
    public static final String CONSUMER_IR_SERVICE = "consumer_ir";
    public static final String MEDIA_PROJECTION_SERVICE = "media_projection";
    public static final String MIDI_SERVICE = "midi";
    public static final String JOB_SCHEDULER_SERVICE = "jobscheduler";
    public static final String USAGE_STATS_SERVICE = "usagestats";
    public static final String FINGERPRINT_SERVICE = "fingerprint";
    public static final String HARDWARE_PROPERTIES_SERVICE = "hardware_properties";
    public static final String SHORTCUT_SERVICE = "shortcut";
    public static final String SYSTEM_HEALTH_SERVICE = "systemhealth";
    public static final String COMPANION_DEVICE_SERVICE = "companiondevice";
    public static final String CROSS_PROFILE_APPS_SERVICE = "crossprofileapps";
    public static final String EUICC_SERVICE = "euicc";
    public static final String TEXT_CLASSIFICATION_SERVICE = "textclassification";
    public static final String BIOMETRIC_SERVICE = "biometric";
    public static final String ROLE_SERVICE = "role";
    public static final String GAME_SERVICE = "game";
    public static final String LOCALE_SERVICE = "locale";

    // ---- core abstract API ----
    public abstract AssetManager getAssets();
    public abstract Resources getResources();
    public abstract PackageManager getPackageManager();
    public abstract ContentResolver getContentResolver();
    public abstract Looper getMainLooper();
    public Executor getMainExecutor() {
        final Handler h = new Handler(getMainLooper());
        return new Executor() {
            public void execute(Runnable r) { h.post(r); }
        };
    }
    public abstract Context getApplicationContext();
    public abstract void setTheme(int resid);
    public int getThemeResId() { return 0; }
    public abstract Resources.Theme getTheme();
    public abstract ClassLoader getClassLoader();
    public abstract String getPackageName();
    public String getOpPackageName() { return getPackageName(); }
    public String getAttributionTag() { return null; }
    public abstract ApplicationInfo getApplicationInfo();
    public abstract String getPackageResourcePath();
    public abstract String getPackageCodePath();
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public SharedPreferences getSharedPreferences(File file, int mode) { return new SharedPreferencesImpl(file); }
    public abstract boolean moveSharedPreferencesFrom(Context sourceContext, String name);
    public abstract boolean deleteSharedPreferences(String name);
    public abstract FileInputStream openFileInput(String name) throws FileNotFoundException;
    public abstract FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException;
    public abstract boolean deleteFile(String name);
    public abstract File getFileStreamPath(String name);
    public File getSharedPreferencesPath(String name) { return new File(getDataDir(), "shared_prefs/" + name + ".xml"); }
    public abstract File getDataDir();
    public abstract File getFilesDir();
    public abstract File getNoBackupFilesDir();
    public abstract File getExternalFilesDir(String type);
    public abstract File[] getExternalFilesDirs(String type);
    public abstract File getObbDir();
    public abstract File[] getObbDirs();
    public abstract File getCacheDir();
    public abstract File getCodeCacheDir();
    public abstract File getExternalCacheDir();
    public abstract File[] getExternalCacheDirs();
    public abstract File[] getExternalMediaDirs();
    public abstract String[] fileList();
    public abstract File getDir(String name, int mode);
    public abstract SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory);
    public abstract SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler);
    public abstract boolean moveDatabaseFrom(Context sourceContext, String name);
    public abstract boolean deleteDatabase(String name);
    public abstract File getDatabasePath(String name);
    public abstract String[] databaseList();
    @Deprecated public abstract Drawable getWallpaper();
    @Deprecated public abstract Drawable peekWallpaper();
    @Deprecated public abstract int getWallpaperDesiredMinimumWidth();
    @Deprecated public abstract int getWallpaperDesiredMinimumHeight();
    @Deprecated public abstract void setWallpaper(Bitmap bitmap) throws IOException;
    @Deprecated public abstract void setWallpaper(InputStream data) throws IOException;
    @Deprecated public abstract void clearWallpaper() throws IOException;
    public abstract void startActivity(Intent intent);
    public abstract void startActivity(Intent intent, Bundle options);
    public void startActivities(Intent[] intents) { for (Intent i : intents) startActivity(i); }
    public void startActivities(Intent[] intents, Bundle options) { startActivities(intents); }
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) throws IntentSender.SendIntentException {}
    public void startIntentSender(IntentSender intent, Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) throws IntentSender.SendIntentException {}
    public abstract void sendBroadcast(Intent intent);
    public abstract void sendBroadcast(Intent intent, String receiverPermission);
    public abstract void sendOrderedBroadcast(Intent intent, String receiverPermission);
    public abstract void sendOrderedBroadcast(Intent intent, String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras);
    public void sendBroadcastAsUser(Intent intent, UserHandle user) { sendBroadcast(intent); }
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission) { sendBroadcast(intent); }
    @Deprecated public abstract void sendStickyBroadcast(Intent intent);
    @Deprecated public abstract void removeStickyBroadcast(Intent intent);
    public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter);
    public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags);
    public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler);
    public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler, int flags);
    public abstract void unregisterReceiver(BroadcastReceiver receiver);
    public abstract ComponentName startService(Intent service);
    public abstract ComponentName startForegroundService(Intent service);
    public abstract boolean stopService(Intent service);
    public abstract boolean bindService(Intent service, ServiceConnection conn, int flags);
    public boolean bindService(Intent service, int flags, Executor executor, ServiceConnection conn) { return bindService(service, conn, flags); }
    public abstract void unbindService(ServiceConnection conn);
    public abstract boolean startInstrumentation(ComponentName className, String profileFile, Bundle arguments);
    public abstract Object getSystemService(String name);

    @SuppressWarnings("unchecked")
    public final <T> T getSystemService(Class<T> serviceClass) {
        String serviceName = getSystemServiceName(serviceClass);
        return serviceName != null ? (T) getSystemService(serviceName) : null;
    }

    public abstract String getSystemServiceName(Class<?> serviceClass);
    public abstract int checkPermission(String permission, int pid, int uid);
    public abstract int checkCallingPermission(String permission);
    public abstract int checkCallingOrSelfPermission(String permission);
    public abstract int checkSelfPermission(String permission);
    public abstract void enforcePermission(String permission, int pid, int uid, String message);
    public abstract void enforceCallingPermission(String permission, String message);
    public abstract void enforceCallingOrSelfPermission(String permission, String message);
    public abstract void grantUriPermission(String toPackage, Uri uri, int modeFlags);
    public abstract void revokeUriPermission(Uri uri, int modeFlags);
    public abstract int checkUriPermission(Uri uri, int pid, int uid, int modeFlags);
    public abstract int checkCallingUriPermission(Uri uri, int modeFlags);
    public abstract int checkCallingOrSelfUriPermission(Uri uri, int modeFlags);
    public abstract int checkUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags);
    public abstract void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message);
    public abstract void enforceCallingUriPermission(Uri uri, int modeFlags, String message);
    public abstract void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message);
    public abstract void enforceUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags, String message);
    public abstract Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException;
    public abstract Context createContextForSplit(String splitName) throws PackageManager.NameNotFoundException;
    public abstract Context createConfigurationContext(Configuration overrideConfiguration);
    public abstract Context createDisplayContext(Display display);
    public Context createWindowContext(int type, Bundle options) { return this; }
    public Context createAttributionContext(String attributionTag) { return this; }
    public abstract Context createDeviceProtectedStorageContext();
    public abstract boolean isDeviceProtectedStorage();
    public boolean isRestricted() { return false; }
    public Display getDisplay() { return android.view.WindowManagerImpl.getDefault().getDefaultDisplay(); }
    public int getDisplayId() { return 0; }
    public boolean isUiContext() { return false; }

    public final CharSequence getText(int resId) { return getResources().getText(resId); }
    public final String getString(int resId) { return getResources().getString(resId); }
    public final String getString(int resId, Object... formatArgs) { return getResources().getString(resId, formatArgs); }
    public final int getColor(int id) { return getResources().getColor(id, getTheme()); }
    public final Drawable getDrawable(int id) { return getResources().getDrawable(id, getTheme()); }
    public final ColorStateList getColorStateList(int id) { return getResources().getColorStateList(id, getTheme()); }
    public final TypedArray obtainStyledAttributes(int[] attrs) { return getTheme().obtainStyledAttributes(attrs); }
    public final TypedArray obtainStyledAttributes(int resid, int[] attrs) throws Resources.NotFoundException { return getTheme().obtainStyledAttributes(resid, attrs); }
    public final TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs) { return getTheme().obtainStyledAttributes(set, attrs, 0, 0); }
    public final TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) { return getTheme().obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes); }

    public void registerComponentCallbacks(ComponentCallbacks callback) { getApplicationContext().registerComponentCallbacks(callback); }
    public void unregisterComponentCallbacks(ComponentCallbacks callback) { getApplicationContext().unregisterComponentCallbacks(callback); }
}
