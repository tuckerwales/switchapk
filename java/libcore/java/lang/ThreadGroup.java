package java.lang;

public class ThreadGroup implements Thread.UncaughtExceptionHandler {
    static final ThreadGroup SYSTEM = new ThreadGroup(null, "system", true);
    static final ThreadGroup MAIN = new ThreadGroup(SYSTEM, "main", true);

    private final ThreadGroup parent;
    private final String name;
    private int maxPriority = Thread.MAX_PRIORITY;
    private boolean daemon;

    private ThreadGroup(ThreadGroup parent, String name, boolean internal) {
        this.parent = parent;
        this.name = name;
    }

    public ThreadGroup(String name) {
        this(Thread.currentThread().getThreadGroup(), name);
    }

    public ThreadGroup(ThreadGroup parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public final ThreadGroup getParent() {
        return parent;
    }

    public final int getMaxPriority() {
        return maxPriority;
    }

    public final void setMaxPriority(int pri) {
        maxPriority = pri;
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public int activeCount() {
        return 1;
    }

    public int enumerate(Thread[] list) {
        return 0;
    }

    public final void interrupt() {
    }

    public void uncaughtException(Thread t, Throwable e) {
        if (parent != null) {
            parent.uncaughtException(t, e);
            return;
        }
        Thread.UncaughtExceptionHandler ueh = Thread.getDefaultUncaughtExceptionHandler();
        if (ueh != null) {
            ueh.uncaughtException(t, e);
        } else {
            System.err.print("Exception in thread \"" + t.getName() + "\" ");
            e.printStackTrace(System.err);
        }
    }

    public String toString() {
        return getClass().getName() + "[name=" + getName() + ",maxpri=" + maxPriority + "]";
    }
}
