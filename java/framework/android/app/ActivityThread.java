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
import android.content.res.XmlResourceParser;
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

    private ActivityThread() {}

    public static void main(String[] args) {
        Looper.prepareMainLooper();
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
            Activity activity = sStack.get(i).activity;
            if (activity != null) activity.performDestroy();
        }
        sStack.clear();
        System.exit(0);
    }

    static void startActivity(Activity caller, Intent intent, int requestCode) {
        if (sShutdown) return;
        ParsedActivity parsed = resolveActivity(intent);
        if (parsed == null) {
            Log.w(TAG, "No activity for " + intent);
            if (requestCode >= 0) throw new android.content.ActivityNotFoundException(String.valueOf(intent));
            return;
        }
        ActivityRecord prev = top();
        if (prev != null && prev.activity != null) prev.activity.performPause();
        Object obj = newComponent(parsed.info.name);
        if (!(obj instanceof Activity)) throw new ClassCastException(parsed.info.name + " is not an Activity");
        Activity next = (Activity) obj;
        ActivityRecord rec = new ActivityRecord();
        rec.activity = next;
        rec.parsed = parsed;
        rec.caller = caller == null ? null : findRecord(caller);
        rec.requestCode = requestCode;
        rec.intent = intent != null ? new Intent(intent) : new Intent();
        if (rec.intent.getComponent() == null) {
            rec.intent.setComponent(new ComponentName(parsed.info.packageName, parsed.info.name));
        }
        sStack.add(rec);
        next.attach(sContext, parsed.info, rec.intent, sApplication);
        next.performCreate();
        if (next.isFinishing() || top() != rec) return;
        next.performStart();
        if (next.isFinishing() || top() != rec) return;
        next.performResume();
        if (prev != null && prev != rec && prev.activity != null) prev.activity.performStop();
    }

    static void finishActivity(Activity activity, int resultCode, Intent data) {
        if (sShutdown || activity == null) return;
        ActivityRecord rec = findRecord(activity);
        if (rec == null) return;
        boolean wasTop = top() == rec;
        activity.performDestroy();
        sStack.remove(rec);
        if (rec.caller != null && rec.requestCode >= 0 && rec.caller.activity != null) {
            rec.caller.activity.deliverResult(rec.requestCode, resultCode, data);
        }
        if (sStack.isEmpty()) {
            shutdown();
            return;
        }
        if (!wasTop) return;
        ActivityRecord now = top();
        if (now == null || now.activity == null || now.activity.isFinishing()) return;
        now.activity.performStart();
        now.activity.performResume();
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
        startActivity(null, launch, -1);
        Log.i(TAG, sPackageName + " " + launcher.info.name);
    }

    private static void dispatchPlatform() {
        PlatformInput.drain(new PlatformInput.Sink() {
            public void onQuit() { shutdown(); }

            public void onFocus(boolean gained) {
                ActivityRecord rec = top();
                if (rec == null || rec.activity == null || rec.activity.isFinishing()) return;
                if (gained) rec.activity.performResume();
                else rec.activity.performPause();
            }

            public void onResize(int width, int height, int dpi) {
                WindowManagerGlobal.getInstance().scheduleAll();
            }
        });
    }

    private static Resources buildResources() {
        Display display = WindowManagerImpl.getDefault().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
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
        config.uiMode = Configuration.UI_MODE_TYPE_NORMAL;
        return new Resources(AssetManager.getSystem(), metrics, config);
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
                        activity = new ParsedActivity(info, "receiver".equals(name));
                        filter = null;
                    } else if ("service".equals(name)) {
                        ServiceInfo info = new ServiceInfo();
                        fillComponent(info, parser);
                        service = new ParsedService(info);
                        filter = null;
                    } else if ("provider".equals(name)) {
                        provider = new ProviderInfo();
                        fillComponent(provider, parser);
                        provider.authority = attrString(parser, android.R.attr.authorities, "authorities");
                    } else if ("intent-filter".equals(name)) {
                        filter = new IntentFilter();
                        if (activity != null) activity.filters.add(filter);
                        else if (service != null) service.filters.add(filter);
                        else filter = null;
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

    private static Object newComponent(String className) {
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
    }
}
