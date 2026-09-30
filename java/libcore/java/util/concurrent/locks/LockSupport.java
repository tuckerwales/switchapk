package java.util.concurrent.locks;

import java.util.WeakHashMap;

public class LockSupport {
    private static final WeakHashMap<Thread, Object[]> permits = new WeakHashMap<Thread, Object[]>();

    private LockSupport() {
    }

    private static Object[] state(Thread t) {
        synchronized (permits) {
            Object[] s = permits.get(t);
            if (s == null) {
                s = new Object[] {Boolean.FALSE};
                permits.put(t, s);
            }
            return s;
        }
    }

    public static void unpark(Thread thread) {
        if (thread == null) {
            return;
        }
        Object[] s = state(thread);
        synchronized (s) {
            s[0] = Boolean.TRUE;
            s.notifyAll();
        }
    }

    public static void park() {
        parkNanos(0);
    }

    public static void park(Object blocker) {
        parkNanos(0);
    }

    public static void parkNanos(long nanos) {
        Object[] s = state(Thread.currentThread());
        synchronized (s) {
            if (s[0] != Boolean.TRUE) {
                try {
                    if (nanos > 0) {
                        s.wait(nanos / 1000000, (int) (nanos % 1000000));
                    } else {
                        s.wait();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            s[0] = Boolean.FALSE;
        }
    }

    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            parkNanos(nanos);
        }
    }

    public static void parkUntil(long deadline) {
        long ms = deadline - System.currentTimeMillis();
        if (ms > 0) {
            parkNanos(ms * 1000000);
        }
    }

    public static void parkUntil(Object blocker, long deadline) {
        parkUntil(deadline);
    }

    public static Object getBlocker(Thread t) {
        return null;
    }
}
