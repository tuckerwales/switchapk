package java.lang;

import java.util.HashMap;

public class Thread implements Runnable {
    public static final int MIN_PRIORITY = 1;
    public static final int NORM_PRIORITY = 5;
    public static final int MAX_PRIORITY = 10;

    private volatile long vmThread;
    private volatile String name;
    private int priority = NORM_PRIORITY;
    private boolean daemon;
    private Runnable target;
    private ThreadGroup group;
    private long stackSize;
    private boolean started;
    private final long tid;
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;
    private ClassLoader contextClassLoader;
    HashMap<ThreadLocal<?>, Object> threadLocals;
    HashMap<ThreadLocal<?>, Object> inheritableThreadLocals;

    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;
    private static long threadSeqNumber;
    private static int threadInitNumber;

    public enum State {
        NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED
    }

    public interface UncaughtExceptionHandler {
        void uncaughtException(Thread t, Throwable e);
    }

    private static synchronized long nextThreadID() {
        return ++threadSeqNumber;
    }

    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    /** Used by the VM to create the main thread object. */
    private Thread(long vmThread, String name) {
        this.vmThread = vmThread;
        this.name = name;
        this.tid = nextThreadID();
        this.group = ThreadGroup.MAIN;
        this.started = true;
    }

    public Thread() {
        this(null, null, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(Runnable target) {
        this(null, target, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(ThreadGroup group, Runnable target) {
        this(group, target, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(String name) {
        this(null, null, name, 0);
    }

    public Thread(ThreadGroup group, String name) {
        this(group, null, name, 0);
    }

    public Thread(Runnable target, String name) {
        this(null, target, name, 0);
    }

    public Thread(ThreadGroup group, Runnable target, String name) {
        this(group, target, name, 0);
    }

    public Thread(ThreadGroup group, Runnable target, String name, long stackSize) {
        Thread parent = currentThread();
        this.group = group != null ? group : (parent != null ? parent.getThreadGroup() : ThreadGroup.MAIN);
        this.target = target;
        this.name = name == null ? "Thread-" + nextThreadNum() : name;
        this.stackSize = stackSize;
        this.tid = nextThreadID();
        if (parent != null) {
            this.daemon = parent.daemon;
            this.priority = parent.priority;
            this.contextClassLoader = parent.contextClassLoader;
            if (parent.inheritableThreadLocals != null) {
                inheritableThreadLocals = new HashMap<ThreadLocal<?>, Object>();
                for (java.util.Map.Entry<ThreadLocal<?>, Object> e : parent.inheritableThreadLocals.entrySet()) {
                    @SuppressWarnings("unchecked")
                    InheritableThreadLocal<Object> itl = (InheritableThreadLocal<Object>) e.getKey();
                    inheritableThreadLocals.put(itl, itl.childValue(e.getValue()));
                }
            }
        }
    }

    public static native Thread currentThread();

    public static native void yield();

    public static void sleep(long millis) throws InterruptedException {
        sleep(millis, 0);
    }

    public static native void sleep(long millis, int nanos) throws InterruptedException;

    public static void onSpinWait() {
    }

    public synchronized void start() {
        if (started) {
            throw new IllegalThreadStateException("Thread already started");
        }
        started = true;
        nativeStart(stackSize);
    }

    private native void nativeStart(long stackSize);

    public void run() {
        if (target != null) {
            target.run();
        }
    }

    @Deprecated
    public final void stop() {
        throw new UnsupportedOperationException();
    }

    public void interrupt() {
        interruptNative();
    }

    private native void interruptNative();

    public static native boolean interrupted();

    public boolean isInterrupted() {
        return isInterruptedNative();
    }

    private native boolean isInterruptedNative();

    public final boolean isAlive() {
        return vmThread != 0;
    }

    public final void setPriority(int newPriority) {
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException("Priority out of range: " + newPriority);
        }
        priority = newPriority;
    }

    public final int getPriority() {
        return priority;
    }

    public final synchronized void setName(String name) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public final ThreadGroup getThreadGroup() {
        return isAlive() || !started ? group : null;
    }

    public static int activeCount() {
        return 1;
    }

    public final void join(long millis) throws InterruptedException {
        synchronized (this) {
            if (millis < 0) {
                throw new IllegalArgumentException("timeout value is negative");
            }
            if (millis == 0) {
                while (isAlive()) {
                    wait(0);
                }
            } else {
                long base = System.currentTimeMillis();
                long now = 0;
                while (isAlive()) {
                    long delay = millis - now;
                    if (delay <= 0) {
                        break;
                    }
                    wait(delay);
                    now = System.currentTimeMillis() - base;
                }
            }
        }
    }

    public final void join(long millis, int nanos) throws InterruptedException {
        join(millis + (nanos > 0 ? 1 : 0));
    }

    public final void join() throws InterruptedException {
        join(0);
    }

    public static void dumpStack() {
        new Exception("Stack trace").printStackTrace();
    }

    public final void setDaemon(boolean on) {
        if (isAlive()) {
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void checkAccess() {
    }

    public String toString() {
        ThreadGroup g = getThreadGroup();
        return "Thread[" + getName() + "," + getPriority() + "," + (g != null ? g.getName() : "") + "]";
    }

    public ClassLoader getContextClassLoader() {
        return contextClassLoader != null ? contextClassLoader : ClassLoader.getSystemClassLoader();
    }

    public void setContextClassLoader(ClassLoader cl) {
        contextClassLoader = cl;
    }

    public static native boolean holdsLock(Object obj);

    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[0];
    }

    public static java.util.Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return new HashMap<Thread, StackTraceElement[]>();
    }

    public long getId() {
        return tid;
    }

    public State getState() {
        if (!started) {
            return State.NEW;
        }
        return isAlive() ? State.RUNNABLE : State.TERMINATED;
    }

    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        defaultUncaughtExceptionHandler = eh;
    }

    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }

    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler != null ? uncaughtExceptionHandler : group;
    }

    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        uncaughtExceptionHandler = eh;
    }

    /** Called by the VM when run() throws. */
    public final void dispatchUncaughtException(Throwable e) {
        UncaughtExceptionHandler h = getUncaughtExceptionHandler();
        if (h == null) {
            h = defaultUncaughtExceptionHandler;
        }
        if (h != null) {
            h.uncaughtException(this, e);
        } else {
            System.err.print("Exception in thread \"" + getName() + "\" ");
            e.printStackTrace(System.err);
        }
    }

    public Thread(ThreadGroup group, Runnable target, String name, long stackSize, boolean inheritThreadLocals) {
        this(group, target, name, stackSize);
    }

    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Deprecated
    public int countStackFrames() {
        throw new UnsupportedOperationException();
    }

    /* Thread enumeration is not tracked; only the calling thread is reported. */
    public static int enumerate(Thread[] tarray) {
        if (tarray.length == 0) {
            return 0;
        }
        tarray[0] = currentThread();
        return 1;
    }

    @Deprecated
    public void destroy() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    public final void resume() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    public final void suspend() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    public final void stop(Throwable obj) {
        throw new UnsupportedOperationException();
    }
}
