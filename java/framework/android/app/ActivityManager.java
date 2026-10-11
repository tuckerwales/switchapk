package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.util.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One app in one process with one task: process and task queries describe this app, memory figures
 * come from the VM heap, and there is nothing to kill or move.
 */
public class ActivityManager {
    public static final String ACTION_REPORT_HEAP_LIMIT = "android.app.action.REPORT_HEAP_LIMIT";
    public static final int LOCK_TASK_MODE_NONE = 0;
    public static final int LOCK_TASK_MODE_LOCKED = 1;
    public static final int LOCK_TASK_MODE_PINNED = 2;
    public static final String META_HOME_ALTERNATE = "android.app.home.alternate";
    public static final int MOVE_TASK_WITH_HOME = 1;
    public static final int MOVE_TASK_NO_USER_ACTION = 2;
    public static final int RECENT_WITH_EXCLUDED = 1;
    public static final int RECENT_IGNORE_UNAVAILABLE = 2;

    /** The console has 4 GiB; applications get about 3.2 GiB of it. */
    private static final long TOTAL_MEM = 3200L << 20;
    private static final int TASK_ID = 1;

    private final Context mContext;

    ActivityManager() {
        this(null);
    }

    ActivityManager(Context context) {
        mContext = context;
    }

    public int getMemoryClass() {
        return (int) Math.max(16, Runtime.getRuntime().maxMemory() >> 20);
    }

    public int getLargeMemoryClass() {
        return getMemoryClass();
    }

    public boolean isLowRamDevice() {
        return false;
    }

    private <T extends TaskInfo> T fillTask(T info) {
        ComponentName top = null;
        Intent base = null;
        if (mContext != null) {
            Intent launch = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
            if (launch != null) {
                base = launch;
                top = launch.getComponent();
            }
        }
        info.taskId = TASK_ID;
        info.isRunning = true;
        info.baseIntent = base;
        info.baseActivity = top;
        info.topActivity = top;
        info.origActivity = null;
        info.numActivities = 1;
        info.taskDescription = sTaskDescription != null ? new TaskDescription(sTaskDescription) : null;
        return info;
    }

    static TaskDescription sTaskDescription;

    @Deprecated
    public List<RecentTaskInfo> getRecentTasks(int maxNum, int flags) throws SecurityException {
        List<RecentTaskInfo> list = new ArrayList<RecentTaskInfo>();
        if (maxNum > 0) {
            RecentTaskInfo r = fillTask(new RecentTaskInfo());
            r.id = TASK_ID;
            r.persistentId = TASK_ID;
            r.affiliatedTaskId = TASK_ID;
            list.add(r);
        }
        return list;
    }

    public List<AppTask> getAppTasks() {
        List<AppTask> list = new ArrayList<AppTask>();
        list.add(new AppTask(this));
        return list;
    }

    public Size getAppTaskThumbnailSize() {
        return new Size(256, 144);
    }

    public int addAppTask(Activity activity, Intent intent, TaskDescription description, Bitmap thumbnail) {
        return -1;
    }

    @Deprecated
    public List<RunningTaskInfo> getRunningTasks(int maxNum) throws SecurityException {
        List<RunningTaskInfo> list = new ArrayList<RunningTaskInfo>();
        if (maxNum > 0) {
            RunningTaskInfo r = fillTask(new RunningTaskInfo());
            r.id = TASK_ID;
            r.numRunning = 1;
            list.add(r);
        }
        return list;
    }

    public void moveTaskToFront(int taskId, int flags) {
    }

    public void moveTaskToFront(int taskId, int flags, Bundle options) {
    }

    public boolean isActivityStartAllowedOnDisplay(Context context, int displayId, Intent intent) {
        return displayId == android.view.Display.DEFAULT_DISPLAY;
    }

    @Deprecated
    public List<RunningServiceInfo> getRunningServices(int maxNum) throws SecurityException {
        return new ArrayList<RunningServiceInfo>();
    }

    public PendingIntent getRunningServiceControlPanel(ComponentName service) throws SecurityException {
        return null;
    }

    public void getMemoryInfo(MemoryInfo outInfo) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        outInfo.totalMem = TOTAL_MEM;
        outInfo.advertisedMem = 4096L << 20;
        outInfo.availMem = Math.max(0, TOTAL_MEM - used - (512L << 20));
        outInfo.threshold = 256L << 20;
        outInfo.lowMemory = outInfo.availMem < outInfo.threshold;
    }

    public boolean clearApplicationUserData() {
        return false;
    }

    public List<ProcessErrorStateInfo> getProcessesInErrorState() {
        return null;
    }

    public boolean isBackgroundRestricted() {
        return false;
    }

    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        List<RunningAppProcessInfo> list = new ArrayList<RunningAppProcessInfo>();
        RunningAppProcessInfo info = new RunningAppProcessInfo();
        getMyMemoryState(info);
        list.add(info);
        return list;
    }

    public void setProcessStateSummary(byte[] state) {
    }

    // Element types are ApplicationStartInfo / ApplicationExitInfo on Android; there is no history here.
    @SuppressWarnings("rawtypes")
    public List getHistoricalProcessStartReasons(int maxNum) {
        return new ArrayList();
    }

    @SuppressWarnings("rawtypes")
    public void addApplicationStartInfoCompletionListener(java.util.concurrent.Executor executor,
            java.util.function.Consumer listener) {
    }

    @SuppressWarnings("rawtypes")
    public void removeApplicationStartInfoCompletionListener(java.util.function.Consumer listener) {
    }

    public void addStartInfoTimestamp(int key, long timestampNs) {
    }

    @SuppressWarnings("rawtypes")
    public List getHistoricalProcessExitReasons(String packageName, int pid, int maxNum) {
        return new ArrayList();
    }

    public static boolean isLowMemoryKillReportSupported() {
        return false;
    }

    public static void getMyMemoryState(RunningAppProcessInfo outState) {
        String name = ActivityThread.currentProcessName();
        outState.processName = name;
        outState.pid = Process.myPid();
        outState.uid = Process.myUid();
        outState.pkgList = name != null ? new String[] {name} : new String[0];
        outState.importance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        outState.lru = 0;
        outState.lastTrimLevel = 0;
        outState.importanceReasonCode = RunningAppProcessInfo.REASON_UNKNOWN;
        outState.importanceReasonPid = 0;
        outState.importanceReasonComponent = null;
    }

    public android.os.Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids) {
        android.os.Debug.MemoryInfo[] out = new android.os.Debug.MemoryInfo[pids.length];
        for (int i = 0; i < pids.length; i++) {
            out[i] = new android.os.Debug.MemoryInfo();
            if (pids[i] == Process.myPid()) {
                android.os.Debug.getMemoryInfo(out[i]);
            }
        }
        return out;
    }

    @Deprecated
    public void restartPackage(String packageName) {
    }

    public void killBackgroundProcesses(String packageName) {
    }

    public ConfigurationInfo getDeviceConfigurationInfo() {
        ConfigurationInfo info = new ConfigurationInfo();
        info.reqTouchScreen = android.content.res.Configuration.TOUCHSCREEN_FINGER;
        info.reqKeyboardType = android.content.res.Configuration.KEYBOARD_NOKEYS;
        info.reqNavigation = android.content.res.Configuration.NAVIGATION_DPAD;
        info.reqInputFeatures = ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV;
        info.reqGlEsVersion = 0x00030002;
        return info;
    }

    public int getLauncherLargeIconDensity() {
        return android.util.DisplayMetrics.DENSITY_XHIGH;
    }

    public int getLauncherLargeIconSize() {
        return 96;
    }

    public static boolean isUserAMonkey() {
        return false;
    }

    public static boolean isRunningInTestHarness() {
        return false;
    }

    public static boolean isRunningInUserTestHarness() {
        return false;
    }

    public void dumpPackageState(java.io.FileDescriptor fd, String packageName) {
    }

    public void setWatchHeapLimit(long pssSize) {
    }

    public void clearWatchHeapLimit() {
    }

    @Deprecated
    public boolean isInLockTaskMode() {
        return false;
    }

    public int getLockTaskModeState() {
        return LOCK_TASK_MODE_NONE;
    }

    public static void setVrThread(int tid) {
    }

    public void appNotResponding(String message) {
        android.util.Log.e("ActivityManager", "appNotResponding: " + message);
    }

    public static class TaskDescription implements Parcelable {
        private String mLabel;
        private Bitmap mIcon;
        private int mIconRes;
        private int mColorPrimary;
        private int mColorBackground;
        private int mStatusBarColor;
        private int mNavigationBarColor;

        public static final class Builder {
            private String mLabel;
            private int mIconRes;
            private int mPrimaryColor;
            private int mBackgroundColor;
            private int mStatusBarColor;
            private int mNavigationBarColor;

            public Builder() {
            }

            public Builder setLabel(String label) {
                mLabel = label;
                return this;
            }

            public Builder setIcon(int iconRes) {
                mIconRes = iconRes;
                return this;
            }

            public Builder setPrimaryColor(int color) {
                mPrimaryColor = color;
                return this;
            }

            public Builder setBackgroundColor(int color) {
                mBackgroundColor = color;
                return this;
            }

            public Builder setStatusBarColor(int color) {
                mStatusBarColor = color;
                return this;
            }

            public Builder setNavigationBarColor(int color) {
                mNavigationBarColor = color;
                return this;
            }

            public TaskDescription build() {
                TaskDescription td = new TaskDescription(mLabel, null, mIconRes, mPrimaryColor);
                td.mColorBackground = mBackgroundColor;
                td.mStatusBarColor = mStatusBarColor;
                td.mNavigationBarColor = mNavigationBarColor;
                return td;
            }
        }

        private static int checkOpaque(int colorPrimary) {
            if (colorPrimary != 0 && Color.alpha(colorPrimary) != 255) {
                throw new RuntimeException("A TaskDescription's primary color should be opaque");
            }
            return colorPrimary;
        }

        private TaskDescription(String label, Bitmap icon, int iconRes, int colorPrimary) {
            mLabel = label;
            mIcon = icon;
            mIconRes = iconRes;
            mColorPrimary = checkOpaque(colorPrimary);
        }

        @Deprecated
        public TaskDescription(String label, int iconRes, int colorPrimary) {
            this(label, null, iconRes, colorPrimary);
        }

        @Deprecated
        public TaskDescription(String label, int iconRes) {
            this(label, null, iconRes, 0);
        }

        @Deprecated
        public TaskDescription(String label) {
            this(label, null, 0, 0);
        }

        @Deprecated
        public TaskDescription() {
            this(null, null, 0, 0);
        }

        @Deprecated
        public TaskDescription(String label, Bitmap icon, int colorPrimary) {
            this(label, icon, 0, colorPrimary);
        }

        @Deprecated
        public TaskDescription(String label, Bitmap icon) {
            this(label, icon, 0, 0);
        }

        public TaskDescription(TaskDescription td) {
            mLabel = td.mLabel;
            mIcon = td.mIcon;
            mIconRes = td.mIconRes;
            mColorPrimary = td.mColorPrimary;
            mColorBackground = td.mColorBackground;
            mStatusBarColor = td.mStatusBarColor;
            mNavigationBarColor = td.mNavigationBarColor;
        }

        private TaskDescription(Parcel source) {
            readFromParcel(source);
        }

        public String getLabel() {
            return mLabel;
        }

        @Deprecated
        public Bitmap getIcon() {
            return mIcon;
        }

        public int getPrimaryColor() {
            return mColorPrimary;
        }

        public int getBackgroundColor() {
            return mColorBackground;
        }

        public int getStatusBarColor() {
            return mStatusBarColor;
        }

        public int getNavigationBarColor() {
            return mNavigationBarColor;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mLabel);
            dest.writeParcelable(mIcon, flags);
            dest.writeInt(mIconRes);
            dest.writeInt(mColorPrimary);
            dest.writeInt(mColorBackground);
            dest.writeInt(mStatusBarColor);
            dest.writeInt(mNavigationBarColor);
        }

        public void readFromParcel(Parcel source) {
            mLabel = source.readString();
            mIcon = source.readParcelable(null);
            mIconRes = source.readInt();
            mColorPrimary = source.readInt();
            mColorBackground = source.readInt();
            mStatusBarColor = source.readInt();
            mNavigationBarColor = source.readInt();
        }

        public static final Creator<TaskDescription> CREATOR = new Creator<TaskDescription>() {
            public TaskDescription createFromParcel(Parcel source) {
                return new TaskDescription(source);
            }

            public TaskDescription[] newArray(int size) {
                return new TaskDescription[size];
            }
        };

        public String toString() {
            return "TaskDescription Label: " + mLabel + " Icon: " + mIcon + " IconRes: " + mIconRes
                    + " colorPrimary: " + mColorPrimary + " colorBackground: " + mColorBackground
                    + " statusBarColor: " + mStatusBarColor + " navigationBarColor: " + mNavigationBarColor;
        }

        public int hashCode() {
            return Objects.hash(mLabel, mIcon, mIconRes, mColorPrimary, mColorBackground, mStatusBarColor,
                    mNavigationBarColor);
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof TaskDescription)) {
                return false;
            }
            TaskDescription o = (TaskDescription) obj;
            return Objects.equals(mLabel, o.mLabel) && Objects.equals(mIcon, o.mIcon) && mIconRes == o.mIconRes
                    && mColorPrimary == o.mColorPrimary && mColorBackground == o.mColorBackground
                    && mStatusBarColor == o.mStatusBarColor && mNavigationBarColor == o.mNavigationBarColor;
        }
    }

    public static class MemoryInfo implements Parcelable {
        public long advertisedMem;
        public long availMem;
        public long totalMem;
        public long threshold;
        public boolean lowMemory;

        public MemoryInfo() {
        }

        private MemoryInfo(Parcel source) {
            readFromParcel(source);
        }

        public int describeContents() {
            return 0;
        }

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

        public static final Creator<MemoryInfo> CREATOR = new Creator<MemoryInfo>() {
            public MemoryInfo createFromParcel(Parcel source) {
                return new MemoryInfo(source);
            }

            public MemoryInfo[] newArray(int size) {
                return new MemoryInfo[size];
            }
        };
    }

    public static class RunningAppProcessInfo implements Parcelable {
        public static final int IMPORTANCE_FOREGROUND = 100;
        public static final int IMPORTANCE_FOREGROUND_SERVICE = 125;
        public static final int IMPORTANCE_PERCEPTIBLE_PRE_26 = 130;
        public static final int IMPORTANCE_TOP_SLEEPING_PRE_28 = 150;
        public static final int IMPORTANCE_VISIBLE = 200;
        public static final int IMPORTANCE_PERCEPTIBLE = 230;
        public static final int IMPORTANCE_SERVICE = 300;
        public static final int IMPORTANCE_TOP_SLEEPING = 325;
        public static final int IMPORTANCE_CANT_SAVE_STATE = 350;
        public static final int IMPORTANCE_CACHED = 400;
        @Deprecated
        public static final int IMPORTANCE_BACKGROUND = IMPORTANCE_CACHED;
        @Deprecated
        public static final int IMPORTANCE_EMPTY = 500;
        public static final int IMPORTANCE_GONE = 1000;
        public static final int REASON_UNKNOWN = 0;
        public static final int REASON_PROVIDER_IN_USE = 1;
        public static final int REASON_SERVICE_IN_USE = 2;

        public String processName;
        public int pid;
        public int uid;
        public String[] pkgList;
        public int lastTrimLevel;
        public int importance;
        @Deprecated
        public int lru;
        public int importanceReasonCode;
        public int importanceReasonPid;
        public ComponentName importanceReasonComponent;

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

        private RunningAppProcessInfo(Parcel source) {
            readFromParcel(source);
        }

        public int describeContents() {
            return 0;
        }

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
            dest.writeParcelable(importanceReasonComponent, flags);
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
            importanceReasonComponent = source.readParcelable(null);
        }

        public static final Creator<RunningAppProcessInfo> CREATOR = new Creator<RunningAppProcessInfo>() {
            public RunningAppProcessInfo createFromParcel(Parcel source) {
                return new RunningAppProcessInfo(source);
            }

            public RunningAppProcessInfo[] newArray(int size) {
                return new RunningAppProcessInfo[size];
            }
        };
    }

    public static class RunningTaskInfo extends TaskInfo implements Parcelable {
        @Deprecated
        public int id;
        @Deprecated
        public Bitmap thumbnail;
        @Deprecated
        public CharSequence description;
        @Deprecated
        public int numRunning;

        public RunningTaskInfo() {
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(id);
            dest.writeInt(taskId);
            dest.writeInt(numRunning);
            dest.writeInt(numActivities);
        }

        public void readFromParcel(Parcel source) {
            id = source.readInt();
            taskId = source.readInt();
            numRunning = source.readInt();
            numActivities = source.readInt();
        }

        public static final Creator<RunningTaskInfo> CREATOR = new Creator<RunningTaskInfo>() {
            public RunningTaskInfo createFromParcel(Parcel source) {
                RunningTaskInfo r = new RunningTaskInfo();
                r.readFromParcel(source);
                return r;
            }

            public RunningTaskInfo[] newArray(int size) {
                return new RunningTaskInfo[size];
            }
        };
    }

    public static class RecentTaskInfo extends TaskInfo implements Parcelable {
        @Deprecated
        public int id;
        @Deprecated
        public int persistentId;
        @Deprecated
        public CharSequence description;
        @Deprecated
        public int affiliatedTaskId;

        public RecentTaskInfo() {
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(id);
            dest.writeInt(persistentId);
            dest.writeInt(affiliatedTaskId);
            dest.writeInt(taskId);
        }

        public void readFromParcel(Parcel source) {
            id = source.readInt();
            persistentId = source.readInt();
            affiliatedTaskId = source.readInt();
            taskId = source.readInt();
        }

        public static final Creator<RecentTaskInfo> CREATOR = new Creator<RecentTaskInfo>() {
            public RecentTaskInfo createFromParcel(Parcel source) {
                RecentTaskInfo r = new RecentTaskInfo();
                r.readFromParcel(source);
                return r;
            }

            public RecentTaskInfo[] newArray(int size) {
                return new RecentTaskInfo[size];
            }
        };
    }

    public static class AppTask {
        private final ActivityManager mManager;

        AppTask(ActivityManager manager) {
            mManager = manager;
        }

        public void finishAndRemoveTask() {
            ActivityThread.finishAllActivities();
        }

        public RecentTaskInfo getTaskInfo() {
            return mManager.getRecentTasks(1, 0).get(0);
        }

        public void moveToFront() {
        }

        public void startActivity(Context context, Intent intent, Bundle options) {
            context.startActivity(intent, options);
        }

        public void setExcludeFromRecents(boolean exclude) {
        }
    }

    public static class RunningServiceInfo implements Parcelable {
        public static final int FLAG_STARTED = 1;
        public static final int FLAG_FOREGROUND = 2;
        public static final int FLAG_SYSTEM_PROCESS = 4;
        public static final int FLAG_PERSISTENT_PROCESS = 8;

        public ComponentName service;
        public int pid;
        public int uid;
        public String process;
        @Deprecated
        public boolean foreground;
        public long activeSince;
        public boolean started;
        public int clientCount;
        public int crashCount;
        public long lastActivityTime;
        public long restarting;
        public int flags;
        public String clientPackage;
        public int clientLabel;

        public RunningServiceInfo() {
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(service, flags);
            dest.writeInt(pid);
            dest.writeInt(uid);
            dest.writeString(process);
            dest.writeInt(this.flags);
        }

        public void readFromParcel(Parcel source) {
            service = source.readParcelable(null);
            pid = source.readInt();
            uid = source.readInt();
            process = source.readString();
            flags = source.readInt();
        }

        public static final Creator<RunningServiceInfo> CREATOR = new Creator<RunningServiceInfo>() {
            public RunningServiceInfo createFromParcel(Parcel source) {
                RunningServiceInfo r = new RunningServiceInfo();
                r.readFromParcel(source);
                return r;
            }

            public RunningServiceInfo[] newArray(int size) {
                return new RunningServiceInfo[size];
            }
        };
    }

    public static class ProcessErrorStateInfo implements Parcelable {
        public static final int NO_ERROR = 0;
        public static final int CRASHED = 1;
        public static final int NOT_RESPONDING = 2;

        public int condition;
        public String processName;
        public int pid;
        public int uid;
        public String tag;
        public String shortMsg;
        public String longMsg;
        public String stackTrace;
        @Deprecated
        public byte[] crashData;

        public ProcessErrorStateInfo() {
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(condition);
            dest.writeString(processName);
            dest.writeInt(pid);
            dest.writeInt(uid);
            dest.writeString(tag);
            dest.writeString(shortMsg);
            dest.writeString(longMsg);
            dest.writeString(stackTrace);
        }

        public void readFromParcel(Parcel source) {
            condition = source.readInt();
            processName = source.readString();
            pid = source.readInt();
            uid = source.readInt();
            tag = source.readString();
            shortMsg = source.readString();
            longMsg = source.readString();
            stackTrace = source.readString();
        }

        public static final Creator<ProcessErrorStateInfo> CREATOR = new Creator<ProcessErrorStateInfo>() {
            public ProcessErrorStateInfo createFromParcel(Parcel source) {
                ProcessErrorStateInfo r = new ProcessErrorStateInfo();
                r.readFromParcel(source);
                return r;
            }

            public ProcessErrorStateInfo[] newArray(int size) {
                return new ProcessErrorStateInfo[size];
            }
        };
    }
}
