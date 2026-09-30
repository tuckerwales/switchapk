package android.os;

public class ConditionVariable {
    private volatile boolean mCondition;

    public ConditionVariable() { mCondition = false; }
    public ConditionVariable(boolean state) { mCondition = state; }

    public void open() {
        synchronized (this) {
            boolean old = mCondition;
            mCondition = true;
            if (!old) this.notifyAll();
        }
    }

    public void close() { synchronized (this) { mCondition = false; } }

    public void block() {
        synchronized (this) {
            while (!mCondition) {
                try {
                    this.wait();
                } catch (InterruptedException e) {}
            }
        }
    }

    public boolean block(long timeoutMs) {
        if (timeoutMs != 0) {
            synchronized (this) {
                long now = SystemClock.elapsedRealtime();
                long end = now + timeoutMs;
                while (!mCondition && now < end) {
                    try {
                        this.wait(end - now);
                    } catch (InterruptedException e) {}
                    now = SystemClock.elapsedRealtime();
                }
                return mCondition;
            }
        } else {
            this.block();
            return true;
        }
    }
}
