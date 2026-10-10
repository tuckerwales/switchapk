package libcore.io;

/** Writes one line to the platform log (logcat on Android, the switchapk log here). */
public final class Logcat {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6;

    private Logcat() {
    }

    public static native void println(int priority, String tag, String msg);
}
