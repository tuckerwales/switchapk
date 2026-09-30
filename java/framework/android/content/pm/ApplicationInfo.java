package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.UUID;

public class ApplicationInfo extends PackageItemInfo implements Parcelable {
    public static final int FLAG_SYSTEM = 1 << 0;
    public static final int FLAG_DEBUGGABLE = 1 << 1;
    public static final int FLAG_HAS_CODE = 1 << 2;
    public static final int FLAG_PERSISTENT = 1 << 3;
    public static final int FLAG_FACTORY_TEST = 1 << 4;
    public static final int FLAG_ALLOW_TASK_REPARENTING = 1 << 5;
    public static final int FLAG_ALLOW_CLEAR_USER_DATA = 1 << 6;
    public static final int FLAG_UPDATED_SYSTEM_APP = 1 << 7;
    public static final int FLAG_TEST_ONLY = 1 << 8;
    public static final int FLAG_SUPPORTS_SMALL_SCREENS = 1 << 9;
    public static final int FLAG_SUPPORTS_NORMAL_SCREENS = 1 << 10;
    public static final int FLAG_SUPPORTS_LARGE_SCREENS = 1 << 11;
    public static final int FLAG_RESIZEABLE_FOR_SCREENS = 1 << 12;
    public static final int FLAG_SUPPORTS_SCREEN_DENSITIES = 1 << 13;
    public static final int FLAG_VM_SAFE_MODE = 1 << 14;
    public static final int FLAG_ALLOW_BACKUP = 1 << 15;
    public static final int FLAG_KILL_AFTER_RESTORE = 1 << 16;
    public static final int FLAG_RESTORE_ANY_VERSION = 1 << 17;
    public static final int FLAG_EXTERNAL_STORAGE = 1 << 18;
    public static final int FLAG_SUPPORTS_XLARGE_SCREENS = 1 << 19;
    public static final int FLAG_LARGE_HEAP = 1 << 20;
    public static final int FLAG_STOPPED = 1 << 21;
    public static final int FLAG_SUPPORTS_RTL = 1 << 22;
    public static final int FLAG_INSTALLED = 1 << 23;
    public static final int FLAG_IS_DATA_ONLY = 1 << 24;
    public static final int FLAG_IS_GAME = 1 << 25;
    public static final int FLAG_FULL_BACKUP_ONLY = 1 << 26;
    public static final int FLAG_USES_CLEARTEXT_TRAFFIC = 1 << 27;
    public static final int FLAG_EXTRACT_NATIVE_LIBS = 1 << 28;
    public static final int FLAG_HARDWARE_ACCELERATED = 1 << 29;
    public static final int FLAG_SUSPENDED = 1 << 30;
    public static final int FLAG_MULTIARCH = 1 << 31;
    public static final int CATEGORY_UNDEFINED = -1;
    public static final int CATEGORY_GAME = 0;
    public static final int CATEGORY_AUDIO = 1;
    public static final int CATEGORY_VIDEO = 2;
    public static final int CATEGORY_IMAGE = 3;
    public static final int CATEGORY_SOCIAL = 4;
    public static final int CATEGORY_NEWS = 5;
    public static final int CATEGORY_MAPS = 6;
    public static final int CATEGORY_PRODUCTIVITY = 7;

    public String taskAffinity;
    public String permission;
    public String processName;
    public String className;
    public int descriptionRes;
    public int theme;
    public String manageSpaceActivityName;
    public String backupAgentName;
    public int uiOptions;
    public int flags;
    public int requiresSmallestWidthDp;
    public int compatibleWidthLimitDp;
    public int largestWidthLimitDp;
    public UUID storageUuid;
    public String sourceDir;
    public String publicSourceDir;
    public String[] splitNames;
    public String[] splitSourceDirs;
    public String[] splitPublicSourceDirs;
    public String[] sharedLibraryFiles;
    public String dataDir;
    public String deviceProtectedDataDir;
    public String nativeLibraryDir;
    public String primaryCpuAbi;
    public int uid;
    public int minSdkVersion;
    public int targetSdkVersion;
    public int compileSdkVersion;
    public String compileSdkVersionCodename;
    public boolean enabled = true;
    public int category = CATEGORY_UNDEFINED;
    public String appComponentFactory;

    public ApplicationInfo() {}

    public ApplicationInfo(ApplicationInfo orig) {
        super(orig);
        taskAffinity = orig.taskAffinity;
        permission = orig.permission;
        processName = orig.processName;
        className = orig.className;
        theme = orig.theme;
        flags = orig.flags;
        sourceDir = orig.sourceDir;
        publicSourceDir = orig.publicSourceDir;
        dataDir = orig.dataDir;
        deviceProtectedDataDir = orig.deviceProtectedDataDir;
        nativeLibraryDir = orig.nativeLibraryDir;
        uid = orig.uid;
        minSdkVersion = orig.minSdkVersion;
        targetSdkVersion = orig.targetSdkVersion;
        enabled = orig.enabled;
        descriptionRes = orig.descriptionRes;
        category = orig.category;
        appComponentFactory = orig.appComponentFactory;
    }

    public boolean isProfileableByShell() { return false; }
    public boolean isResourceOverlay() { return false; }
    public boolean areAttributionsUserVisible() { return false; }
    public static CharSequence getCategoryTitle(android.content.Context context, int category) { return null; }
    public CharSequence loadDescription(PackageManager pm) { return null; }

    @Override
    protected ApplicationInfo getApplicationInfo() { return this; }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int parcelableFlags) { dest.writeValue(new ApplicationInfo(this)); }

    public static final Parcelable.Creator<ApplicationInfo> CREATOR = new Parcelable.Creator<ApplicationInfo>() {
        public ApplicationInfo createFromParcel(Parcel source) { return (ApplicationInfo) source.readValue(null); }
        public ApplicationInfo[] newArray(int size) { return new ApplicationInfo[size]; }
    };

    public String toString() { return "ApplicationInfo{" + Integer.toHexString(System.identityHashCode(this)) + " " + packageName + "}"; }
}
