package java.util.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Reentrant mutual exclusion lock built like OpenJDK's: a Sync (AQS subclass) whose state is the hold count. The
 * class structure and serialVersionUIDs match OpenJDK so the serial form is byte-identical; a deserialized lock is
 * unlocked.
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;

    private final Sync sync;

    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /** Acquires when free or already held by this thread (reentrant), without waiting. */
        final boolean tryLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, 1)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (getExclusiveOwnerThread() == current) {
                if (++c < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(c);
                return true;
            }
            return false;
        }

        abstract boolean initialTryLock();

        final void lock() {
            if (!initialTryLock()) {
                acquire(1);
            }
        }

        final void lockInterruptibly() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (!initialTryLock()) {
                acquireInterruptibly(1);
            }
        }

        final boolean tryLockNanos(long nanos) throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return initialTryLock() || tryAcquireNanos(1, nanos);
        }

        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (getExclusiveOwnerThread() != Thread.currentThread()) {
                throw new IllegalMonitorStateException();
            }
            boolean free = (c == 0);
            if (free) {
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        /** Reconstitutes the lock unlocked, whatever state was written. */
        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0);
        }
    }

    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        final boolean initialTryLock() {
            Thread current = Thread.currentThread();
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(current);
                return true;
            } else if (getExclusiveOwnerThread() == current) {
                int c = getState() + 1;
                if (c < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(c);
                return true;
            }
            return false;
        }

        protected final boolean tryAcquire(int acquires) {
            if (getState() == 0 && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
    }

    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        final boolean initialTryLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedThreads() && compareAndSetState(0, 1)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (getExclusiveOwnerThread() == current) {
                if (++c < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(c);
                return true;
            }
            return false;
        }

        protected final boolean tryAcquire(int acquires) {
            if (getState() == 0 && !hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
    }

    public ReentrantLock() {
        sync = new NonfairSync();
    }

    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    public void lock() {
        sync.lock();
    }

    public void lockInterruptibly() throws InterruptedException {
        sync.lockInterruptibly();
    }

    public boolean tryLock() {
        return sync.tryLock();
    }

    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryLockNanos(unit.toNanos(timeout));
    }

    public void unlock() {
        sync.release(1);
    }

    public Condition newCondition() {
        return sync.newCondition();
    }

    public int getHoldCount() {
        return sync.getHoldCount();
    }

    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    public boolean isLocked() {
        return sync.isLocked();
    }

    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    protected Thread getOwner() {
        return sync.getOwner();
    }

    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    private AbstractQueuedSynchronizer.ConditionObject conditionOf(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return (AbstractQueuedSynchronizer.ConditionObject) condition;
    }

    public boolean hasWaiters(Condition condition) {
        return sync.hasWaiters(conditionOf(condition));
    }

    public int getWaitQueueLength(Condition condition) {
        return sync.getWaitQueueLength(conditionOf(condition));
    }

    protected Collection<Thread> getWaitingThreads(Condition condition) {
        return sync.getWaitingThreads(conditionOf(condition));
    }

    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }
}
