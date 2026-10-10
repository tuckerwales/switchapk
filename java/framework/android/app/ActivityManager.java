package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;

/**
 * One app process in the foreground (WS4). Memory figures are the Switch's: 4 GB in total, of which about 3 GB is
 * left to an app running in title takeover; the Java heap limit is Runtime.maxMemory(). There are no other tasks,
 * services or past exits to report.
 */
public class ActivityManager {
    public static final String ACTION_REPORT_HEAP_LIMIT = "android.app.action.REPORT_HEAP_LIMIT";
    public static final int LOCK_TASK_MODE_LOCKED = 1;
    public static final int LOCK_TASK_MODE_NONE = 0;
    public static final int LOCK_TASK_MODE_PINNED = 2;
    public static final String META_HOME_ALTERNATE = "android.app.home.alternate";
    public static final int MOVE_TASK_NO_USER_ACTION = 2;
    public static final int MOVE_TASK_WITH_HOME = 1;
    public static final int RECENT_IGNORE_UNAVAILABLE = 2;
    public static final int RECENT_WITH_EXCLUDED = 1;

    private static final long GB = 1024L * 1024 * 1024;
    private static final long TOTAL_MEM = 4 * GB;
    private static final long APP_MEM = 3 * GB;

    /** framework-internal: the instance behind Context.ACTIVITY_SERVICE. */
    public ActivityManager() {}

    public int getMemoryClass() { return (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024)); }
    public int getLargeMemoryClass() { return getMemoryClass(); }
    public boolean isLowRamDevice() { return false; }

    public void moveTaskToFront(int taskId, int flags) {}
    public void moveTaskToFront(int taskId, int flags, android.os.Bundle options) {}

    public boolean isActivityStartAllowedOnDisplay(Context context, int displayId, Intent intent) {
        return displayId == 0;
    }

    public android.util.Size getAppTaskThumbnailSize() { return new android.util.Size(256, 144); }

    public PendingIntent getRunningServiceControlPanel(android.content.ComponentName service) { return null; }

    public void addStartInfoTimestamp(int key, long timestampNs) {}

    public void getMemoryInfo(MemoryInfo outInfo) {
        Runtime rt = Runtime.getRuntime();
        outInfo.totalMem = TOTAL_MEM;
        outInfo.advertisedMem = TOTAL_MEM;
        outInfo.availMem = APP_MEM - (rt.totalMemory() - rt.freeMemory());
        outInfo.threshold = 256L * 1024 * 1024;
        outInfo.lowMemory = outInfo.availMem < outInfo.threshold;
    }

    public boolean clearApplicationUserData() { return false; }
    public boolean isBackgroundRestricted() { return false; }

    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        List<RunningAppProcessInfo> out = new ArrayList<RunningAppProcessInfo>();
        RunningAppProcessInfo info = new RunningAppProcessInfo();
        getMyMemoryState(info);
        out.add(info);
        return out;
    }

    public List<ApplicationExitInfo> getHistoricalProcessExitReasons(String packageName, int pid, int maxNum) {
        return new ArrayList<ApplicationExitInfo>();
    }

    public void setProcessStateSummary(byte[] state) {}
    public static boolean isLowMemoryKillReportSupported() { return false; }

    public static void getMyMemoryState(RunningAppProcessInfo outState) {
        outState.processName = Process.myProcessName();
        outState.pid = Process.myPid();
        outState.uid = Process.myUid();
        outState.pkgList = new String[] { outState.processName };
        outState.importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        outState.lru = 0;
        outState.lastTrimLevel = 0;
        outState.importanceReasonCode = RunningAppProcessInfo.REASON_UNKNOWN;
    }

    public android.os.Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids) {
        android.os.Debug.MemoryInfo[] out = new android.os.Debug.MemoryInfo[pids.length];
        for (int i = 0; i < pids.length; i++) out[i] = new android.os.Debug.MemoryInfo();
        return out;
    }

    @Deprecated public void restartPackage(String packageName) {}
    public void killBackgroundProcesses(String packageName) {}

    /** OpenGL ES 3.2, which the Switch driver (and host Mesa) provide. */
    public ConfigurationInfo getDeviceConfigurationInfo() {
        ConfigurationInfo info = new ConfigurationInfo();
        info.reqGlEsVersion = 0x00030002;
        return info;
    }

    public int getLauncherLargeIconDensity() { return android.util.DisplayMetrics.DENSITY_XHIGH; }
    public int getLauncherLargeIconSize() { return 96; }
    public static boolean isUserAMonkey() { return false; }
    public static boolean isRunningInTestHarness() { return false; }
    public static boolean isRunningInUserTestHarness() { return false; }
    public void dumpPackageState(java.io.FileDescriptor fd, String packageName) {}
    public void setWatchHeapLimit(long pssSize) {}
    public void clearWatchHeapLimit() {}
    @Deprecated public boolean isInLockTaskMode() { return false; }
    public int getLockTaskModeState() { return LOCK_TASK_MODE_NONE; }
    public static void setVrThread(int tid) {}
    public void appNotResponding(String message) {}

    public static class MemoryInfo implements Parcelable {
        public long advertisedMem;
        public long availMem;
        public boolean lowMemory;
        public long threshold;
        public long totalMem;

        public MemoryInfo() {}

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(advertisedMem);
            dest.writeLong(availMem);
            dest.writeLong(totalMem);
            dest.writeLong(threshold);
            dest.writeInt(lowMemory ? 1 : 0);
        }

        public void readFromParcel(Parcel source) {
            advertisedMem = source.readLong();
            availMem = source.readLong();
            totalMem = source.readLong();
            threshold = source.readLong();
            lowMemory = source.readInt() != 0;
        }

        public static final Parcelable.Creator<MemoryInfo> CREATOR = new Parcelable.Creator<MemoryInfo>() {
            public MemoryInfo createFromParcel(Parcel source) {
                MemoryInfo m = new MemoryInfo();
                m.readFromParcel(source);
                return m;
            }

            public MemoryInfo[] newArray(int size) { return new MemoryInfo[size]; }
        };
    }

    public static class RunningAppProcessInfo implements Parcelable {
        public static final int IMPORTANCE_BACKGROUND = 400;
        public static final int IMPORTANCE_CACHED = 400;
        public static final int IMPORTANCE_CANT_SAVE_STATE = 350;
        public static final int IMPORTANCE_EMPTY = 500;
        public static final int IMPORTANCE_FOREGROUND = 100;
        public static final int IMPORTANCE_FOREGROUND_SERVICE = 125;
        public static final int IMPORTANCE_GONE = 1000;
        public static final int IMPORTANCE_PERCEPTIBLE = 230;
        public static final int IMPORTANCE_PERCEPTIBLE_PRE_26 = 130;
        public static final int IMPORTANCE_SERVICE = 300;
        public static final int IMPORTANCE_TOP_SLEEPING = 325;
        public static final int IMPORTANCE_TOP_SLEEPING_PRE_28 = 150;
        public static final int IMPORTANCE_VISIBLE = 200;
        public static final int REASON_PROVIDER_IN_USE = 1;
        public static final int REASON_SERVICE_IN_USE = 2;
        public static final int REASON_UNKNOWN = 0;

        public int importance;
        public int importanceReasonCode;
        public android.content.ComponentName importanceReasonComponent;
        public int importanceReasonPid;
        public int lastTrimLevel;
        public int lru;
        public int pid;
        public String[] pkgList;
        public String processName;
        public int uid;

        public RunningAppProcessInfo() {
            importance = IMPORTANCE_FOREGROUND;
            importanceReasonCode = REASON_UNKNOWN;
        }

        public RunningAppProcessInfo(String pProcessName, int pPid, String[] pArr) {
            this();
            processName = pProcessName;
            pid = pPid;
            pkgList = pArr;
        }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(processName);
            dest.writeInt(pid);
            dest.writeInt(uid);
            dest.writeStringArray(pkgList);
            dest.writeInt(lastTrimLevel);
            dest.writeInt(importance);
            dest.writeInt(lru);
            dest.writeInt(importanceReasonCode);
            dest.writeInt(importanceReasonPid);
        }

        public void readFromParcel(Parcel source) {
            processName = source.readString();
            pid = source.readInt();
            uid = source.readInt();
            pkgList = source.createStringArray();
            lastTrimLevel = source.readInt();
            importance = source.readInt();
            lru = source.readInt();
            importanceReasonCode = source.readInt();
            importanceReasonPid = source.readInt();
        }

        public static final Parcelable.Creator<RunningAppProcessInfo> CREATOR =
                new Parcelable.Creator<RunningAppProcessInfo>() {
            public RunningAppProcessInfo createFromParcel(Parcel source) {
                RunningAppProcessInfo r = new RunningAppProcessInfo();
                r.readFromParcel(source);
                return r;
            }

            public RunningAppProcessInfo[] newArray(int size) { return new RunningAppProcessInfo[size]; }
        };
    }
}
