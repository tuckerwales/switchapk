package android.os;

public class HandlerThread extends Thread {
    int mPriority;
    int mTid = -1;
    Looper mLooper;
    private Handler mHandler;

    public HandlerThread(String name) {
        super(name);
        mPriority = Process.THREAD_PRIORITY_DEFAULT;
    }

    public HandlerThread(String name, int priority) {
        super(name);
        mPriority = priority;
    }

    protected void onLooperPrepared() {}

    @Override
    public void run() {
        mTid = Process.myTid();
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        onLooperPrepared();
        Looper.loop();
        mTid = -1;
    }

    public Looper getLooper() {
        if (!isAlive()) return null;
        synchronized (this) {
            while (isAlive() && mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException ignored) {}
            }
        }
        return mLooper;
    }

    public Handler getThreadHandler() {
        if (mHandler == null) mHandler = new Handler(getLooper());
        return mHandler;
    }

    public boolean quit() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quit();
            return true;
        }
        return false;
    }

    public boolean quitSafely() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quitSafely();
            return true;
        }
        return false;
    }

    public int getThreadId() { return mTid; }
}
