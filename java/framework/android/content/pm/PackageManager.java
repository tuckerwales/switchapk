package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.AndroidException;
import java.util.List;

public abstract class PackageManager {
    public static class NameNotFoundException extends AndroidException {
        public NameNotFoundException() {}
        public NameNotFoundException(String name) { super(name); }
    }

    public static final int GET_ACTIVITIES = 0x00000001;
    public static final int GET_RECEIVERS = 0x00000002;
    public static final int GET_SERVICES = 0x00000004;
    public static final int GET_PROVIDERS = 0x00000008;
    public static final int GET_INSTRUMENTATION = 0x00000010;
    public static final int GET_INTENT_FILTERS = 0x00000020;
    public static final int GET_SIGNATURES = 0x00000040;
    public static final int GET_RESOLVED_FILTER = 0x00000040;
    public static final int GET_META_DATA = 0x00000080;
    public static final int GET_GIDS = 0x00000100;
    public static final int GET_DISABLED_COMPONENTS = 0x00000200;
    public static final int GET_SHARED_LIBRARY_FILES = 0x00000400;
    public static final int GET_URI_PERMISSION_PATTERNS = 0x00000800;
    public static final int GET_PERMISSIONS = 0x00001000;
    public static final int GET_UNINSTALLED_PACKAGES = 0x00002000;
    public static final int GET_CONFIGURATIONS = 0x00004000;
    public static final int GET_DISABLED_UNTIL_USED_COMPONENTS = 0x00008000;
    public static final int MATCH_UNINSTALLED_PACKAGES = 0x00002000;
    public static final int MATCH_DISABLED_COMPONENTS = 0x00000200;
    public static final int MATCH_DEFAULT_ONLY = 0x00010000;
    public static final int MATCH_ALL = 0x00020000;
    public static final int MATCH_DIRECT_BOOT_UNAWARE = 0x00040000;
    public static final int MATCH_DIRECT_BOOT_AWARE = 0x00080000;
    public static final int MATCH_SYSTEM_ONLY = 0x00100000;
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000;
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int SIGNATURE_MATCH = 0;
    public static final int SIGNATURE_NEITHER_SIGNED = 1;
    public static final int SIGNATURE_FIRST_NOT_SIGNED = -1;
    public static final int SIGNATURE_SECOND_NOT_SIGNED = -2;
    public static final int SIGNATURE_NO_MATCH = -3;
    public static final int SIGNATURE_UNKNOWN_PACKAGE = -4;
    public static final int COMPONENT_ENABLED_STATE_DEFAULT = 0;
    public static final int COMPONENT_ENABLED_STATE_ENABLED = 1;
    public static final int COMPONENT_ENABLED_STATE_DISABLED = 2;
    public static final int COMPONENT_ENABLED_STATE_DISABLED_USER = 3;
    public static final int COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED = 4;
    public static final int DONT_KILL_APP = 0x00000001;
    public static final int VERIFICATION_ALLOW = 1;
    public static final int VERIFICATION_REJECT = -1;
    public static final int INSTALL_REASON_UNKNOWN = 0;

    public static final String FEATURE_AUDIO_OUTPUT = "android.hardware.audio.output";
    public static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";
    public static final String FEATURE_CAMERA = "android.hardware.camera";
    public static final String FEATURE_CAMERA_ANY = "android.hardware.camera.any";
    public static final String FEATURE_CAMERA_FRONT = "android.hardware.camera.front";
    public static final String FEATURE_CAMERA_FLASH = "android.hardware.camera.flash";
    public static final String FEATURE_CAMERA_AUTOFOCUS = "android.hardware.camera.autofocus";
    public static final String FEATURE_GAMEPAD = "android.hardware.gamepad";
    public static final String FEATURE_LOCATION = "android.hardware.location";
    public static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    public static final String FEATURE_LOCATION_NETWORK = "android.hardware.location.network";
    public static final String FEATURE_MICROPHONE = "android.hardware.microphone";
    public static final String FEATURE_NFC = "android.hardware.nfc";
    public static final String FEATURE_SENSOR_ACCELEROMETER = "android.hardware.sensor.accelerometer";
    public static final String FEATURE_SENSOR_GYROSCOPE = "android.hardware.sensor.gyroscope";
    public static final String FEATURE_SENSOR_COMPASS = "android.hardware.sensor.compass";
    public static final String FEATURE_SCREEN_LANDSCAPE = "android.hardware.screen.landscape";
    public static final String FEATURE_SCREEN_PORTRAIT = "android.hardware.screen.portrait";
    public static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    public static final String FEATURE_TELEVISION = "android.hardware.type.television";
    public static final String FEATURE_LEANBACK = "android.software.leanback";
    public static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    public static final String FEATURE_TOUCHSCREEN_MULTITOUCH = "android.hardware.touchscreen.multitouch";
    public static final String FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT = "android.hardware.touchscreen.multitouch.distinct";
    public static final String FEATURE_FAKETOUCH = "android.hardware.faketouch";
    public static final String FEATURE_USB_HOST = "android.hardware.usb.host";
    public static final String FEATURE_WIFI = "android.hardware.wifi";
    public static final String FEATURE_OPENGLES_EXTENSION_PACK = "android.hardware.opengles.aep";
    public static final String FEATURE_VULKAN_HARDWARE_LEVEL = "android.hardware.vulkan.level";
    public static final String FEATURE_WATCH = "android.hardware.type.watch";
    public static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    public static final String FEATURE_PC = "android.hardware.type.pc";
    public static final String FEATURE_WEBVIEW = "android.software.webview";
    public static final String FEATURE_APP_WIDGETS = "android.software.app_widgets";
    public static final String FEATURE_FINGERPRINT = "android.hardware.fingerprint";
    public static final String FEATURE_VR_MODE_HIGH_PERFORMANCE = "android.hardware.vr.high_performance";

    public abstract PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException;
    public PackageInfo getPackageInfo(String packageName, PackageInfoFlags flags) throws NameNotFoundException { return getPackageInfo(packageName, (int) flags.getValue()); }
    public abstract ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException;
    public ApplicationInfo getApplicationInfo(String packageName, ApplicationInfoFlags flags) throws NameNotFoundException { return getApplicationInfo(packageName, (int) flags.getValue()); }
    public abstract ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException;
    public abstract ActivityInfo getReceiverInfo(ComponentName component, int flags) throws NameNotFoundException;
    public abstract ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException;
    public abstract ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException;
    public abstract List<PackageInfo> getInstalledPackages(int flags);
    public abstract List<ApplicationInfo> getInstalledApplications(int flags);
    public abstract int checkPermission(String permName, String packageName);
    public abstract boolean hasSystemFeature(String featureName);
    public boolean hasSystemFeature(String featureName, int version) { return hasSystemFeature(featureName); }
    public abstract FeatureInfo[] getSystemAvailableFeatures();
    public abstract ResolveInfo resolveActivity(Intent intent, int flags);
    public abstract List<ResolveInfo> queryIntentActivities(Intent intent, int flags);
    public abstract List<ResolveInfo> queryIntentServices(Intent intent, int flags);
    public abstract List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags);
    public abstract ResolveInfo resolveService(Intent intent, int flags);
    public abstract ProviderInfo resolveContentProvider(String authority, int flags);
    public abstract List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags);
    public abstract List<ProviderInfo> queryContentProviders(String processName, int uid, int flags);
    // API 33 typed-flag overloads: delegate to the int versions, as Android's defaults do.
    public ActivityInfo getActivityInfo(ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException { return getActivityInfo(component, (int) flags.getValue()); }
    public ActivityInfo getReceiverInfo(ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException { return getReceiverInfo(component, (int) flags.getValue()); }
    public ServiceInfo getServiceInfo(ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException { return getServiceInfo(component, (int) flags.getValue()); }
    public ProviderInfo getProviderInfo(ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException { return getProviderInfo(component, (int) flags.getValue()); }
    public List<PackageInfo> getInstalledPackages(PackageInfoFlags flags) { return getInstalledPackages((int) flags.getValue()); }
    public List<ApplicationInfo> getInstalledApplications(ApplicationInfoFlags flags) { return getInstalledApplications((int) flags.getValue()); }
    public ResolveInfo resolveActivity(Intent intent, ResolveInfoFlags flags) { return resolveActivity(intent, (int) flags.getValue()); }
    public List<ResolveInfo> queryIntentActivities(Intent intent, ResolveInfoFlags flags) { return queryIntentActivities(intent, (int) flags.getValue()); }
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, ResolveInfoFlags flags) { return queryBroadcastReceivers(intent, (int) flags.getValue()); }
    public ResolveInfo resolveService(Intent intent, ResolveInfoFlags flags) { return resolveService(intent, (int) flags.getValue()); }
    public List<ResolveInfo> queryIntentServices(Intent intent, ResolveInfoFlags flags) { return queryIntentServices(intent, (int) flags.getValue()); }
    public List<ResolveInfo> queryIntentContentProviders(Intent intent, ResolveInfoFlags flags) { return queryIntentContentProviders(intent, (int) flags.getValue()); }
    public ProviderInfo resolveContentProvider(String authority, ComponentInfoFlags flags) { return resolveContentProvider(authority, (int) flags.getValue()); }
    public List<ProviderInfo> queryContentProviders(String processName, int uid, ComponentInfoFlags flags) { return queryContentProviders(processName, uid, (int) flags.getValue()); }
    public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, PackageInfoFlags flags) { return getPackagesHoldingPermissions(permissions, (int) flags.getValue()); }
    public PackageInfo getPackageArchiveInfo(String archiveFilePath, PackageInfoFlags flags) { return getPackageArchiveInfo(archiveFilePath, (int) flags.getValue()); }
    public abstract Intent getLaunchIntentForPackage(String packageName);
    public Intent getLeanbackLaunchIntentForPackage(String packageName) { return null; }
    public abstract Drawable getDrawable(String packageName, int resid, ApplicationInfo appInfo);
    public abstract Drawable getActivityIcon(ComponentName activityName) throws NameNotFoundException;
    public abstract Drawable getApplicationIcon(ApplicationInfo info);
    public abstract Drawable getApplicationIcon(String packageName) throws NameNotFoundException;
    public abstract Drawable getDefaultActivityIcon();
    public abstract CharSequence getApplicationLabel(ApplicationInfo info);
    public abstract CharSequence getText(String packageName, int resid, ApplicationInfo appInfo);
    public abstract XmlResourceParser getXml(String packageName, int resid, ApplicationInfo appInfo);
    public abstract Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException;
    public abstract Resources getResourcesForApplication(String packageName) throws NameNotFoundException;
    public abstract String getInstallerPackageName(String packageName);
    public abstract void setComponentEnabledSetting(ComponentName componentName, int newState, int flags);
    public abstract int getComponentEnabledSetting(ComponentName componentName);
    public abstract void setApplicationEnabledSetting(String packageName, int newState, int flags);
    public abstract int getApplicationEnabledSetting(String packageName);
    public abstract boolean isSafeMode();
    public abstract String[] getPackagesForUid(int uid);
    public abstract String getNameForUid(int uid);
    public abstract int checkSignatures(String packageName1, String packageName2);
    public Drawable loadItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        int res = itemInfo.icon != 0 ? itemInfo.icon : (appInfo != null ? appInfo.icon : 0);
        Drawable d = res != 0 ? getDrawable(itemInfo.packageName, res, appInfo) : null;
        return d != null ? d : getDefaultActivityIcon();
    }
    public boolean isInstantApp() { return false; }
    public boolean canRequestPackageInstalls() { return false; }
    public boolean isPermissionRevokedByPolicy(String permName, String packageName) { return false; }
    public boolean shouldShowRequestPermissionRationale(String permission) { return false; }
    public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, int flags) { return new java.util.ArrayList<PackageInfo>(); }
    public int[] getPackageGids(String packageName) throws NameNotFoundException { return new int[0]; }
    public PermissionInfo getPermissionInfo(String permName, int flags) throws NameNotFoundException { throw new NameNotFoundException(permName); }
    public InstallSourceInfo getInstallSourceInfo(String packageName) throws NameNotFoundException { return new InstallSourceInfo(); }
    public PackageInstaller getPackageInstaller() { return new PackageInstaller(); }
    public void addPackageToPreferred(String packageName) {}
    public void removePackageFromPreferred(String packageName) {}
    public void clearPackagePreferredActivities(String packageName) {}
    public int getPreferredActivities(List<IntentFilter> outFilters, List<ComponentName> outActivities, String packageName) { return 0; }
    public String getPackageArchiveInfoPath() { return null; }
    public PackageInfo getPackageArchiveInfo(String archiveFilePath, int flags) { return null; }
    public ChangedPackages getChangedPackages(int sequenceNumber) { return null; }
    public boolean hasSigningCertificate(String packageName, byte[] certificate, int type) { return false; }

    public static final class PackageInfoFlags {
        private final long mValue;
        private PackageInfoFlags(long value) { mValue = value; }
        public static PackageInfoFlags of(long value) { return new PackageInfoFlags(value); }
        public long getValue() { return mValue; }
    }

    public static final class ApplicationInfoFlags {
        private final long mValue;
        private ApplicationInfoFlags(long value) { mValue = value; }
        public static ApplicationInfoFlags of(long value) { return new ApplicationInfoFlags(value); }
        public long getValue() { return mValue; }
    }

    public static final class ResolveInfoFlags {
        private final long mValue;
        private ResolveInfoFlags(long value) { mValue = value; }
        public static ResolveInfoFlags of(long value) { return new ResolveInfoFlags(value); }
        public long getValue() { return mValue; }
    }

    public static final class ComponentInfoFlags {
        private final long mValue;
        private ComponentInfoFlags(long value) { mValue = value; }
        public static ComponentInfoFlags of(long value) { return new ComponentInfoFlags(value); }
        public long getValue() { return mValue; }
    }
}
