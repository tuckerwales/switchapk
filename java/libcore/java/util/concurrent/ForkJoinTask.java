package java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;

/**
 * A task run by a ForkJoinPool. fork() queues it on the current worker's pool (or the common pool); join()
 * returns its result, and a worker that has to wait first runs the task itself if it is still queued, then
 * other queued tasks, so nested fork/join never starves the pool. Status and result follow the JDK: join and
 * invoke rethrow the task's exception unchecked, get wraps it in ExecutionException.
 */
public abstract class ForkJoinTask<V> implements Future<V>, java.io.Serializable {
    private static final long serialVersionUID = -7721805057305804111L;

    static final int NORMAL = 1;
    static final int CANCELLED = 2;
    static final int EXCEPTIONAL = 3;

    volatile int status;
    private Throwable exception;
    /** The thread that recorded exception: a join from another thread rethrows a copy with this as cause. */
    private transient long thrower;
    private short tag;
    /** The pool whose queue holds this task after fork or submit, so join can take it back. */
    transient volatile ForkJoinPool queuedIn;

    public ForkJoinTask() {
    }

    // ---- completion

    /** Sets the status if still pending; wakes joiners. Returns the final status. */
    final int setCompletion(int s) {
        synchronized (this) {
            if (status == 0) {
                status = s;
                notifyAll();
            }
            return status;
        }
    }

    final int recordExceptionalCompletion(Throwable ex) {
        synchronized (this) {
            if (status == 0) {
                exception = ex;
                thrower = Thread.currentThread().getId();
                status = EXCEPTIONAL;
                notifyAll();
            }
            return status;
        }
    }

    /** CountedCompleter passes exceptions up its completer chain. */
    void internalPropagateException(Throwable ex) {
    }

    final int doExec() {
        if (status != 0) {
            return status;
        }
        boolean completed;
        try {
            completed = exec();
        } catch (Throwable ex) {
            int s = recordExceptionalCompletion(ex);
            internalPropagateException(ex);
            return s;
        }
        return completed ? setCompletion(NORMAL) : status;
    }

    /** Waits for completion, running this task or other queued work meanwhile. */
    final int awaitDone(boolean interruptible, long deadline) throws InterruptedException {
        Thread t = Thread.currentThread();
        ForkJoinWorkerThread w = t instanceof ForkJoinWorkerThread ? (ForkJoinWorkerThread) t : null;
        while (status == 0) {
            if (interruptible && Thread.interrupted()) {
                throw new InterruptedException();
            }
            // A pool's own workers take the task back and run it; outside threads may do so only for the common
            // pool (as the JDK), otherwise they wait for a worker.
            ForkJoinPool p = queuedIn;
            if (p != null && ((w != null && w.pool == p) || p == ForkJoinPool.commonPool()) && p.tryRemove(this)) {
                doExec();
                continue;
            }
            if (w != null) {
                ForkJoinTask<?> other = w.pool.pollTask();
                if (other != null) {
                    other.doExec();
                    w.pool.afterTask(other);
                    continue;
                }
            }
            long wait = w != null ? 1 : 0;
            if (deadline != 0) {
                long left = (deadline - System.nanoTime()) / 1000000;
                if (left <= 0) {
                    return status;
                }
                wait = wait == 0 ? left : Math.min(wait, left);
            }
            synchronized (this) {
                if (status == 0) {
                    try {
                        wait(wait);
                    } catch (InterruptedException e) {
                        if (interruptible) {
                            throw e;
                        }
                    }
                }
            }
        }
        return status;
    }

    private int awaitUninterruptibly() {
        try {
            return awaitDone(false, 0);
        } catch (InterruptedException impossible) {
            return status;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    static void sneakyRethrow(Throwable t) {
        ForkJoinTask.<RuntimeException>sneakyThrow(t);
    }

    /** As the JDK: an exception thrown in another thread is rethrown as a new instance of its class (through a
     * public (Throwable) constructor, else the public no-arg one plus initCause), so the joiner's stack shows. */
    private Throwable joinException() {
        Throwable ex = exception;
        if (ex == null || thrower == Thread.currentThread().getId()) {
            return ex;
        }
        try {
            java.lang.reflect.Constructor<?> noArg = null;
            for (java.lang.reflect.Constructor<?> c : ex.getClass().getConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                if (ps.length == 0) {
                    noArg = c;
                } else if (ps.length == 1 && ps[0] == Throwable.class) {
                    return (Throwable) c.newInstance(ex);
                }
            }
            if (noArg != null) {
                Throwable wx = (Throwable) noArg.newInstance();
                wx.initCause(ex);
                return wx;
            }
        } catch (Exception ignore) {
        }
        return ex;
    }

    private V reportJoin(int s) {
        if (s == CANCELLED) {
            throw new CancellationException();
        }
        if (s == EXCEPTIONAL) {
            ForkJoinTask.<RuntimeException>sneakyThrow(joinException());
        }
        return getRawResult();
    }

    private V reportGet(int s) throws ExecutionException {
        if (s == CANCELLED) {
            throw new CancellationException();
        }
        if (s == EXCEPTIONAL) {
            throw new ExecutionException(exception);
        }
        return getRawResult();
    }

    // ---- public API

    public final ForkJoinTask<V> fork() {
        Thread t = Thread.currentThread();
        if (t instanceof ForkJoinWorkerThread) {
            ((ForkJoinWorkerThread) t).pool.push(this, true);
        } else {
            ForkJoinPool.commonPool().push(this, false);
        }
        return this;
    }

    public final V join() {
        int s = status;
        if (s == 0) {
            s = awaitUninterruptibly();
        }
        return reportJoin(s);
    }

    public final V invoke() {
        int s = doExec();
        if (s == 0) {
            s = awaitUninterruptibly();
        }
        return reportJoin(s);
    }

    public static void invokeAll(ForkJoinTask<?> t1, ForkJoinTask<?> t2) {
        t2.fork();
        t1.invoke();
        t2.join();
    }

    public static void invokeAll(ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        for (int i = tasks.length - 1; i > 0; i--) {
            tasks[i].fork();
        }
        if (tasks.length > 0) {
            tasks[0].quietlyInvoke();
        }
        for (ForkJoinTask<?> t : tasks) {
            t.quietlyJoin();
            if (ex == null && t.status > NORMAL) {
                ex = t.getException();
            }
        }
        if (ex != null) {
            sneakyThrow(ex);
        }
    }

    public static <T extends ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new ForkJoinTask<?>[0]));
            return tasks;
        }
        invokeAll(((List<? extends ForkJoinTask<?>>) tasks).toArray(new ForkJoinTask<?>[0]));
        return tasks;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return setCompletion(CANCELLED) == CANCELLED;
    }

    public final boolean isDone() {
        return status != 0;
    }

    public final boolean isCancelled() {
        return status == CANCELLED;
    }

    public final boolean isCompletedAbnormally() {
        return status > NORMAL;
    }

    public final boolean isCompletedNormally() {
        return status == NORMAL;
    }

    public final Throwable getException() {
        int s = status;
        return s == CANCELLED ? new CancellationException() : s == EXCEPTIONAL ? exception : null;
    }

    public void completeExceptionally(Throwable ex) {
        recordExceptionalCompletion(
                (ex instanceof RuntimeException) || (ex instanceof Error) ? ex : new RuntimeException(ex));
    }

    public void complete(V value) {
        try {
            setRawResult(value);
        } catch (Throwable rex) {
            recordExceptionalCompletion(rex);
            return;
        }
        setCompletion(NORMAL);
    }

    public final void quietlyComplete() {
        setCompletion(NORMAL);
    }

    public final V get() throws InterruptedException, ExecutionException {
        int s = status;
        if (s == 0) {
            s = awaitDone(true, 0);
        }
        return reportGet(s);
    }

    public final V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
            TimeoutException {
        long nanos = unit.toNanos(timeout);
        int s = status;
        if (s == 0) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            s = awaitDone(true, System.nanoTime() + Math.max(nanos, 1));
            if (s == 0) {
                throw new TimeoutException();
            }
        }
        return reportGet(s);
    }

    public final void quietlyJoin() {
        if (status == 0) {
            awaitUninterruptibly();
        }
    }

    public final void quietlyInvoke() {
        if (doExec() == 0) {
            awaitUninterruptibly();
        }
    }

    public static void helpQuiesce() {
        Thread t = Thread.currentThread();
        if (t instanceof ForkJoinWorkerThread) {
            ForkJoinPool p = ((ForkJoinWorkerThread) t).pool;
            for (ForkJoinTask<?> task; (task = p.pollTask()) != null;) {
                task.doExec();
                p.afterTask(task);
            }
        } else {
            ForkJoinPool.commonPool().awaitQuiescence(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }
    }

    public void reinitialize() {
        synchronized (this) {
            status = 0;
            exception = null;
            queuedIn = null;
        }
    }

    public static ForkJoinPool getPool() {
        Thread t = Thread.currentThread();
        return t instanceof ForkJoinWorkerThread ? ((ForkJoinWorkerThread) t).pool : null;
    }

    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }

    public boolean tryUnfork() {
        ForkJoinPool p = queuedIn;
        return p != null && p.tryRemove(this);
    }

    public static int getQueuedTaskCount() {
        ForkJoinPool p = getPool();
        return p == null ? 0 : (int) p.getQueuedTaskCount();
    }

    public static int getSurplusQueuedTaskCount() {
        ForkJoinPool p = getPool();
        return p == null ? 0 : p.surplusQueuedTaskCount();
    }

    public abstract V getRawResult();

    protected abstract void setRawResult(V value);

    protected abstract boolean exec();

    protected static ForkJoinTask<?> peekNextLocalTask() {
        ForkJoinPool p = getPool();
        return p == null ? null : p.peekTask();
    }

    protected static ForkJoinTask<?> pollNextLocalTask() {
        ForkJoinPool p = getPool();
        return p == null ? null : p.pollTask();
    }

    protected static ForkJoinTask<?> pollTask() {
        return pollNextLocalTask();
    }

    public final short getForkJoinTaskTag() {
        return tag;
    }

    public final synchronized short setForkJoinTaskTag(short newValue) {
        short old = tag;
        tag = newValue;
        return old;
    }

    public final synchronized boolean compareAndSetForkJoinTaskTag(short expect, short update) {
        if (tag != expect) {
            return false;
        }
        tag = update;
        return true;
    }

    // ---- adapters

    static final class AdaptedRunnable<T> extends ForkJoinTask<T> implements RunnableFuture<T> {
        private static final long serialVersionUID = 5232453952276885070L;
        final Runnable runnable;
        T result;

        AdaptedRunnable(Runnable runnable, T result) {
            if (runnable == null) {
                throw new NullPointerException();
            }
            this.runnable = runnable;
            this.result = result;
        }

        public T getRawResult() {
            return result;
        }

        public void setRawResult(T v) {
            result = v;
        }

        public boolean exec() {
            runnable.run();
            return true;
        }

        public void run() {
            invoke();
        }
    }

    static final class AdaptedRunnableAction extends ForkJoinTask<Void> implements RunnableFuture<Void> {
        private static final long serialVersionUID = 5232453952276885070L;
        final Runnable runnable;

        AdaptedRunnableAction(Runnable runnable) {
            if (runnable == null) {
                throw new NullPointerException();
            }
            this.runnable = runnable;
        }

        public Void getRawResult() {
            return null;
        }

        public void setRawResult(Void v) {
        }

        public boolean exec() {
            runnable.run();
            return true;
        }

        public void run() {
            invoke();
        }
    }

    /** From ForkJoinPool.execute(Runnable): an exception reaches the worker's uncaught exception handler. */
    static final class RunnableExecuteAction extends ForkJoinTask<Void> {
        private static final long serialVersionUID = 5232453952276885070L;
        final Runnable runnable;

        RunnableExecuteAction(Runnable runnable) {
            if (runnable == null) {
                throw new NullPointerException();
            }
            this.runnable = runnable;
        }

        public Void getRawResult() {
            return null;
        }

        public void setRawResult(Void v) {
        }

        public boolean exec() {
            runnable.run();
            return true;
        }

        Throwable failure() {
            return status == EXCEPTIONAL ? getException() : null;
        }
    }

    static final class AdaptedCallable<T> extends ForkJoinTask<T> implements RunnableFuture<T> {
        private static final long serialVersionUID = 2838392045355241008L;
        final Callable<? extends T> callable;
        T result;

        AdaptedCallable(Callable<? extends T> callable) {
            if (callable == null) {
                throw new NullPointerException();
            }
            this.callable = callable;
        }

        public T getRawResult() {
            return result;
        }

        public void setRawResult(T v) {
            result = v;
        }

        public boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public void run() {
            invoke();
        }
    }

    public static ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnableAction(runnable);
    }

    public static <T> ForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedRunnable<T>(runnable, result);
    }

    public static <T> ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }
}
