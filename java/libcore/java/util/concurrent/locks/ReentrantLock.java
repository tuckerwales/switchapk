package java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ReentrantLock implements Lock, java.io.Serializable {
    private Thread owner;
    private int holds;

    public ReentrantLock() {
    }

    public ReentrantLock(boolean fair) {
    }

    public void lock() {
        synchronized (this) {
            Thread me = Thread.currentThread();
            while (owner != null && owner != me) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            owner = me;
            holds++;
        }
    }

    public void lockInterruptibly() throws InterruptedException {
        synchronized (this) {
            Thread me = Thread.currentThread();
            while (owner != null && owner != me) {
                wait();
            }
            owner = me;
            holds++;
        }
    }

    public synchronized boolean tryLock() {
        Thread me = Thread.currentThread();
        if (owner == null || owner == me) {
            owner = me;
            holds++;
            return true;
        }
        return false;
    }

    public synchronized boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        Thread me = Thread.currentThread();
        while (owner != null && owner != me) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        owner = me;
        holds++;
        return true;
    }

    public synchronized void unlock() {
        if (owner != Thread.currentThread()) {
            throw new IllegalMonitorStateException();
        }
        if (--holds == 0) {
            owner = null;
            notifyAll();
        }
    }

    public Condition newCondition() {
        return new ConditionObject();
    }

    public synchronized int getHoldCount() {
        return owner == Thread.currentThread() ? holds : 0;
    }

    public synchronized boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    public synchronized boolean isLocked() {
        return owner != null;
    }

    public final boolean isFair() {
        return false;
    }

    public final boolean hasQueuedThreads() {
        return false;
    }

    public String toString() {
        Thread o = owner;
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }

    private int releaseAll() {
        synchronized (this) {
            if (owner != Thread.currentThread()) {
                throw new IllegalMonitorStateException();
            }
            int h = holds;
            holds = 0;
            owner = null;
            notifyAll();
            return h;
        }
    }

    private void reacquire(int h) {
        synchronized (this) {
            Thread me = Thread.currentThread();
            while (owner != null && owner != me) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            owner = me;
            holds = h;
        }
    }

    class ConditionObject implements Condition {
        private int signals;

        public void await() throws InterruptedException {
            awaitNanos(0);
        }

        public void awaitUninterruptibly() {
            try {
                awaitNanos(0);
            } catch (InterruptedException e) {
            }
        }

        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            long start = System.nanoTime();
            int h;
            synchronized (this) {
                h = releaseAll();
                try {
                    if (nanosTimeout > 0) {
                        TimeUnit.NANOSECONDS.timedWait(this, nanosTimeout);
                    } else {
                        wait();
                    }
                } finally {
                    reacquireLater = h;
                }
            }
            reacquire(h);
            return nanosTimeout - (System.nanoTime() - start);
        }

        private int reacquireLater;

        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return awaitNanos(Math.max(1, unit.toNanos(time))) > 0;
        }

        public boolean awaitUntil(Date deadline) throws InterruptedException {
            long ms = deadline.getTime() - System.currentTimeMillis();
            return ms > 0 && await(ms, TimeUnit.MILLISECONDS);
        }

        public synchronized void signal() {
            notify();
        }

        public synchronized void signalAll() {
            notifyAll();
        }
    }
}
