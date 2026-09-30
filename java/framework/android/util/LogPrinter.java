package android.util;

public class LogPrinter implements Printer {
    private final int mPriority;
    private final String mTag;

    public LogPrinter(int priority, String tag) {
        mPriority = priority;
        mTag = tag;
    }

    public void println(String x) { Log.println(mPriority, mTag, x); }
}
