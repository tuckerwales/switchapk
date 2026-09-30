package java.util.concurrent;

public class Semaphore implements java.io.Serializable {
    private int permits;

    public Semaphore(int permits) {
        this.permits = permits;
    }

    public Semaphore(int permits, boolean fair) {
        this.permits = permits;
    }

    public void acquire() throws InterruptedException {
        acquire(1);
    }

    public void acquireUninterruptibly() {
        acquireUninterruptibly(1);
    }

    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return tryAcquire(1, timeout, unit);
    }

    public void release() {
        release(1);
    }

    public synchronized void acquire(int n) throws InterruptedException {
        while (permits < n) {
            wait();
        }
        permits -= n;
    }

    public synchronized void acquireUninterruptibly(int n) {
        while (permits < n) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        permits -= n;
    }

    public synchronized boolean tryAcquire(int n) {
        if (permits >= n) {
            permits -= n;
            return true;
        }
        return false;
    }

    public synchronized boolean tryAcquire(int n, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (permits < n) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        permits -= n;
        return true;
    }

    public synchronized void release(int n) {
        permits += n;
        notifyAll();
    }

    public synchronized int availablePermits() {
        return permits;
    }

    public synchronized int drainPermits() {
        int p = permits;
        permits = 0;
        return p;
    }
}
