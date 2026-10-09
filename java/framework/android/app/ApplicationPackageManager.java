package android.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** framework-internal. Package queries for the single loaded APK. */
public class ApplicationPackageManager extends PackageManager {
    private final HashMap<ComponentName, Integer> mComponentEnabled = new HashMap<ComponentName, Integer>();
    private final HashMap<String, Integer> mAppEnabled = new HashMap<String, Integer>();

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        if (!ours(packageName)) throw new NameNotFoundException(packageName);
        PackageInfo pi = new PackageInfo();
        pi.packageName = packageName;
        pi.applicationInfo = ActivityThread.sAppInfo;
        if ((flags & GET_ACTIVITIES) != 0) pi.activities = activityInfos(false);
        if ((flags & GET_RECEIVERS) != 0) pi.receivers = activityInfos(true);
        if ((flags & GET_SERVICES) != 0) pi.services = serviceInfos();
        if ((flags & GET_PROVIDERS) != 0) pi.providers = providerInfos();
        return pi;
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        if (!ours(packageName)) throw new NameNotFoundException(packageName);
        return ActivityThread.sAppInfo;
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException {
        ActivityThread.ParsedActivity pa = findActivity(component, false);
        if (pa == null) throw new NameNotFoundException(String.valueOf(component));
        return pa.info;
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags) throws NameNotFoundException {
        ActivityThread.ParsedActivity pa = findActivity(component, true);
        if (pa == null) throw new NameNotFoundException(String.valueOf(component));
        return pa.info;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
        ActivityThread.ParsedService ps = findService(component);
        if (ps == null) throw new NameNotFoundException(String.valueOf(component));
        return ps.info;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException {
        ProviderInfo info = findProvider(component);
        if (info == null) throw new NameNotFoundException(String.valueOf(component));
        return info;
    }

    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        ArrayList<PackageInfo> out = new ArrayList<PackageInfo>();
        if (ActivityThread.sPackageName == null) return out;
        try {
            out.add(getPackageInfo(ActivityThread.sPackageName, flags));
        } catch (NameNotFoundException ignored) {}
        return out;
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags) {
        ArrayList<ApplicationInfo> out = new ArrayList<ApplicationInfo>();
        if (ActivityThread.sAppInfo != null) out.add(ActivityThread.sAppInfo);
        return out;
    }

    @Override public int checkPermission(String permName, String packageName) { return PERMISSION_GRANTED; }

    /** What the console has: a multi-touch screen, controllers, Wi-Fi, audio and the controller IMU (WS15). */
    private static String[] features() {
        ArrayList<String> f = new ArrayList<String>();
        String[] fixed = {FEATURE_TOUCHSCREEN, FEATURE_TOUCHSCREEN_MULTITOUCH, FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT,
            FEATURE_FAKETOUCH, FEATURE_GAMEPAD, FEATURE_WIFI, FEATURE_AUDIO_OUTPUT, FEATURE_SCREEN_LANDSCAPE,
            FEATURE_SCREEN_PORTRAIT};
        for (String s : fixed) f.add(s);
        android.hardware.SensorManager sm = android.hardware.SystemSensorManager.getInstance();
        if (sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null) f.add(FEATURE_SENSOR_ACCELEROMETER);
        if (sm.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null) f.add(FEATURE_SENSOR_GYROSCOPE);
        return f.toArray(new String[f.size()]);
    }

    @Override
    public boolean hasSystemFeature(String featureName) {
        for (String f : features()) {
            if (f.equals(featureName)) return true;
        }
        return false;
    }

    @Override
    public FeatureInfo[] getSystemAvailableFeatures() {
        String[] names = features();
        FeatureInfo[] out = new FeatureInfo[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = new FeatureInfo();
            out[i].name = names[i];
        }
        return out;
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags) {
        List<ResolveInfo> list = queryIntentActivities(intent, flags);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        return queryActivities(ActivityThread.sActivities, intent);
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        return queryActivities(ActivityThread.sReceivers, intent);
    }

    @Override
    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        ArrayList<ResolveInfo> out = new ArrayList<ResolveInfo>();
        if (intent == null) return out;
        for (int i = 0; i < ActivityThread.sServices.size(); i++) {
            ActivityThread.ParsedService ps = ActivityThread.sServices.get(i);
            if (!ActivityThread.matchesService(ps, intent)) continue;
            ResolveInfo ri = new ResolveInfo();
            ri.serviceInfo = ps.info;
            ri.filter = ps.filters.isEmpty() ? null : ps.filters.get(0);
            out.add(ri);
        }
        return out;
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags) {
        List<ResolveInfo> list = queryIntentServices(intent, flags);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public ProviderInfo resolveContentProvider(String authority, int flags) {
        if (authority == null) return null;
        for (int i = 0; i < ActivityThread.sProviders.size(); i++) {
            ProviderInfo pi = ActivityThread.sProviders.get(i);
            if (pi.authority == null) continue;
            String[] parts = pi.authority.split(";");
            for (int j = 0; j < parts.length; j++) {
                if (authority.equals(parts[j].trim())) return pi;
            }
        }
        return null;
    }

    @Override
    public Intent getLaunchIntentForPackage(String packageName) {
        if (!ours(packageName)) return null;
        for (int i = 0; i < ActivityThread.sActivities.size(); i++) {
            ActivityThread.ParsedActivity pa = ActivityThread.sActivities.get(i);
            for (int j = 0; j < pa.filters.size(); j++) {
                IntentFilter f = pa.filters.get(j);
                if (!f.hasAction(Intent.ACTION_MAIN) || !f.hasCategory(Intent.CATEGORY_LAUNCHER)) continue;
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new ComponentName(pa.info.packageName, pa.info.name));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return intent;
            }
        }
        return null;
    }

    @Override
    public Drawable getDrawable(String packageName, int resid, ApplicationInfo appInfo) {
        if (resid == 0) return null;
        Resources res = ActivityThread.sResources;
        if (res != null && (ours(packageName) || appInfo == null || ours(appInfo.packageName))) {
            try {
                return res.getDrawable(resid);
            } catch (Resources.NotFoundException ignored) {}
        }
        return new ColorDrawable(0xFF808080);
    }

    @Override
    public Drawable getActivityIcon(ComponentName activityName) throws NameNotFoundException {
        ActivityInfo info = getActivityInfo(activityName, 0);
        if (info.icon != 0) {
            Drawable d = getDrawable(info.packageName, info.icon, info.applicationInfo);
            if (d != null) return d;
        }
        return getDefaultActivityIcon();
    }

    @Override
    public Drawable getApplicationIcon(ApplicationInfo info) {
        if (info != null && info.icon != 0) {
            Drawable d = getDrawable(info.packageName, info.icon, info);
            if (d != null) return d;
        }
        return getDefaultActivityIcon();
    }

    @Override
    public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
        return getApplicationIcon(getApplicationInfo(packageName, 0));
    }

    @Override public Drawable getDefaultActivityIcon() { return new ColorDrawable(0xFF808080); }

    @Override
    public CharSequence getApplicationLabel(ApplicationInfo info) {
        if (info == null) return null;
        if (info.nonLocalizedLabel != null) return info.nonLocalizedLabel;
        if (info.labelRes != 0) {
            CharSequence text = getText(info.packageName, info.labelRes, info);
            if (text != null) return text;
        }
        return info.packageName;
    }

    @Override
    public CharSequence getText(String packageName, int resid, ApplicationInfo appInfo) {
        if (resid == 0 || ActivityThread.sResources == null) return null;
        if (!ours(packageName) && (appInfo == null || !ours(appInfo.packageName))) return null;
        try {
            return ActivityThread.sResources.getText(resid);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    @Override
    public XmlResourceParser getXml(String packageName, int resid, ApplicationInfo appInfo) {
        if (resid == 0 || ActivityThread.sResources == null) return null;
        if (!ours(packageName) && (appInfo == null || !ours(appInfo.packageName))) return null;
        try {
            return ActivityThread.sResources.getXml(resid);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    @Override
    public Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException {
        if (app == null || !ours(app.packageName)) {
            throw new NameNotFoundException(app != null ? app.packageName : null);
        }
        return resources();
    }

    @Override
    public Resources getResourcesForApplication(String packageName) throws NameNotFoundException {
        if (!ours(packageName)) throw new NameNotFoundException(packageName);
        return resources();
    }

    @Override public String getInstallerPackageName(String packageName) { return null; }

    @Override
    public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {
        mComponentEnabled.put(componentName, Integer.valueOf(newState));
    }

    @Override
    public int getComponentEnabledSetting(ComponentName componentName) {
        Integer state = mComponentEnabled.get(componentName);
        return state != null ? state.intValue() : COMPONENT_ENABLED_STATE_DEFAULT;
    }

    @Override
    public void setApplicationEnabledSetting(String packageName, int newState, int flags) {
        mAppEnabled.put(packageName, Integer.valueOf(newState));
    }

    @Override
    public int getApplicationEnabledSetting(String packageName) {
        Integer state = mAppEnabled.get(packageName);
        return state != null ? state.intValue() : COMPONENT_ENABLED_STATE_DEFAULT;
    }

    @Override public boolean isSafeMode() { return false; }

    @Override
    public String[] getPackagesForUid(int uid) {
        if (uid == Process.myUid() && ActivityThread.sPackageName != null) {
            return new String[] { ActivityThread.sPackageName };
        }
        return null;
    }

    @Override
    public String getNameForUid(int uid) {
        return uid == Process.myUid() ? ActivityThread.sPackageName : null;
    }

    @Override
    public int checkSignatures(String packageName1, String packageName2) {
        boolean known1 = ours(packageName1);
        boolean known2 = ours(packageName2);
        if (!known1 || !known2) return SIGNATURE_UNKNOWN_PACKAGE;
        return packageName1.equals(packageName2) ? SIGNATURE_MATCH : SIGNATURE_NO_MATCH;
    }

    private static boolean ours(String packageName) {
        return packageName != null && packageName.equals(ActivityThread.sPackageName);
    }

    private static Resources resources() {
        return ActivityThread.sResources != null ? ActivityThread.sResources : Resources.getSystem();
    }

    private static ActivityThread.ParsedActivity findActivity(ComponentName component, boolean receiver) {
        ArrayList<ActivityThread.ParsedActivity> list = receiver
                ? ActivityThread.sReceivers : ActivityThread.sActivities;
        for (int i = 0; i < list.size(); i++) {
            if (ActivityThread.sameComponent(list.get(i).info, component)) return list.get(i);
        }
        return null;
    }

    private static ActivityThread.ParsedService findService(ComponentName component) {
        for (int i = 0; i < ActivityThread.sServices.size(); i++) {
            ActivityThread.ParsedService ps = ActivityThread.sServices.get(i);
            if (ActivityThread.sameComponent(ps.info, component)) return ps;
        }
        return null;
    }

    private static ProviderInfo findProvider(ComponentName component) {
        for (int i = 0; i < ActivityThread.sProviders.size(); i++) {
            ProviderInfo pi = ActivityThread.sProviders.get(i);
            if (ActivityThread.sameComponent(pi, component)) return pi;
        }
        return null;
    }

    private static ActivityInfo[] activityInfos(boolean receivers) {
        ArrayList<ActivityThread.ParsedActivity> list = receivers
                ? ActivityThread.sReceivers : ActivityThread.sActivities;
        ActivityInfo[] out = new ActivityInfo[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i).info;
        return out;
    }

    private static ServiceInfo[] serviceInfos() {
        ServiceInfo[] out = new ServiceInfo[ActivityThread.sServices.size()];
        for (int i = 0; i < out.length; i++) out[i] = ActivityThread.sServices.get(i).info;
        return out;
    }

    private static ProviderInfo[] providerInfos() {
        return ActivityThread.sProviders.toArray(new ProviderInfo[ActivityThread.sProviders.size()]);
    }

    private static List<ResolveInfo> queryActivities(ArrayList<ActivityThread.ParsedActivity> list, Intent intent) {
        ArrayList<ResolveInfo> out = new ArrayList<ResolveInfo>();
        if (intent == null) return out;
        for (int i = 0; i < list.size(); i++) {
            ActivityThread.ParsedActivity pa = list.get(i);
            if (!ActivityThread.matches(pa, intent)) continue;
            ResolveInfo ri = new ResolveInfo();
            ri.activityInfo = pa.info;
            ri.filter = pa.filters.isEmpty() ? null : pa.filters.get(0);
            out.add(ri);
        }
        return out;
    }
}
