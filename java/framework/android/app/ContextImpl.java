package android.app;

import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.view.inputmethod.InputMethodManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/** framework-internal. One process context for the loaded APK. */
public class ContextImpl extends Context {
    private static final String TAG = "ContextImpl";
    private static boolean sLoggedIpc;

    private final String mPackageName;
    private final ApplicationInfo mInfo;
    private final String mApkPath;
    private final Resources mResources;
    private final HashMap<String, SharedPreferences> mPrefs;
    private final ArrayList<ComponentCallbacks> mCallbacks;
    /** The component this context is the base of (activity, service or application). */
    private Context mOuterContext;
    private ReceiverRestrictedContext mReceiverRestrictedContext;

    private Application mApplication;
    private PackageManager mPackageManager;
    private ContentResolver mResolver;
    private Resources.Theme mTheme;
    private int mThemeRes;

    ContextImpl(String packageName, ApplicationInfo info, String apkPath, Resources resources) {
        mPackageName = packageName;
        mInfo = info;
        mApkPath = apkPath;
        mResources = resources;
        mPrefs = new HashMap<String, SharedPreferences>();
        mCallbacks = new ArrayList<ComponentCallbacks>();
    }

    /** A context for one activity or service: same package state, its own registrations. */
    private ContextImpl(ContextImpl base, Context outer) {
        mPackageName = base.mPackageName;
        mInfo = base.mInfo;
        mApkPath = base.mApkPath;
        mResources = base.mResources;
        mPrefs = base.mPrefs;
        mCallbacks = base.mCallbacks;
        mApplication = base.mApplication;
        mPackageManager = base.mPackageManager;
        mResolver = base.mResolver;
        mOuterContext = outer;
    }

    /** framework-internal. Set before providers and Application.onCreate. */
    void setApplication(Application application) {
        mApplication = application;
        mOuterContext = application;
    }

    /** framework-internal. The base context of an activity or service. */
    ContextImpl createComponentContext(Context outer) { return new ContextImpl(this, outer); }

    final Context getOuterContext() { return mOuterContext != null ? mOuterContext : this; }

    /** framework-internal. The context manifest receivers get: no registering or binding. */
    Context getReceiverRestrictedContext() {
        if (mReceiverRestrictedContext == null) mReceiverRestrictedContext = new ReceiverRestrictedContext(getOuterContext());
        return mReceiverRestrictedContext;
    }

    /** framework-internal. After onDestroy: drop the receivers and connections the component leaked. */
    void scheduleFinalCleanup(final String who, final String what) {
        final Context outer = getOuterContext();
        ActivityThread.post(new Runnable() {
            public void run() {
                BroadcastQueue.removeContextRegistrations(outer, who, what);
                ActiveServices.removeContextRegistrations(outer, who, what);
            }
        });
    }

    @Override public AssetManager getAssets() { return mResources.getAssets(); }
    @Override public Resources getResources() { return mResources; }
    @Override public Looper getMainLooper() { return Looper.getMainLooper(); }
    @Override public ClassLoader getClassLoader() { return ClassLoader.getSystemClassLoader(); }
    @Override public String getPackageName() { return mPackageName; }
    @Override public ApplicationInfo getApplicationInfo() { return mInfo; }
    @Override public String getPackageResourcePath() { return mApkPath; }
    @Override public String getPackageCodePath() { return mApkPath; }

    @Override
    public Context getApplicationContext() { return mApplication != null ? mApplication : this; }

    @Override
    public PackageManager getPackageManager() {
        if (mPackageManager == null) mPackageManager = new ApplicationPackageManager();
        return mPackageManager;
    }

    @Override
    public ContentResolver getContentResolver() {
        if (mResolver == null) mResolver = new ContentResolver(this) {};
        return mResolver;
    }

    @Override
    public void setTheme(int resid) {
        mThemeRes = resid;
        if (mTheme != null && resid != 0) mTheme.applyStyle(resid, true);
    }

    @Override
    public Resources.Theme getTheme() {
        if (mTheme == null) {
            mTheme = mResources.newTheme();
            int resid = mThemeRes;
            if (resid == 0 && mInfo != null) resid = mInfo.theme;
            if (mInfo != null) resid = Resources.selectDefaultTheme(resid, mInfo.targetSdkVersion);
            if (resid != 0) mTheme.applyStyle(resid, true);
        }
        return mTheme;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        synchronized (mPrefs) {
            SharedPreferences sp = mPrefs.get(name);
            if (sp == null) {
                File dir = new File(getDataDir(), "shared_prefs");
                if (!dir.exists()) dir.mkdirs();
                sp = new android.content.SharedPreferencesImpl(new File(dir, name + ".xml"));
                mPrefs.put(name, sp);
            }
            return sp;
        }
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) { return false; }

    @Override
    public boolean deleteSharedPreferences(String name) {
        synchronized (mPrefs) { mPrefs.remove(name); }
        File f = new File(getDataDir(), "shared_prefs/" + name + ".xml");
        boolean deleted = f.delete();
        deleted |= new File(f.getPath() + ".bak").delete();
        return deleted;
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return new FileInputStream(makeFilename(getFilesDir(), name));
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        boolean append = (mode & MODE_APPEND) != 0;
        return new FileOutputStream(makeFilename(getFilesDir(), name), append);
    }

    @Override public boolean deleteFile(String name) { return makeFilename(getFilesDir(), name).delete(); }
    @Override public File getFileStreamPath(String name) { return makeFilename(getFilesDir(), name); }

    @Override public File getDataDir() { return ensure(new File("/data/data/" + mPackageName)); }
    @Override public File getFilesDir() { return ensure(new File(getDataDir(), "files")); }
    @Override public File getNoBackupFilesDir() { return ensure(new File(getDataDir(), "no_backup")); }
    @Override public File getCacheDir() { return ensure(new File(getDataDir(), "cache")); }
    @Override public File getCodeCacheDir() { return ensure(new File(getDataDir(), "code_cache")); }
    @Override public File getObbDir() { return ensure(new File(getDataDir(), "obb")); }
    @Override public File[] getObbDirs() { return new File[] { getObbDir() }; }

    @Override
    public File getExternalFilesDir(String type) {
        File base = ensure(new File(getDataDir(), "external"));
        return type == null ? base : ensure(new File(base, type));
    }

    @Override public File[] getExternalFilesDirs(String type) { return new File[] { getExternalFilesDir(type) }; }
    @Override public File getExternalCacheDir() { return ensure(new File(getDataDir(), "external-cache")); }
    @Override public File[] getExternalCacheDirs() { return new File[] { getExternalCacheDir() }; }
    @Override
    public File[] getExternalMediaDirs() { return new File[] { ensure(new File(getDataDir(), "external-media")) }; }

    @Override
    public String[] fileList() {
        String[] names = getFilesDir().list();
        return names != null ? names : new String[0];
    }

    @Override public File getDir(String name, int mode) { return ensure(new File(getDataDir(), "app_" + name)); }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return openOrCreateDatabase(name, mode, factory, null);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
            DatabaseErrorHandler errorHandler) {
        int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
        if ((mode & MODE_ENABLE_WRITE_AHEAD_LOGGING) != 0) flags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
        if ((mode & MODE_NO_LOCALIZED_COLLATORS) != 0) flags |= SQLiteDatabase.NO_LOCALIZED_COLLATORS;
        return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory, flags, errorHandler);
    }

    @Override public boolean moveDatabaseFrom(Context sourceContext, String name) { return false; }

    @Override public boolean deleteDatabase(String name) { return getDatabasePath(name).delete(); }

    @Override
    public File getDatabasePath(String name) {
        return new File(ensure(new File(getDataDir(), "databases")), name);
    }

    @Override
    public String[] databaseList() {
        String[] names = new File(getDataDir(), "databases").list();
        return names != null ? names : new String[0];
    }

    @Override public Drawable getWallpaper() { return null; }
    @Override public Drawable peekWallpaper() { return null; }
    @Override public int getWallpaperDesiredMinimumWidth() { return 0; }
    @Override public int getWallpaperDesiredMinimumHeight() { return 0; }
    @Override public void setWallpaper(Bitmap bitmap) throws IOException {}
    @Override public void setWallpaper(InputStream data) throws IOException {}
    @Override public void clearWallpaper() throws IOException {}

    @Override public void startActivity(Intent intent) { startActivity(intent, null); }

    @Override
    public void startActivity(Intent intent, Bundle options) { ActivityThread.startActivity(null, intent, -1); }

    @Override public void sendBroadcast(Intent intent) { BroadcastQueue.send(intent, false, null, null, Activity.RESULT_OK, null, null); }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission) {
        BroadcastQueue.send(intent, false, null, null, Activity.RESULT_OK, null, null);
    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission) {
        BroadcastQueue.send(intent, true, null, null, Activity.RESULT_OK, null, null);
    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, BroadcastReceiver resultReceiver,
            Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
        BroadcastQueue.send(intent, true, resultReceiver, scheduler, initialCode, initialData, initialExtras);
    }

    @Override public void sendStickyBroadcast(Intent intent) { BroadcastQueue.sendSticky(intent); }
    @Override public void removeStickyBroadcast(Intent intent) { BroadcastQueue.removeSticky(intent); }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver, Handler scheduler,
            int initialCode, String initialData, Bundle initialExtras) {
        BroadcastQueue.addSticky(intent);
        BroadcastQueue.send(intent, true, resultReceiver, scheduler, initialCode, initialData, initialExtras);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return registerReceiver(receiver, filter, null, null, 0);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return registerReceiver(receiver, filter, null, null, flags);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission,
            Handler scheduler) {
        return registerReceiver(receiver, filter, broadcastPermission, scheduler, 0);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission,
            Handler scheduler, int flags) {
        return BroadcastQueue.register(getOuterContext(), receiver, filter, scheduler, flags);
    }

    @Override public void unregisterReceiver(BroadcastReceiver receiver) { BroadcastQueue.unregister(getOuterContext(), receiver); }

    @Override public ComponentName startService(Intent service) { return ActiveServices.startService(service); }
    @Override public ComponentName startForegroundService(Intent service) { return ActiveServices.startService(service); }
    @Override public boolean stopService(Intent service) { return ActiveServices.stopService(service); }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return ActiveServices.bindService(getOuterContext(), service, conn, flags, null);
    }

    @Override
    public boolean bindService(Intent service, int flags, java.util.concurrent.Executor executor, ServiceConnection conn) {
        if (executor == null) throw new IllegalArgumentException("executor must not be null");
        return ActiveServices.bindService(getOuterContext(), service, conn, flags, executor);
    }

    @Override public void unbindService(ServiceConnection conn) { ActiveServices.unbindService(getOuterContext(), conn); }

    @Override
    public boolean startInstrumentation(ComponentName className, String profileFile, Bundle arguments) {
        logIpc();
        return false;
    }

    @Override
    public Object getSystemService(String name) {
        if (WINDOW_SERVICE.equals(name)) return WindowManagerImpl.getDefault();
        if (ALARM_SERVICE.equals(name)) return AlarmManager.getInstance();
        if (NOTIFICATION_SERVICE.equals(name)) return NotificationManager.getInstance();
        if (JOB_SCHEDULER_SERVICE.equals(name)) {
            return android.app.job.JobSchedulerImpl.getInstance(mApplication != null ? mApplication : this);
        }
        if (VIBRATOR_SERVICE.equals(name)) return new Vibrator.SystemVibrator();
        if (VIBRATOR_MANAGER_SERVICE.equals(name)) return new android.os.VibratorManager.SystemVibratorManager();
        if (SENSOR_SERVICE.equals(name)) return android.hardware.SystemSensorManager.getInstance();
        if (BATTERY_SERVICE.equals(name)) return new android.os.BatteryManager();
        if (LOCATION_SERVICE.equals(name)) {
            if (sLocationManager == null) sLocationManager = new android.location.LocationManager();
            return sLocationManager;
        }
        if (TELEPHONY_SERVICE.equals(name)) return new android.telephony.TelephonyManager();
        if (CAMERA_SERVICE.equals(name)) return new android.hardware.camera2.CameraManager();
        if (POWER_SERVICE.equals(name)) {
            if (sPowerManager == null) sPowerManager = new android.os.PowerManager();
            return sPowerManager;
        }
        if (INPUT_METHOD_SERVICE.equals(name)) return InputMethodManager.systemInstance();
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            if (mLayoutInflater == null) mLayoutInflater = new com.android.internal.policy.PhoneLayoutInflater(this);
            return mLayoutInflater;
        }
        if (ACCESSIBILITY_SERVICE.equals(name)) {
            return android.view.accessibility.AccessibilityManager.getInstance(this);
        }
        if (SEARCH_SERVICE.equals(name)) {
            if (mSearchManager == null) mSearchManager = new SearchManager(getOuterContext(), null);
            return mSearchManager;
        }
        if (AUDIO_SERVICE.equals(name)) return AudioManager.getInstance();
        if (CONNECTIVITY_SERVICE.equals(name)) return android.net.ConnectivityManager.from(this);
        if (CLIPBOARD_SERVICE.equals(name)) {
            if (sClipboard == null) sClipboard = new ClipboardManager();
            return sClipboard;
        }
        return null;
    }

    private static ClipboardManager sClipboard;
    private static android.os.PowerManager sPowerManager;
    private static android.location.LocationManager sLocationManager;
    private android.view.LayoutInflater mLayoutInflater;
    private SearchManager mSearchManager;

    @Override
    public String getSystemServiceName(Class<?> serviceClass) {
        if (serviceClass == WindowManager.class) return WINDOW_SERVICE;
        if (serviceClass == AlarmManager.class) return ALARM_SERVICE;
        if (serviceClass == NotificationManager.class) return NOTIFICATION_SERVICE;
        if (serviceClass == android.app.job.JobScheduler.class) return JOB_SCHEDULER_SERVICE;
        if (serviceClass == Vibrator.class) return VIBRATOR_SERVICE;
        if (serviceClass == android.os.VibratorManager.class) return VIBRATOR_MANAGER_SERVICE;
        if (serviceClass == android.hardware.SensorManager.class) return SENSOR_SERVICE;
        if (serviceClass == android.os.BatteryManager.class) return BATTERY_SERVICE;
        if (serviceClass == android.os.PowerManager.class) return POWER_SERVICE;
        if (serviceClass == android.location.LocationManager.class) return LOCATION_SERVICE;
        if (serviceClass == android.telephony.TelephonyManager.class) return TELEPHONY_SERVICE;
        if (serviceClass == android.hardware.camera2.CameraManager.class) return CAMERA_SERVICE;
        if (serviceClass == InputMethodManager.class) return INPUT_METHOD_SERVICE;
        if (serviceClass == android.view.LayoutInflater.class) return LAYOUT_INFLATER_SERVICE;
        if (serviceClass == android.view.accessibility.AccessibilityManager.class) return ACCESSIBILITY_SERVICE;
        if (serviceClass == SearchManager.class) return SEARCH_SERVICE;
        if (serviceClass == AudioManager.class) return AUDIO_SERVICE;
        if (serviceClass == android.net.ConnectivityManager.class) return CONNECTIVITY_SERVICE;
        if (serviceClass == ClipboardManager.class) return CLIPBOARD_SERVICE;
        return null;
    }

    @Override
    public int checkPermission(String permission, int pid, int uid) { return PackageManager.PERMISSION_GRANTED; }
    @Override public int checkCallingPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }
    @Override
    public int checkCallingOrSelfPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }
    @Override public int checkSelfPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }

    @Override
    public void enforcePermission(String permission, int pid, int uid, String message) {
        if (checkPermission(permission, pid, uid) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(message != null ? message : permission);
        }
    }

    @Override
    public void enforceCallingPermission(String permission, String message) {
        if (checkCallingPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(message != null ? message : permission);
        }
    }

    @Override
    public void enforceCallingOrSelfPermission(String permission, String message) {
        if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(message != null ? message : permission);
        }
    }

    @Override public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {}
    @Override public void revokeUriPermission(Uri uri, int modeFlags) {}
    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }
    @Override
    public int checkCallingUriPermission(Uri uri, int modeFlags) { return PackageManager.PERMISSION_GRANTED; }
    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) { return PackageManager.PERMISSION_GRANTED; }

    @Override
    public int checkUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid,
            int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {}
    @Override public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {}
    @Override public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {}

    @Override
    public void enforceUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid,
            int modeFlags, String message) {}

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        if (packageName != null && packageName.equals(mPackageName)) return this;
        throw new PackageManager.NameNotFoundException(packageName);
    }

    @Override
    public Context createContextForSplit(String splitName) throws PackageManager.NameNotFoundException { return this; }
    @Override public Context createConfigurationContext(Configuration overrideConfiguration) { return this; }
    @Override public Context createDisplayContext(Display display) { return this; }
    @Override public Context createDeviceProtectedStorageContext() { return this; }
    @Override public boolean isDeviceProtectedStorage() { return false; }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (callback != null) mCallbacks.add(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) { mCallbacks.remove(callback); }

    private static File ensure(File dir) {
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File makeFilename(File base, String name) {
        if (name.indexOf(File.separatorChar) >= 0) {
            throw new IllegalArgumentException("File " + name + " contains a path separator");
        }
        return new File(base, name);
    }

    /** Instrumentation runs no tests here. */
    private static void logIpc() {
        if (!sLoggedIpc) {
            sLoggedIpc = true;
            Log.w(TAG, "instrumentation is not supported");
        }
    }
}
