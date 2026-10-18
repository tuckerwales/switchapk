package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/**
 * Capability-based lock with write, read and optimistic read modes, on a monitor. Stamps follow the JDK's encoding:
 * the low 7 bits mark a read lock, bit 7 a write lock, the bits above a version that each write advances, so
 * validate() fails once a writer has acquired the lock after the stamp was issued.
 */
public class StampedLock implements java.io.Serializable {
    private static final long serialVersionUID = -6001602636862214147L;

    private static final long RBITS = (1L << 7) - 1;
    private static final long WBIT = 1L << 7;
    private static final long ABITS = RBITS | WBIT;
    private static final long SBITS = ~RBITS;
    private static final long ORIGIN = WBIT << 1;

    /** Version plus WBIT while write-locked; never carries reader bits (readers are counted separately). */
    private transient long state = ORIGIN;
    private transient int readers;

    public StampedLock() {
    }

    private void advance() {
        state += WBIT;
        if (state == 0L) {
            state = ORIGIN;
        }
    }

    private boolean writeLocked() {
        return (state & WBIT) != 0;
    }

    private long readStamp() {
        return state + Math.min(readers, (int) RBITS);
    }

    // ---- write mode

    private long acquireWrite(boolean interruptible, long deadline) throws InterruptedException {
        synchronized (this) {
            while (writeLocked() || readers > 0) {
                if (!await(interruptible, deadline)) {
                    return 0L;
                }
            }
            state += WBIT;
            return state;
        }
    }

    /** Waits on the monitor; false once the deadline (if any) passes. */
    private boolean await(boolean interruptible, long deadline) throws InterruptedException {
        if (deadline == 0L) {
            if (interruptible) {
                wait();
            } else {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
        long left = (deadline - System.nanoTime()) / 1000000;
        if (left <= 0) {
            return false;
        }
        wait(left);
        return true;
    }

    public long writeLock() {
        try {
            return acquireWrite(false, 0L);
        } catch (InterruptedException impossible) {
            return 0L;
        }
    }

    public synchronized long tryWriteLock() {
        if (writeLocked() || readers > 0) {
            return 0L;
        }
        state += WBIT;
        return state;
    }

    public long tryWriteLock(long time, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        long s = tryWriteLock();
        if (s != 0L || time <= 0L) {
            return s;
        }
        return acquireWrite(true, System.nanoTime() + unit.toNanos(time));
    }

    public long writeLockInterruptibly() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return acquireWrite(true, 0L);
    }

    // ---- read mode

    private long acquireRead(boolean interruptible, long deadline) throws InterruptedException {
        synchronized (this) {
            while (writeLocked()) {
                if (!await(interruptible, deadline)) {
                    return 0L;
                }
            }
            readers++;
            return readStamp();
        }
    }

    public long readLock() {
        try {
            return acquireRead(false, 0L);
        } catch (InterruptedException impossible) {
            return 0L;
        }
    }

    public synchronized long tryReadLock() {
        if (writeLocked()) {
            return 0L;
        }
        readers++;
        return readStamp();
    }

    public long tryReadLock(long time, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        long s = tryReadLock();
        if (s != 0L || time <= 0L) {
            return s;
        }
        return acquireRead(true, System.nanoTime() + unit.toNanos(time));
    }

    public long readLockInterruptibly() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return acquireRead(true, 0L);
    }

    // ---- optimistic reads and validation

    public synchronized long tryOptimisticRead() {
        return writeLocked() ? 0L : state & SBITS;
    }

    public synchronized boolean validate(long stamp) {
        return (stamp & SBITS) == (state & SBITS);
    }

    // ---- release

    public synchronized void unlockWrite(long stamp) {
        if (!writeLocked() || stamp != state) {
            throw new IllegalMonitorStateException();
        }
        advance();
        notifyAll();
    }

    public synchronized void unlockRead(long stamp) {
        if ((stamp & RBITS) == 0L || (stamp & SBITS) != (state & SBITS) || readers == 0) {
            throw new IllegalMonitorStateException();
        }
        if (--readers == 0) {
            notifyAll();
        }
    }

    public void unlock(long stamp) {
        if ((stamp & WBIT) != 0L) {
            unlockWrite(stamp);
        } else {
            unlockRead(stamp);
        }
    }

    // ---- conversions

    public synchronized long tryConvertToWriteLock(long stamp) {
        if ((stamp & SBITS) != (state & SBITS)) {
            return 0L;
        }
        if ((stamp & WBIT) != 0L) {
            return stamp; // already the writer
        }
        if ((stamp & RBITS) != 0L) {
            if (readers != 1) {
                return 0L;
            }
            readers = 0;
        } else if (readers > 0) {
            return 0L; // optimistic, but others hold read locks
        }
        state += WBIT;
        return state;
    }

    public synchronized long tryConvertToReadLock(long stamp) {
        if ((stamp & SBITS) != (state & SBITS)) {
            return 0L;
        }
        if ((stamp & WBIT) != 0L) {
            advance();
            readers++;
            notifyAll();
            return readStamp();
        }
        if ((stamp & RBITS) != 0L) {
            return stamp;
        }
        readers++;
        return readStamp();
    }

    public synchronized long tryConvertToOptimisticRead(long stamp) {
        if ((stamp & SBITS) != (state & SBITS)) {
            return 0L;
        }
        if ((stamp & WBIT) != 0L) {
            advance();
            notifyAll();
            return state;
        }
        if ((stamp & RBITS) != 0L) {
            if (readers == 0) {
                return 0L;
            }
            if (--readers == 0) {
                notifyAll();
            }
        }
        return state & SBITS;
    }

    public synchronized boolean tryUnlockWrite() {
        if (!writeLocked()) {
            return false;
        }
        advance();
        notifyAll();
        return true;
    }

    public synchronized boolean tryUnlockRead() {
        if (readers == 0) {
            return false;
        }
        if (--readers == 0) {
            notifyAll();
        }
        return true;
    }

    // ---- state

    public synchronized boolean isWriteLocked() {
        return writeLocked();
    }

    public synchronized boolean isReadLocked() {
        return readers > 0;
    }

    public static boolean isWriteLockStamp(long stamp) {
        return (stamp & ABITS) == WBIT;
    }

    public static boolean isReadLockStamp(long stamp) {
        return (stamp & RBITS) != 0L;
    }

    public static boolean isLockStamp(long stamp) {
        return (stamp & ABITS) != 0L;
    }

    public static boolean isOptimisticReadStamp(long stamp) {
        return (stamp & ABITS) == 0L && stamp != 0L;
    }

    public synchronized int getReadLockCount() {
        return readers;
    }

    public String toString() {
        String s;
        synchronized (this) {
            s = writeLocked() ? "[Write-locked]" : readers > 0 ? "[Read-locks:" + readers + "]" : "[Unlocked]";
        }
        return super.toString() + s;
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN;
    }

    // ---- Lock views

    public Lock asReadLock() {
        return new Lock() {
            public void lock() {
                readLock();
            }

            public void lockInterruptibly() throws InterruptedException {
                readLockInterruptibly();
            }

            public boolean tryLock() {
                return tryReadLock() != 0L;
            }

            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                return tryReadLock(time, unit) != 0L;
            }

            public void unlock() {
                if (!tryUnlockRead()) {
                    throw new IllegalMonitorStateException();
                }
            }

            public Condition newCondition() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public Lock asWriteLock() {
        return new Lock() {
            public void lock() {
                writeLock();
            }

            public void lockInterruptibly() throws InterruptedException {
                writeLockInterruptibly();
            }

            public boolean tryLock() {
                return tryWriteLock() != 0L;
            }

            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                return tryWriteLock(time, unit) != 0L;
            }

            public void unlock() {
                if (!tryUnlockWrite()) {
                    throw new IllegalMonitorStateException();
                }
            }

            public Condition newCondition() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public ReadWriteLock asReadWriteLock() {
        final Lock r = asReadLock(), w = asWriteLock();
        return new ReadWriteLock() {
            public Lock readLock() {
                return r;
            }

            public Lock writeLock() {
                return w;
            }
        };
    }
}
