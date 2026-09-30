package java.util.concurrent.locks;

/** Minimal AQS supporting exclusive mode for subclasses that implement tryAcquire/tryRelease. */
public abstract class AbstractQueuedSynchronizer implements java.io.Serializable {
    private volatile int state;

    protected AbstractQueuedSynchronizer() {
    }

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    protected final synchronized boolean compareAndSetState(int expect, int update) {
        if (state == expect) {
            state = update;
            return true;
        }
        return false;
    }

    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    public final void acquire(int arg) {
        synchronized (this) {
            while (!tryAcquire(arg)) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public final void acquireInterruptibly(int arg) throws InterruptedException {
        synchronized (this) {
            while (!tryAcquire(arg)) {
                wait();
            }
        }
    }

    public final boolean release(int arg) {
        synchronized (this) {
            if (tryRelease(arg)) {
                notifyAll();
                return true;
            }
            return false;
        }
    }

    public final void acquireShared(int arg) {
        synchronized (this) {
            while (tryAcquireShared(arg) < 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        synchronized (this) {
            while (tryAcquireShared(arg) < 0) {
                wait();
            }
        }
    }

    public final boolean releaseShared(int arg) {
        synchronized (this) {
            if (tryReleaseShared(arg)) {
                notifyAll();
                return true;
            }
            return false;
        }
    }

    public final boolean hasQueuedThreads() {
        return false;
    }
}
