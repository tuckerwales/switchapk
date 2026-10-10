package android.app;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.PlatformInput;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerImpl;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;

/**
 * framework-internal. Boots one APK on the main looper: manifest, application,
 * providers, then the launcher activity.
 */
public final class ActivityThread {
    private static final String TAG = "ActivityThread";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    static String sPackageName;
    static String sApkPath;
    static String sAppClass;
    static ApplicationInfo sAppInfo;
    static Application sApplication;
    static ContextImpl sContext;
    static Resources sResources;

    static final ArrayList<ParsedActivity> sActivities = new ArrayList<ParsedActivity>();
    static final ArrayList<ParsedActivity> sReceivers = new ArrayList<ParsedActivity>();
    static final ArrayList<ParsedService> sServices = new ArrayList<ParsedService>();
    static final ArrayList<ProviderInfo> sProviders = new ArrayList<ProviderInfo>();
    private static final ArrayList<ActivityRecord> sStack = new ArrayList<ActivityRecord>();
    private static boolean sShutdown;
    private static Handler sHandler;

    private ActivityThread() {}

    public static void main(String[] args) {
        Looper.prepareMainLooper();
        sHandler = new Handler(Looper.getMainLooper());
        MessageQueue.setPlatformDispatcher(new Runnable() {
            public void run() { dispatchPlatform(); }
        });
        String apk = args != null && args.length > 0 && args[0] != null ? args[0] : "";
        try {
            boot(apk);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start " + apk, e);
            System.exit(1);
        }
        Looper.loop();
    }

    /** framework-internal. Pause, stop and destroy, then leave the process. */
    public static void shutdown() {
        if (sShutdown) return;
        sShutdown = true;
        for (int i = sStack.size() - 1; i >= 0; i--) {
            ActivityRecord rec = sStack.get(i);
            try {
                destroyRecord(rec);
            } catch (RuntimeException e) {
                Log.e(TAG, "Error destroying " + rec.parsed.info.name, e);
            }
        }
        sStack.clear();
        System.exit(0);
    }

    // ---------------------------------------------------------------- activity stack
    //
    // One task, one stack. Requests from app code (start, finish, recreate) are
    // posted to the main looper like the binder calls they replace, so they never
    // run inside another activity's lifecycle callback. Ordering follows AOSP:
    // launching pauses the previous activity, creates, starts and resumes the new
    // one, then stops the previous one and saves its state (P+ order); finishing
    // pauses, resumes the activity below (results delivered just before its
    // onResume), then stops and destroys the finished one.

    static void startActivity(Activity caller, Intent intent, int requestCode) {
        startActivity(caller, intent, requestCode, null);
    }

    static void startActivity(Activity caller, Intent intent, int requestCode, final String resultWho) {
        if (sShutdown) return;
        if (intent == null) throw new IllegalArgumentException("intent is null");
        final ParsedActivity parsed = resolveActivity(intent);
        if (parsed == null) {
            if (intent.getComponent() != null) {
                throw new android.content.ActivityNotFoundException("Unable to find explicit activity class "
                        + intent.getComponent().toShortString() + "; have you declared this activity in your AndroidManifest.xml?");
            }
            // Implicit intents for other apps (browser, mail, settings) have no handler here.
            Log.w(TAG, "No activity for " + intent);
            return;
        }
        final ActivityRecord callerRec = caller != null ? findRecord(caller) : null;
        final Intent copy = new Intent(intent);
        if (copy.getComponent() == null) copy.setComponent(new ComponentName(parsed.info.packageName, parsed.info.name));
        final int code = callerRec != null ? requestCode : -1;
        post(new Runnable() {
            public void run() { handleStartActivity(callerRec, parsed, copy, code, resultWho); }
        });
    }

    static void finishActivity(Activity activity, final int resultCode, final Intent data) {
        if (sShutdown || activity == null) return;
        final ActivityRecord rec = findRecord(activity);
        if (rec == null) return;
        post(new Runnable() {
            public void run() { handleFinish(rec, resultCode, data); }
        });
    }

    /** Activity.finishActivity(int): finishes activities this one started with requestCode. */
    static void finishActivityForRequest(Activity caller, int requestCode) {
        ActivityRecord callerRec = findRecord(caller);
        if (callerRec == null) return;
        for (int i = sStack.size() - 1; i >= 0; i--) {
            ActivityRecord rec = sStack.get(i);
            if (rec.caller == callerRec && rec.requestCode == requestCode && rec.resultWho == null) {
                rec.activity.mFinished = true;
                finishActivity(rec.activity, Activity.RESULT_CANCELED, null);
            }
        }
    }

    /** Activity.finishAffinity: this activity and everything below it (one task, one affinity). */
    static void finishAffinity(Activity activity) {
        final ActivityRecord rec = findRecord(activity);
        if (rec == null) return;
        if (rec.caller != null && rec.requestCode >= 0) {
            throw new IllegalStateException("Can not be called to deliver a result");
        }
        for (int i = sStack.indexOf(rec); i >= 0; i--) {
            Activity a = sStack.get(i).activity;
            if (a.mFinished) continue;
            a.mFinished = true;
            finishActivity(a, Activity.RESULT_CANCELED, null);
        }
    }

    static boolean isTaskRoot(Activity activity) {
        return !sStack.isEmpty() && sStack.get(0).activity == activity;
    }

    static String getCallingPackage(Activity activity) {
        ActivityRecord rec = findRecord(activity);
        if (rec == null || rec.caller == null || rec.requestCode < 0) return null;
        return rec.caller.parsed.info.packageName;
    }

    static ComponentName getCallingActivity(Activity activity) {
        ActivityRecord rec = findRecord(activity);
        if (rec == null || rec.caller == null || rec.requestCode < 0) return null;
        return new ComponentName(rec.caller.parsed.info.packageName, rec.caller.parsed.info.name);
    }

    static void recreateActivity(Activity activity) {
        final ActivityRecord rec = findRecord(activity);
        if (rec == null) return;
        post(new Runnable() {
            public void run() {
                if (!sStack.contains(rec) || rec.activity.isFinishing()) return;
                relaunch(rec, 0, rec.activity.mResumed || top() == rec);
            }
        });
    }

    /** Activity.navigateUpTo: back to the parent if it is in the stack, finishing everything above it. */
    static boolean navigateUpTo(Activity activity, Intent upIntent) {
        final ActivityRecord rec = findRecord(activity);
        if (rec == null || upIntent == null || upIntent.getComponent() == null) return false;
        ActivityRecord parent = null;
        for (int i = sStack.indexOf(rec) - 1; i >= 0; i--) {
            if (sameComponent(sStack.get(i).parsed.info, upIntent.getComponent())) {
                parent = sStack.get(i);
                break;
            }
        }
        if (parent == null) return false;
        final ActivityRecord target = parent;
        final Intent intent = new Intent(upIntent);
        final int resultCode;
        final Intent resultData;
        synchronized (activity) {
            resultCode = activity.mResultCode;
            resultData = activity.mResultData;
        }
        activity.mFinished = true;
        post(new Runnable() {
            public void run() {
                if (!sStack.contains(target)) return;
                boolean reuse = target.parsed.info.launchMode != ActivityInfo.LAUNCH_MULTIPLE
                        || (intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0;
                if (reuse) {
                    clearAbove(target, rec, resultCode, resultData, intent);
                    return;
                }
                // Standard launch mode: the parent is replaced by a new instance (AOSP).
                ArrayList<ActivityRecord> removed = popTo(target, true, rec, resultCode, resultData);
                launch(sStack.contains(target.caller) ? target.caller : null, target.parsed, intent, -1, null, null);
                for (int i = 0; i < removed.size(); i++) destroyRecord(removed.get(i));
            }
        });
        return true;
    }

    private static void handleStartActivity(ActivityRecord caller, ParsedActivity parsed, Intent intent,
            int requestCode, String resultWho) {
        if (sShutdown) return;
        if (caller != null && !sStack.contains(caller)) caller = null;
        int flags = intent.getFlags();
        int launchMode = parsed.info.launchMode;
        boolean singleTop = launchMode == ActivityInfo.LAUNCH_SINGLE_TOP
                || (flags & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0;
        boolean singleTask = launchMode == ActivityInfo.LAUNCH_SINGLE_TASK
                || launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE;
        if ((flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0 && (flags & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0
                && !sStack.isEmpty()) {
            // The new activity becomes the only one: pause the top, launch, then destroy the rest.
            ArrayList<ActivityRecord> old = new ArrayList<ActivityRecord>(sStack);
            ActivityRecord prev = top();
            if (prev != null) pauseRecord(prev);
            sStack.clear();
            launch(null, parsed, intent, -1, null, null);
            for (int i = old.size() - 1; i >= 0; i--) {
                old.get(i).activity.mFinished = true;
                destroyRecord(old.get(i));
            }
            return;
        }
        ActivityRecord existing = null;
        if ((flags & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0 || singleTask) {
            for (int i = sStack.size() - 1; i >= 0; i--) {
                if (sStack.get(i).parsed == parsed && !sStack.get(i).activity.isFinishing()) {
                    existing = sStack.get(i);
                    break;
                }
            }
        }
        if (existing != null) {
            if (singleTop || singleTask) {
                if (requestCode >= 0 && caller != null) sendResult(caller, resultWho, requestCode, Activity.RESULT_CANCELED, null);
                if (existing == top()) newIntent(existing, intent);
                else clearAbove(existing, null, 0, null, intent);
                return;
            }
            // CLEAR_TOP on a standard activity: it is finished too and launched again.
            ArrayList<ActivityRecord> removed = popTo(existing, true, null, 0, null);
            launch(caller != null && sStack.contains(caller) ? caller : null, parsed, intent, requestCode, resultWho, null);
            for (int i = 0; i < removed.size(); i++) destroyRecord(removed.get(i));
            return;
        }
        ActivityRecord top = top();
        if (singleTop && top != null && top.parsed == parsed && !top.activity.isFinishing()) {
            if (requestCode >= 0 && caller != null) sendResult(caller, resultWho, requestCode, Activity.RESULT_CANCELED, null);
            newIntent(top, intent);
            return;
        }
        launch(caller, parsed, intent, requestCode, resultWho, top);
    }

    /** Creates, starts and resumes a new activity on top; prev (the old top) is paused first and stopped after. */
    private static void launch(ActivityRecord caller, ParsedActivity parsed, Intent intent, int requestCode,
            String resultWho, ActivityRecord prev) {
        if (prev != null) {
            if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NO_USER_ACTION) == 0 && prev.activity.mResumed) {
                prev.activity.performUserLeaving();
            }
            pauseRecord(prev);
        }
        ActivityRecord rec = new ActivityRecord();
        rec.parsed = parsed;
        rec.intent = intent;
        rec.caller = caller;
        rec.requestCode = requestCode;
        rec.resultWho = resultWho;
        sStack.add(rec);
        if (!createRecord(rec, null, null)) return;
        Activity a = rec.activity;
        a.performStart();
        rec.started = true;
        a.performPostCreate(null);
        if (top() != rec || a.isFinishing()) return;
        a.performResume();
        show(rec);
        if (prev != null && sStack.contains(prev) && !isTranslucent(rec)) stopRecord(prev, true);
    }

    /** New instance, attach, onCreate. False if it finished (or threw away its record) during onCreate. */
    private static boolean createRecord(ActivityRecord rec, Bundle state, Activity.NonConfigurationInstances nci) {
        ActivityInfo info = rec.parsed.info;
        Object obj = newComponent(info.name);
        if (!(obj instanceof Activity)) throw new ClassCastException(info.name + " is not an Activity");
        Activity a = (Activity) obj;
        rec.activity = a;
        rec.started = false;
        rec.relaunchPending = false;
        rec.pendingConfigChanges = 0;
        a.attach(sContext.createComponentContext(a), info, rec.intent, sApplication, nci, sResources.getConfiguration());
        a.performCreate(state);
        return !a.isFinishing();
    }

    private static void handleFinish(ActivityRecord rec, int resultCode, Intent data) {
        if (sShutdown || !sStack.contains(rec)) return;
        boolean wasTop = top() == rec;
        pauseRecord(rec);
        removeRecord(rec, resultCode, data);
        if (sStack.isEmpty()) {
            destroyRecord(rec);
            shutdown();
            return;
        }
        if (wasTop) resumeTop();
        destroyRecord(rec);
    }

    /** Takes rec off the stack and queues its result for the caller. */
    private static void removeRecord(ActivityRecord rec, int resultCode, Intent data) {
        sStack.remove(rec);
        rec.activity.mFinished = true;
        sendResult(rec.caller, rec.resultWho, rec.requestCode, resultCode, data);
    }

    /**
     * Pauses the top and takes records off the stack down to target (inclusive or not).
     * finishing reports resultCode/resultData, the others RESULT_CANCELED, as AOSP's
     * clear-top does. The caller destroys the returned records once the new top is resumed.
     */
    private static ArrayList<ActivityRecord> popTo(ActivityRecord target, boolean inclusive, ActivityRecord finishing,
            int resultCode, Intent resultData) {
        pauseRecord(top());
        ArrayList<ActivityRecord> removed = new ArrayList<ActivityRecord>();
        while (!sStack.isEmpty()) {
            ActivityRecord r = top();
            if (r == target && !inclusive) break;
            if (r == finishing) removeRecord(r, resultCode, resultData);
            else removeRecord(r, Activity.RESULT_CANCELED, null);
            removed.add(r);
            if (r == target) break;
        }
        return removed;
    }

    /** Finishes everything above target, then brings target back, delivering newIntent if given. */
    private static void clearAbove(ActivityRecord target, ActivityRecord finishing, int resultCode, Intent resultData,
            Intent newIntent) {
        ArrayList<ActivityRecord> removed = popTo(target, false, finishing, resultCode, resultData);
        if (newIntent != null) newIntent(target, newIntent);
        else resumeTop();
        for (int i = 0; i < removed.size(); i++) destroyRecord(removed.get(i));
    }

    /** onNewIntent for an existing activity, paused around the call as AOSP does. */
    private static void newIntent(ActivityRecord rec, Intent intent) {
        if (rec.relaunchPending) relaunch(rec, rec.pendingConfigChanges, false);
        Activity a = rec.activity;
        if (a.mResumed) a.performPause();
        startRecord(rec);
        a.performNewIntent(intent);
        deliverResults(rec);
        if (top() == rec && !a.isFinishing()) {
            a.performResume();
            show(rec);
        }
    }

    /** The top activity comes back: onRestart/onStart, pending results, onResume. */
    private static void resumeTop() {
        ActivityRecord rec = top();
        if (rec == null || rec.activity.isFinishing()) return;
        if (rec.relaunchPending) {
            relaunch(rec, rec.pendingConfigChanges, true);
            return;
        }
        Activity a = rec.activity;
        startRecord(rec);
        deliverResults(rec);
        if (top() != rec || a.isFinishing() || a.mResumed) return;
        a.performResume();
        show(rec);
    }

    private static void sendResult(ActivityRecord caller, String who, int requestCode, int resultCode, Intent data) {
        if (caller == null || requestCode < 0 || !sStack.contains(caller)) return;
        ResultInfo result = new ResultInfo(who, requestCode, resultCode, data);
        Activity a = caller.activity;
        if (a.mResumed && !caller.relaunchPending) {
            a.performPause();
            a.dispatchActivityResult(who, requestCode, resultCode, data);
            a.performResume();
        } else {
            caller.pendingResults.add(result);
        }
    }

    /** A createPendingResult PendingIntent was sent: deliver like a result, on the main thread. */
    static void sendPendingResult(final Activity activity, final int requestCode, final int resultCode,
            final Intent data) {
        post(new Runnable() {
            public void run() {
                ActivityRecord rec = findRecord(activity);
                if (rec != null && !activity.isFinishing()) sendResult(rec, null, requestCode, resultCode, data);
            }
        });
    }

    /** framework-internal. BroadcastReceiver.peekService. */
    public static android.os.IBinder peekService(Intent service) { return ActiveServices.peekService(service); }

    private static void deliverResults(ActivityRecord rec) {
        while (!rec.pendingResults.isEmpty()) {
            ResultInfo r = rec.pendingResults.remove(0);
            rec.activity.dispatchActivityResult(r.who, r.requestCode, r.resultCode, r.data);
        }
    }

    /** onRestart (if it was stopped) and onStart. */
    private static void startRecord(ActivityRecord rec) {
        if (rec.started) return;
        Activity a = rec.activity;
        if (a.isStopped()) a.performRestart();
        else a.performStart();
        rec.started = true;
    }

    private static void pauseRecord(ActivityRecord rec) {
        if (rec != null && rec.activity != null && rec.activity.mResumed) rec.activity.performPause();
    }

    /** onStop, hide the window, then onSaveInstanceState unless finishing (API 28+ order). */
    private static void stopRecord(ActivityRecord rec, boolean saveState) {
        Activity a = rec.activity;
        pauseRecord(rec);
        if (!rec.started) return;
        a.performStop();
        rec.started = false;
        hide(rec);
        if (saveState && !a.isFinishing()) {
            Bundle state = new Bundle();
            a.performSaveInstanceState(state);
            rec.state = state;
        }
    }

    private static void destroyRecord(ActivityRecord rec) {
        Activity a = rec.activity;
        if (a == null || a.isDestroyed()) return;
        stopRecord(rec, false);
        a.performDestroy();
        a.removeWindow();
        WindowManagerGlobal.getInstance().closeAll(a);
        if (a.getBaseContext() instanceof ContextImpl) {
            ((ContextImpl) a.getBaseContext()).scheduleFinalCleanup(a.getClass().getName(), "Activity");
        }
    }

    private static void show(ActivityRecord rec) {
        Activity a = rec.activity;
        a.mVisibleFromServer = true;
        if (!a.mWindowAdded) {
            if (a.mVisibleFromClient) a.makeVisible();
        } else {
            a.updateVisibility(true);
        }
    }

    private static void hide(ActivityRecord rec) {
        rec.activity.mVisibleFromServer = false;
        rec.activity.updateVisibility(false);
    }

    /** Dialog-themed and translucent activities leave the one below visible (paused, not stopped). */
    private static boolean isTranslucent(ActivityRecord rec) {
        TypedArray a = rec.activity.getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.windowIsFloating, android.R.attr.windowIsTranslucent });
        try {
            return a.getBoolean(0, false) || a.getBoolean(1, false);
        } finally {
            a.recycle();
        }
    }

    /**
     * Destroys rec's activity and creates a new instance with its saved state and
     * non-config instances (configuration change or recreate()).
     */
    private static void relaunch(ActivityRecord rec, int configChanges, boolean resume) {
        Activity old = rec.activity;
        boolean visible = rec.started;
        old.mConfigChangeFlags |= configChanges;
        old.mChangingConfigurations = true;
        pauseRecord(rec);
        Bundle state = rec.state;
        if (rec.started) {
            old.performStop();
            rec.started = false;
            state = new Bundle();
            old.performSaveInstanceState(state);
        }
        Activity.NonConfigurationInstances nci = old.retainNonConfigurationInstances();
        old.performDestroy();
        old.removeWindow();
        WindowManagerGlobal.getInstance().closeAll(old);
        rec.state = null;
        if (!createRecord(rec, state, nci)) return;
        Activity a = rec.activity;
        a.performStart();
        rec.started = true;
        if (state != null) a.performRestoreInstanceState(state);
        a.performPostCreate(state);
        deliverResults(rec);
        if (resume && top() == rec && !a.isFinishing()) {
            a.performResume();
            show(rec);
        } else if (visible || resume) {
            show(rec);
        }
    }

    /** The display changed (docked/handheld): new configuration, then handle or relaunch each activity. */
    private static void handleDisplayChanged() {
        Configuration oldConfig = new Configuration(sResources.getConfiguration());
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration newConfig = computeConfiguration(metrics);
        newConfig.fontScale = oldConfig.fontScale;
        newConfig.uiMode = oldConfig.uiMode;
        newConfig.setLocales(oldConfig.getLocales());
        int diff = oldConfig.diff(newConfig);
        sResources.updateConfiguration(newConfig, metrics);
        WindowManagerGlobal.getInstance().onDisplayChanged();
        if (diff == 0) return;
        Configuration config = new Configuration(sResources.getConfiguration());
        sApplication.onConfigurationChanged(new Configuration(config));
        ActiveServices.dispatchConfigurationChanged(config);
        ArrayList<ActivityRecord> stack = new ArrayList<ActivityRecord>(sStack);
        for (int i = stack.size() - 1; i >= 0; i--) {
            ActivityRecord rec = stack.get(i);
            if (!sStack.contains(rec) || rec.activity.isFinishing()) continue;
            int changes = diff | rec.pendingConfigChanges;
            if ((changes & ~activityConfigChanges(rec.parsed.info)) == 0 && !rec.relaunchPending) {
                rec.activity.performConfigurationChanged(new Configuration(config));
            } else if (rec == top()) {
                relaunch(rec, changes, rec.activity.mResumed);
            } else if (rec.started) {
                relaunch(rec, changes, false);
            } else {
                rec.relaunchPending = true;
                rec.pendingConfigChanges = changes;
            }
        }
    }

    /**
     * android:configChanges as AOSP applies it: screenSize and smallestScreenSize are
     * implied handled for apps targeting below 13, as is everything below 4.
     */
    private static int activityConfigChanges(ActivityInfo info) {
        int handled = info.configChanges;
        int target = sAppInfo != null ? sAppInfo.targetSdkVersion : 0;
        if (target < 13) {
            handled |= ActivityInfo.CONFIG_SCREEN_SIZE | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;
        }
        if (target < 4) handled |= ActivityInfo.CONFIG_SCREEN_LAYOUT;
        return handled;
    }

    static void post(Runnable r) {
        if (sHandler == null) sHandler = new Handler(Looper.getMainLooper());
        sHandler.post(r);
    }

    static boolean sameComponent(ComponentInfo info, ComponentName component) {
        if (info == null || component == null) return false;
        String cls = qualify(sPackageName, component.getClassName());
        return info.name != null && info.name.equals(cls) && component.getPackageName().equals(info.packageName);
    }

    static boolean matches(ParsedActivity parsed, Intent intent) {
        if (parsed == null || intent == null) return false;
        ComponentName component = intent.getComponent();
        if (component != null) return sameComponent(parsed.info, component);
        if (intent.getPackage() != null && !intent.getPackage().equals(parsed.info.packageName)) return false;
        for (int i = 0; i < parsed.filters.size(); i++) {
            if (filterMatches(parsed.filters.get(i), intent)) return true;
        }
        return false;
    }

    static boolean matchesService(ParsedService parsed, Intent intent) {
        if (parsed == null || intent == null) return false;
        ComponentName component = intent.getComponent();
        if (component != null) return sameComponent(parsed.info, component);
        if (intent.getPackage() != null && !intent.getPackage().equals(parsed.info.packageName)) return false;
        for (int i = 0; i < parsed.filters.size(); i++) {
            if (filterMatches(parsed.filters.get(i), intent)) return true;
        }
        return false;
    }

    private static void boot(String apk) throws Exception {
        sApkPath = apk;
        parseManifest();
        Process.ActivityThreadHook.setProcessName(sPackageName);
        sResources = buildResources();
        Resources.setSystem(sResources);
        sContext = new ContextImpl(sPackageName, sAppInfo, sApkPath, sResources);
        android.security.keystore.AndroidKeyStoreProvider.install(sContext.getDataDir());
        String appClass = sAppClass != null ? sAppClass : "android.app.Application";
        Object appObj = newComponent(appClass);
        if (!(appObj instanceof Application)) throw new ClassCastException(appClass + " is not an Application");
        sApplication = (Application) appObj;
        sApplication.attach(sContext);
        sContext.setApplication(sApplication);
        installProviders();
        sApplication.onCreate();
        ParsedActivity launcher = findLauncher();
        if (launcher == null) throw new RuntimeException("No activity in " + sPackageName);
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setComponent(new ComponentName(launcher.info.packageName, launcher.info.name));
        handleStartActivity(null, launcher, launch, -1, null);
        Log.i(TAG, sPackageName + " " + launcher.info.name);
    }

    private static void dispatchPlatform() {
        PlatformInput.drain(new PlatformInput.Sink() {
            public void onQuit() { shutdown(); }

            public void onFocus(boolean gained) {
                ActivityRecord rec = top();
                if (rec == null || rec.activity == null || rec.activity.isFinishing() || rec.relaunchPending) return;
                if (gained && !rec.activity.mResumed) rec.activity.performResume();
                else if (!gained) pauseRecord(rec);
            }

            public void onResize(int width, int height, int dpi) { handleDisplayChanged(); }
        });
    }

    private static Resources buildResources() {
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration config = computeConfiguration(metrics);
        return new Resources(AssetManager.getSystem(), metrics, config);
    }

    /** Configuration for the current display (fills metrics); docked and handheld differ in size and density. */
    private static Configuration computeConfiguration(DisplayMetrics metrics) {
        Display display = WindowManagerImpl.getDefault().getDefaultDisplay();
        display.getMetrics(metrics);
        Configuration config = new Configuration();
        config.setToDefaults();
        config.densityDpi = metrics.densityDpi;
        config.touchscreen = Configuration.TOUCHSCREEN_FINGER;
        config.keyboard = Configuration.KEYBOARD_NOKEYS;
        config.orientation = metrics.widthPixels >= metrics.heightPixels
                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        float density = metrics.density > 0f ? metrics.density : 1f;
        config.screenWidthDp = (int) (metrics.widthPixels / density);
        config.screenHeightDp = (int) (metrics.heightPixels / density);
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        int size = Configuration.SCREENLAYOUT_SIZE_SMALL;
        if (config.smallestScreenWidthDp >= 720) size = Configuration.SCREENLAYOUT_SIZE_XLARGE;
        else if (config.smallestScreenWidthDp >= 600) size = Configuration.SCREENLAYOUT_SIZE_LARGE;
        else if (config.smallestScreenWidthDp >= 480) size = Configuration.SCREENLAYOUT_SIZE_NORMAL;
        config.screenLayout = size | Configuration.SCREENLAYOUT_LAYOUTDIR_LTR;
        config.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
        return config;
    }

    private static void parseManifest() throws Exception {
        sActivities.clear();
        sReceivers.clear();
        sServices.clear();
        sProviders.clear();
        sAppInfo = new ApplicationInfo();
        XmlResourceParser parser = AssetManager.getSystem().openXmlResourceParser("AndroidManifest.xml");
        try {
            ParsedActivity activity = null;
            ParsedService service = null;
            ProviderInfo provider = null;
            IntentFilter filter = null;
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("manifest".equals(name)) {
                        sPackageName = parser.getAttributeValue(null, "package");
                        if (sPackageName == null) sPackageName = parser.getAttributeValue("", "package");
                    } else if ("uses-sdk".equals(name)) {
                        readUsesSdk(parser);
                    } else if ("application".equals(name)) {
                        readApplication(parser);
                    } else if ("activity".equals(name) || "activity-alias".equals(name) || "receiver".equals(name)) {
                        ActivityInfo info = new ActivityInfo();
                        fillComponent(info, parser);
                        info.theme = attrRes(parser, android.R.attr.theme, "theme");
                        info.screenOrientation = attrInt(parser, android.R.attr.screenOrientation, "screenOrientation",
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        info.configChanges = attrInt(parser, android.R.attr.configChanges, "configChanges", 0);
                        info.softInputMode = attrInt(parser, android.R.attr.windowSoftInputMode,
                                "windowSoftInputMode", 0);
                        info.launchMode = attrInt(parser, android.R.attr.launchMode, "launchMode",
                                ActivityInfo.LAUNCH_MULTIPLE);
                        info.uiOptions = attrInt(parser, android.R.attr.uiOptions, "uiOptions", 0);
                        info.parentActivityName = qualify(sPackageName,
                                attrString(parser, android.R.attr.parentActivityName, "parentActivityName"));
                        activity = new ParsedActivity(info, "receiver".equals(name));
                        filter = null;
                    } else if ("service".equals(name)) {
                        ServiceInfo info = new ServiceInfo();
                        fillComponent(info, parser);
                        info.permission = attrString(parser, android.R.attr.permission, "permission");
                        service = new ParsedService(info);
                        filter = null;
                    } else if ("provider".equals(name)) {
                        provider = new ProviderInfo();
                        fillComponent(provider, parser);
                        provider.authority = attrString(parser, android.R.attr.authorities, "authorities");
                        provider.grantUriPermissions = attrBool(parser, android.R.attr.grantUriPermissions,
                                "grantUriPermissions", false);
                    } else if ("intent-filter".equals(name)) {
                        filter = new IntentFilter();
                        filter.setPriority(attrInt(parser, android.R.attr.priority, "priority", 0));
                        if (activity != null) activity.filters.add(filter);
                        else if (service != null) service.filters.add(filter);
                        else filter = null;
                    } else if ("meta-data".equals(name)) {
                        android.content.pm.PackageItemInfo owner = activity != null ? activity.info
                                : service != null ? service.info : provider != null ? provider : sAppInfo;
                        readMetaData(owner, parser);
                    } else if ("action".equals(name) && filter != null) {
                        String action = attrString(parser, android.R.attr.name, "name");
                        if (action != null) filter.addAction(action);
                    } else if ("category".equals(name) && filter != null) {
                        String category = attrString(parser, android.R.attr.name, "name");
                        if (category != null) filter.addCategory(category);
                    }
                } else if (type == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("activity".equals(name) || "activity-alias".equals(name) || "receiver".equals(name)) {
                        if (activity != null) {
                            if (activity.receiver) sReceivers.add(activity);
                            else sActivities.add(activity);
                        }
                        activity = null;
                        filter = null;
                    } else if ("service".equals(name)) {
                        if (service != null) sServices.add(service);
                        service = null;
                        filter = null;
                    } else if ("provider".equals(name)) {
                        if (provider != null) sProviders.add(provider);
                        provider = null;
                    } else if ("intent-filter".equals(name)) {
                        filter = null;
                    }
                }
            }
        } finally {
            try { parser.close(); } catch (Exception ignored) {}
        }
        if (sPackageName == null || sPackageName.isEmpty()) throw new RuntimeException("Manifest has no package");
        if (sAppInfo.targetSdkVersion == 0) sAppInfo.targetSdkVersion = Math.max(1, sAppInfo.minSdkVersion);
        sAppInfo.packageName = sPackageName;
        sAppInfo.processName = sPackageName;
        sAppInfo.className = sAppClass;
        sAppInfo.name = sAppClass;
        sAppInfo.sourceDir = sApkPath;
        sAppInfo.publicSourceDir = sApkPath;
        sAppInfo.dataDir = "/data/data/" + sPackageName;
        sAppInfo.deviceProtectedDataDir = sAppInfo.dataDir;
        // Android's layout; the native loader serves lib<name>.so under it from the APK's lib/<abi>/
        sAppInfo.nativeLibraryDir = "/data/app/" + sPackageName + "/lib/"
                + ("x86_64".equals(android.os.Build.CPU_ABI) ? "x86_64" : "arm64");
        sAppInfo.uid = Process.myUid();
        sAppInfo.enabled = true;
        sAppInfo.flags |= ApplicationInfo.FLAG_HAS_CODE;
        stamp(sAppInfo);
    }

    /** android:minSdkVersion / targetSdkVersion; a codename counts as the newest level, as on Android. */
    private static void readUsesSdk(XmlResourceParser parser) {
        int min = sdkLevel(attrString(parser, android.R.attr.minSdkVersion, "minSdkVersion"), 1);
        int target = sdkLevel(attrString(parser, android.R.attr.targetSdkVersion, "targetSdkVersion"), min);
        sAppInfo.minSdkVersion = min;
        sAppInfo.targetSdkVersion = target;
    }

    private static int sdkLevel(String value, int def) {
        if (value == null || value.isEmpty()) return def;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 10000;
        }
    }

    private static void readApplication(XmlResourceParser parser) {
        sAppClass = qualify(sPackageName, attrString(parser, android.R.attr.name, "name"));
        sAppInfo.theme = attrRes(parser, android.R.attr.theme, "theme");
        sAppInfo.icon = attrRes(parser, android.R.attr.icon, "icon");
        applyLabel(sAppInfo, parser);
    }

    /** A meta-data element, stored as PackageParser does: resource ids as ints, values by type. */
    private static void readMetaData(android.content.pm.PackageItemInfo owner, XmlResourceParser parser) {
        String key = attrString(parser, android.R.attr.name, "name");
        if (key == null) return;
        if (owner.metaData == null) owner.metaData = new Bundle();
        int n = parser.getAttributeCount();
        int valueIndex = -1;
        for (int i = 0; i < n; i++) {
            int id = parser.getAttributeNameResource(i);
            if (id == android.R.attr.resource) {
                owner.metaData.putInt(key, parser.getAttributeResourceValue(i, 0));
                return;
            }
            if (id == android.R.attr.value) valueIndex = i;
        }
        if (valueIndex < 0 || !(parser instanceof android.content.res.XmlBlock.Parser)) {
            String value = attrString(parser, android.R.attr.value, "value");
            if (value != null) owner.metaData.putString(key, value);
            return;
        }
        android.content.res.XmlBlock.Parser p = (android.content.res.XmlBlock.Parser) parser;
        int type = p.getAttributeDataType(valueIndex);
        int data = p.getAttributeData(valueIndex);
        if (type == android.util.TypedValue.TYPE_STRING) {
            owner.metaData.putString(key, p.getAttributeValue(valueIndex));
        } else if (type == android.util.TypedValue.TYPE_INT_BOOLEAN) {
            owner.metaData.putBoolean(key, data != 0);
        } else if (type >= android.util.TypedValue.TYPE_FIRST_INT && type <= android.util.TypedValue.TYPE_LAST_INT) {
            owner.metaData.putInt(key, data);
        } else if (type == android.util.TypedValue.TYPE_FLOAT) {
            owner.metaData.putFloat(key, Float.intBitsToFloat(data));
        } else if (type == android.util.TypedValue.TYPE_REFERENCE) {
            owner.metaData.putInt(key, data);
        }
    }

    private static void fillComponent(ComponentInfo info, XmlResourceParser parser) {
        info.packageName = sPackageName;
        info.name = qualify(sPackageName, attrString(parser, android.R.attr.name, "name"));
        info.icon = attrRes(parser, android.R.attr.icon, "icon");
        info.exported = attrBool(parser, android.R.attr.exported, "exported", false);
        info.enabled = true;
        applyLabel(info, parser);
    }

    private static void applyLabel(android.content.pm.PackageItemInfo info, XmlResourceParser parser) {
        int res = attrRes(parser, android.R.attr.label, "label");
        if (res != 0) {
            info.labelRes = res;
            return;
        }
        String text = attrString(parser, android.R.attr.label, "label");
        if (text != null && !text.startsWith("@")) info.nonLocalizedLabel = text;
    }

    private static void stamp(ApplicationInfo app) {
        for (int i = 0; i < sActivities.size(); i++) sActivities.get(i).info.applicationInfo = app;
        for (int i = 0; i < sReceivers.size(); i++) sReceivers.get(i).info.applicationInfo = app;
        for (int i = 0; i < sServices.size(); i++) sServices.get(i).info.applicationInfo = app;
        for (int i = 0; i < sProviders.size(); i++) sProviders.get(i).applicationInfo = app;
    }

    private static void installProviders() {
        android.provider.FrameworkProviders.install(sContext);
        for (int i = 0; i < sProviders.size(); i++) {
            ProviderInfo info = sProviders.get(i);
            if (info.name == null) continue;
            Object obj = newComponent(info.name);
            if (!(obj instanceof ContentProvider)) {
                Log.e(TAG, "Not a provider: " + info.name);
                continue;
            }
            ContentProvider provider = (ContentProvider) obj;
            provider.attachInfo(sContext, info);
            if (info.authority != null) ContentResolver.installProvider(info.authority, provider);
        }
    }

    private static ParsedActivity findLauncher() {
        for (int i = 0; i < sActivities.size(); i++) {
            ParsedActivity parsed = sActivities.get(i);
            for (int j = 0; j < parsed.filters.size(); j++) {
                IntentFilter filter = parsed.filters.get(j);
                if (filter.hasAction(Intent.ACTION_MAIN) && filter.hasCategory(Intent.CATEGORY_LAUNCHER)) return parsed;
            }
        }
        return sActivities.isEmpty() ? null : sActivities.get(0);
    }

    // ---------------------------------------------------------------- embedded activities (LocalActivityManager)

    /** framework-internal (AOSP resolveActivityInfo). Throws if nothing in the app handles intent. */
    static ActivityInfo resolveActivityInfo(Intent intent) {
        final ParsedActivity parsed = resolveActivity(intent);
        if (parsed == null) {
            throw new android.content.ActivityNotFoundException("Unable to find explicit activity class "
                    + (intent.getComponent() != null ? intent.getComponent().toShortString() : String.valueOf(intent))
                    + "; have you declared this activity in your AndroidManifest.xml?");
        }
        return parsed.info;
    }

    /**
     * framework-internal (AOSP startActivityNow). Creates an activity embedded in parent and runs
     * its onCreate; the LocalActivityManager drives the rest of its lifecycle. It never gets a
     * record on the stack or a window of its own: its decor goes into the parent's views.
     */
    static Activity startActivityNow(Activity parent, String id, Intent intent, ActivityInfo info, Bundle state,
            Activity.NonConfigurationInstances nci) {
        final Object obj = newComponent(info.name);
        if (!(obj instanceof Activity)) throw new ClassCastException(info.name + " is not an Activity");
        final Activity a = (Activity) obj;
        a.attach(sContext.createComponentContext(a), info, intent, sApplication, parent, id, nci,
                sResources.getConfiguration());
        a.performCreate(state);
        return a;
    }

    /** framework-internal (AOSP performDestroyActivity for an embedded activity). */
    static void destroyEmbeddedActivity(Activity a, boolean finishing) {
        if (a == null || a.isDestroyed()) return;
        if (finishing) a.mFinished = true;
        if (a.mResumed) a.performPause();
        if (!a.isStopped() && a.mCreated) a.performStop();
        a.performDestroy();
        a.getWindow().closeAllPanels();
        WindowManagerGlobal.getInstance().closeAll(a);
    }

    private static ParsedActivity resolveActivity(Intent intent) {
        if (intent == null) return null;
        for (int i = 0; i < sActivities.size(); i++) {
            if (matches(sActivities.get(i), intent)) return sActivities.get(i);
        }
        return null;
    }

    private static boolean filterMatches(IntentFilter filter, Intent intent) {
        String action = intent.getAction();
        Set<String> categories = intent.getCategories();
        if (action == null && (categories == null || categories.isEmpty())) return false;
        return filter.match(action, intent.getType(), intent.getScheme(), intent.getData(), categories, TAG) >= 0;
    }

    static Object newComponent(String className) {
        try {
            return Class.forName(className).newInstance();
        } catch (Exception e) {
            // The VM wraps a constructor failure in InvocationTargetException.
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException("Cannot instantiate " + className, cause);
        }
    }

    static String qualify(String pkg, String cls) {
        if (cls == null || cls.isEmpty()) return null;
        if (cls.startsWith(".")) return (pkg != null ? pkg : "") + cls;
        if (cls.indexOf('.') < 0 && pkg != null) return pkg + "." + cls;
        return cls;
    }

    private static ActivityRecord top() { return sStack.isEmpty() ? null : sStack.get(sStack.size() - 1); }

    private static ActivityRecord findRecord(Activity activity) {
        for (int i = sStack.size() - 1; i >= 0; i--) {
            if (sStack.get(i).activity == activity) return sStack.get(i);
        }
        return null;
    }

    private static String attrString(XmlResourceParser parser, int attrId, String localName) {
        int n = parser.getAttributeCount();
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                if (parser.getAttributeNameResource(i) == attrId) return parser.getAttributeValue(i);
            }
        }
        String value = parser.getAttributeValue(ANDROID_NS, localName);
        if (value != null) return value;
        value = parser.getAttributeValue(null, localName);
        return value != null ? value : parser.getAttributeValue("", localName);
    }

    private static int attrRes(XmlResourceParser parser, int attrId, String localName) {
        int n = parser.getAttributeCount();
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                if (parser.getAttributeNameResource(i) == attrId) return parser.getAttributeResourceValue(i, 0);
            }
        }
        int value = parser.getAttributeResourceValue(ANDROID_NS, localName, 0);
        if (value != 0) return value;
        value = parser.getAttributeResourceValue(null, localName, 0);
        return value != 0 ? value : parser.getAttributeResourceValue("", localName, 0);
    }

    private static int attrInt(XmlResourceParser parser, int attrId, String localName, int def) {
        int n = parser.getAttributeCount();
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                if (parser.getAttributeNameResource(i) == attrId) return parser.getAttributeIntValue(i, def);
            }
        }
        if (parser.getAttributeValue(ANDROID_NS, localName) != null) {
            return parser.getAttributeIntValue(ANDROID_NS, localName, def);
        }
        if (parser.getAttributeValue(null, localName) != null) {
            return parser.getAttributeIntValue(null, localName, def);
        }
        return def;
    }

    private static boolean attrBool(XmlResourceParser parser, int attrId, String localName, boolean def) {
        int n = parser.getAttributeCount();
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                if (parser.getAttributeNameResource(i) == attrId) return parser.getAttributeBooleanValue(i, def);
            }
        }
        if (parser.getAttributeValue(ANDROID_NS, localName) != null) {
            return parser.getAttributeBooleanValue(ANDROID_NS, localName, def);
        }
        if (parser.getAttributeValue(null, localName) != null) {
            return parser.getAttributeBooleanValue(null, localName, def);
        }
        return def;
    }

    static final class ParsedActivity {
        final ActivityInfo info;
        final ArrayList<IntentFilter> filters = new ArrayList<IntentFilter>();
        final boolean receiver;

        ParsedActivity(ActivityInfo info, boolean receiver) {
            this.info = info;
            this.receiver = receiver;
        }
    }

    static final class ParsedService {
        final ServiceInfo info;
        final ArrayList<IntentFilter> filters = new ArrayList<IntentFilter>();

        ParsedService(ServiceInfo info) { this.info = info; }
    }

    private static final class ActivityRecord {
        Activity activity;
        ParsedActivity parsed;
        Intent intent;
        ActivityRecord caller;
        int requestCode = -1;
        String resultWho;
        /** Between onStart and onStop. */
        boolean started;
        /** Saved when stopped; used if the activity is relaunched later. */
        Bundle state;
        /** A configuration change arrived while stopped; relaunch when it comes back. */
        boolean relaunchPending;
        int pendingConfigChanges;
        final ArrayList<ResultInfo> pendingResults = new ArrayList<ResultInfo>();
    }

    private static final class ResultInfo {
        final String who;
        final int requestCode;
        final int resultCode;
        final Intent data;

        ResultInfo(String who, int requestCode, int resultCode, Intent data) {
            this.who = who;
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }
}
