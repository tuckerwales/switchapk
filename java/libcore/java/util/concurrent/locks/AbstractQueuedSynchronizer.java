package java.util.concurrent.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * AQS on Java monitors: threads that cannot acquire wait on this object's monitor and are woken by release.
 * The queue is a plain list of the waiting threads, kept only for the inspection methods. The serial form matches
 * OpenJDK: the only serialized field is {@code state} (the queue fields are transient there too).
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {
    private static final long serialVersionUID = 7373984972572414691L;

    private volatile int state;

    /** Threads waiting in acquire, with their mode; null when nobody has waited yet (and after deserialization). */
    private transient ArrayList<Thread> queued;
    private transient ArrayList<Boolean> queuedShared;
    private transient boolean contended;

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

    private boolean tryMode(boolean shared, int arg) {
        return shared ? tryAcquireShared(arg) >= 0 : tryAcquire(arg);
    }

    private void enqueue(Thread t, boolean shared) {
        if (queued == null) {
            queued = new ArrayList<Thread>();
            queuedShared = new ArrayList<Boolean>();
        }
        queued.add(t);
        queuedShared.add(Boolean.valueOf(shared));
        contended = true;
    }

    private void dequeue(Thread t) {
        int i = queued.indexOf(t);
        if (i >= 0) {
            queued.remove(i);
            queuedShared.remove(i);
        }
    }

    /**
     * Waits until tryAcquire(Shared) succeeds. deadline is a System.nanoTime() value or 0 for none. Returns true when
     * acquired, false on timeout; throws when interruptible and interrupted, else re-asserts the interrupt status.
     */
    private boolean doAcquire(boolean shared, int arg, boolean interruptible, long deadline)
            throws InterruptedException {
        if (interruptible && Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (tryMode(shared, arg)) {
            return true;
        }
        Thread me = Thread.currentThread();
        boolean interrupted = false;
        synchronized (this) {
            enqueue(me, shared);
            try {
                while (!tryMode(shared, arg)) {
                    try {
                        if (deadline != 0) {
                            long left = deadline - System.nanoTime();
                            if (left <= 0) {
                                return false;
                            }
                            TimeUnit.NANOSECONDS.timedWait(this, left);
                        } else {
                            wait();
                        }
                    } catch (InterruptedException e) {
                        if (interruptible) {
                            throw e;
                        }
                        interrupted = true;
                    }
                }
            } finally {
                dequeue(me);
            }
        }
        if (interrupted) {
            me.interrupt();
        }
        return true;
    }

    private void wakeAll() {
        synchronized (this) {
            notifyAll();
        }
    }

    public final void acquire(int arg) {
        try {
            doAcquire(false, arg, false, 0);
        } catch (InterruptedException e) {
            // not thrown when uninterruptible
        }
    }

    public final void acquireInterruptibly(int arg) throws InterruptedException {
        doAcquire(false, arg, true, 0);
    }

    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (nanosTimeout <= 0L) {
            return tryAcquire(arg);
        }
        long deadline = System.nanoTime() + nanosTimeout;
        return doAcquire(false, arg, true, deadline == 0 ? 1 : deadline);
    }

    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            wakeAll();
            return true;
        }
        return false;
    }

    public final void acquireShared(int arg) {
        try {
            doAcquire(true, arg, false, 0);
        } catch (InterruptedException e) {
            // not thrown when uninterruptible
        }
    }

    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        doAcquire(true, arg, true, 0);
    }

    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (nanosTimeout <= 0L) {
            return tryAcquireShared(arg) >= 0;
        }
        long deadline = System.nanoTime() + nanosTimeout;
        return doAcquire(true, arg, true, deadline == 0 ? 1 : deadline);
    }

    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            wakeAll();
            return true;
        }
        return false;
    }

    // ---- queue inspection ----

    public final synchronized boolean hasQueuedThreads() {
        return queued != null && !queued.isEmpty();
    }

    public final synchronized boolean hasContended() {
        return contended;
    }

    public final synchronized Thread getFirstQueuedThread() {
        return (queued == null || queued.isEmpty()) ? null : queued.get(0);
    }

    public final synchronized boolean isQueued(Thread thread) {
        if (thread == null) {
            throw new NullPointerException();
        }
        return queued != null && queued.contains(thread);
    }

    public final synchronized boolean hasQueuedPredecessors() {
        Thread first = getFirstQueuedThread();
        return first != null && first != Thread.currentThread();
    }

    public final synchronized int getQueueLength() {
        return queued == null ? 0 : queued.size();
    }

    private synchronized Collection<Thread> queuedThreads(int mode) {
        ArrayList<Thread> list = new ArrayList<Thread>();
        if (queued != null) {
            for (int i = queued.size() - 1; i >= 0; i--) {
                boolean shared = queuedShared.get(i).booleanValue();
                if (mode == 0 || (mode == 2) == shared) {
                    list.add(queued.get(i));
                }
            }
        }
        return list;
    }

    public final Collection<Thread> getQueuedThreads() {
        return queuedThreads(0);
    }

    public final Collection<Thread> getExclusiveQueuedThreads() {
        return queuedThreads(1);
    }

    public final Collection<Thread> getSharedQueuedThreads() {
        return queuedThreads(2);
    }

    public String toString() {
        return super.toString() + "[State = " + getState() + ", " + (hasQueuedThreads() ? "non" : "") + "empty queue]";
    }

    // ---- conditions ----

    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    private ConditionObject checkOwned(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition;
    }

    public final boolean hasWaiters(ConditionObject condition) {
        return checkOwned(condition).hasWaiters();
    }

    public final int getWaitQueueLength(ConditionObject condition) {
        return checkOwned(condition).getWaitQueueLength();
    }

    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        return checkOwned(condition).getWaitingThreads();
    }

    /** Releases every hold for a condition wait; returns the state to restore. */
    final int fullyRelease() {
        if (isHeldExclusively()) {
            int saved = getState();
            if (release(saved)) {
                return saved;
            }
        }
        throw new IllegalMonitorStateException();
    }

    final void reacquire(int saved) {
        acquire(saved);
    }

    /** One record per thread waiting on a ConditionObject; signal marks the oldest unsignalled one. */
    private static final class Waiter {
        final Thread thread = Thread.currentThread();
        boolean signalled;
    }

    /** Condition on this synchronizer: waiters block on the condition's monitor after fully releasing the lock. */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;

        private transient ArrayList<Waiter> waiters;

        public ConditionObject() {
        }

        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        private void checkHeld() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
        }

        public final void signal() {
            checkHeld();
            synchronized (this) {
                if (waiters != null) {
                    for (Waiter w : waiters) {
                        if (!w.signalled) {
                            w.signalled = true;
                            notifyAll();
                            return;
                        }
                    }
                }
            }
        }

        public final void signalAll() {
            checkHeld();
            synchronized (this) {
                if (waiters != null) {
                    for (Waiter w : waiters) {
                        w.signalled = true;
                    }
                    notifyAll();
                }
            }
        }

        /**
         * Waits for a signal. deadline is a System.nanoTime() value or 0 for none. Returns true when signalled
         * (false on timeout). The lock is reacquired before returning or throwing.
         */
        private boolean doAwait(boolean interruptible, long deadline) throws InterruptedException {
            if (interruptible && Thread.interrupted()) {
                throw new InterruptedException();
            }
            Waiter w = new Waiter();
            boolean interrupted = false;
            boolean signalled;
            int saved;
            synchronized (this) {
                saved = fullyRelease();
                if (waiters == null) {
                    waiters = new ArrayList<Waiter>();
                }
                waiters.add(w);
                try {
                    while (!w.signalled) {
                        try {
                            if (deadline != 0) {
                                long left = deadline - System.nanoTime();
                                if (left <= 0) {
                                    break;
                                }
                                TimeUnit.NANOSECONDS.timedWait(this, left);
                            } else {
                                wait();
                            }
                        } catch (InterruptedException e) {
                            interrupted = true;
                            if (interruptible) {
                                break;
                            }
                        }
                    }
                } finally {
                    waiters.remove(w);
                }
                signalled = w.signalled;
            }
            reacquire(saved);
            if (interrupted) {
                if (interruptible && !signalled) {
                    throw new InterruptedException();
                }
                Thread.currentThread().interrupt();
            }
            return signalled;
        }

        public final void awaitUninterruptibly() {
            try {
                doAwait(false, 0);
            } catch (InterruptedException e) {
                // not thrown when uninterruptible
            }
        }

        public final void await() throws InterruptedException {
            doAwait(true, 0);
        }

        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            long nanos = nanosTimeout < 0L ? 0L : nanosTimeout;
            long deadline = System.nanoTime() + nanos;
            doAwait(true, deadline == 0 ? 1 : deadline);
            long remaining = deadline - System.nanoTime();
            return (remaining <= nanosTimeout) ? remaining : Long.MIN_VALUE;
        }

        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            long ms = deadline.getTime() - System.currentTimeMillis();
            return await(ms, TimeUnit.MILLISECONDS);
        }

        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(time);
            long deadline = System.nanoTime() + (nanos < 0L ? 0L : nanos);
            return doAwait(true, deadline == 0 ? 1 : deadline);
        }

        protected final boolean hasWaiters() {
            checkHeld();
            synchronized (this) {
                return waiters != null && !waiters.isEmpty();
            }
        }

        protected final int getWaitQueueLength() {
            checkHeld();
            synchronized (this) {
                return waiters == null ? 0 : waiters.size();
            }
        }

        protected final Collection<Thread> getWaitingThreads() {
            checkHeld();
            ArrayList<Thread> list = new ArrayList<Thread>();
            synchronized (this) {
                if (waiters != null) {
                    for (Waiter w : waiters) {
                        list.add(w.thread);
                    }
                }
            }
            return list;
        }
    }
}
