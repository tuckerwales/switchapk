package android.os;

public class Process {
    public static final int ROOT_UID = 0;
    public static final int SYSTEM_UID = 1000;
    public static final int PHONE_UID = 1001;
    public static final int SHELL_UID = 2000;
    public static final int FIRST_APPLICATION_UID = 10000;
    public static final int LAST_APPLICATION_UID = 19999;
    public static final int INVALID_UID = -1;
    public static final int BLUETOOTH_UID = 1002;
    public static final int WIFI_UID = 1010;

    public static final int THREAD_PRIORITY_DEFAULT = 0;
    public static final int THREAD_PRIORITY_LOWEST = 19;
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;
    public static final int THREAD_PRIORITY_VIDEO = -10;
    public static final int THREAD_PRIORITY_AUDIO = -16;
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;
    public static final int THREAD_PRIORITY_MORE_FAVORABLE = -1;
    public static final int THREAD_PRIORITY_LESS_FAVORABLE = +1;
    public static final int SIGNAL_QUIT = 3;
    public static final int SIGNAL_KILL = 9;
    public static final int SIGNAL_USR1 = 10;

    private static final long START = SystemClock.elapsedRealtime();

    public static final long getStartElapsedRealtime() { return START; }
    public static final long getStartUptimeMillis() { return START; }
    public static final long getElapsedCpuTime() { return SystemClock.elapsedRealtime() - START; }
    public static final int myPid() { return 4242; }
    public static final int myPpid() { return 1; }
    public static final int myTid() { return (int) Thread.currentThread().getId() + 4242; }
    public static final int myUid() { return FIRST_APPLICATION_UID; }
    public static UserHandle myUserHandle() { return UserHandle.CURRENT_USER; }
    public static boolean isApplicationUid(int uid) { return uid >= FIRST_APPLICATION_UID && uid <= LAST_APPLICATION_UID; }
    public static final boolean isIsolated() { return false; }
    public static final boolean isSdkSandbox() { return false; }
    public static final int getUidForName(String name) { return -1; }
    public static final int getGidForName(String name) { return -1; }
    public static final void setThreadPriority(int tid, int priority) {}
    public static final void setThreadPriority(int priority) {}
    public static final int getThreadPriority(int tid) { return THREAD_PRIORITY_DEFAULT; }
    public static final boolean supportsProcesses() { return false; }
    public static final boolean is64Bit() { return true; }
    public static final int getThreadScheduler(int tid) { return 0; }
    public static final String myProcessName() { return ActivityThreadHook.processName(); }

    public static final void killProcess(int pid) {
        if (pid == myPid()) System.exit(0);
    }

    public static final void sendSignal(int pid, int signal) {
        if (pid == myPid() && (signal == SIGNAL_KILL || signal == SIGNAL_QUIT)) System.exit(0);
    }

    /** Lets android.app.ActivityThread report the process (package) name. */
    public static final class ActivityThreadHook {
        static String sName = "app";
        public static void setProcessName(String n) { sName = n; }
        static String processName() { return sName; }
    }
}
