package android.os;

import android.util.Printer;

public final class Looper {
    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    private static Looper sMainLooper;

    final MessageQueue mQueue;
    final Thread mThread;
    private Printer mLogging;

    public static void prepare() { prepare(true); }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) throw new RuntimeException("Only one Looper may be created per thread");
        sThreadLocal.set(new Looper(quitAllowed, false));
    }

    public static void prepareMainLooper() {
        if (sThreadLocal.get() != null) throw new RuntimeException("Only one Looper may be created per thread");
        Looper l = new Looper(false, true);
        sThreadLocal.set(l);
        synchronized (Looper.class) {
            if (sMainLooper != null) throw new IllegalStateException("The main Looper has already been prepared.");
            sMainLooper = l;
        }
    }

    public static Looper getMainLooper() {
        synchronized (Looper.class) { return sMainLooper; }
    }

    public static void loop() {
        final Looper me = myLooper();
        if (me == null) throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        final MessageQueue queue = me.mQueue;
        for (;;) {
            Message msg = queue.next();
            if (msg == null) return;
            final Printer logging = me.mLogging;
            if (logging != null) logging.println(">>>>> Dispatching to " + msg.target + " " + msg.callback + ": " + msg.what);
            msg.target.dispatchMessage(msg);
            if (logging != null) logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            msg.recycleUnchecked();
        }
    }

    public static Looper myLooper() { return sThreadLocal.get(); }

    public static MessageQueue myQueue() { return myLooper().mQueue; }

    private Looper(boolean quitAllowed, boolean main) {
        mQueue = new MessageQueue(quitAllowed, main);
        mThread = Thread.currentThread();
    }

    public boolean isCurrentThread() { return Thread.currentThread() == mThread; }
    public void setMessageLogging(Printer printer) { mLogging = printer; }
    public void quit() { mQueue.quit(false); }
    public void quitSafely() { mQueue.quit(true); }
    public Thread getThread() { return mThread; }
    public MessageQueue getQueue() { return mQueue; }

    @Override
    public String toString() {
        return "Looper (" + mThread.getName() + ", tid " + mThread.getId() + ") {" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}
