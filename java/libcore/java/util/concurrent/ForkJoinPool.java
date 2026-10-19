package java.util.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * An executor for ForkJoinTasks. One shared deque: tasks forked by a worker go on the front (run next, as a
 * worker's own deque would), submissions from outside go on the back. Up to parallelism daemon workers start
 * on demand and idle out after the keep-alive; a worker blocked in join runs queued tasks instead of waiting
 * (see ForkJoinTask.awaitDone), and managedBlock lets one more worker start while a worker blocks. The common
 * pool uses availableProcessors - 1 workers (at least one), or the
 * java.util.concurrent.ForkJoinPool.common.parallelism property, and ignores shutdown.
 */
public class ForkJoinPool extends AbstractExecutorService {
    public interface ForkJoinWorkerThreadFactory {
        ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    public interface ManagedBlocker {
        boolean block() throws InterruptedException;

        boolean isReleasable();
    }

    public static final ForkJoinWorkerThreadFactory defaultForkJoinWorkerThreadFactory =
            new ForkJoinWorkerThreadFactory() {
                public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
                    return new ForkJoinWorkerThread(pool);
                }
            };

    private static int poolNumberSequence;
    private static final ForkJoinPool common = makeCommonPool();

    private final int parallelism;
    private final ForkJoinWorkerThreadFactory factory;
    private final Thread.UncaughtExceptionHandler ueh;
    private final boolean asyncMode;
    private final long keepAliveMillis;
    private final String workerNamePrefix;
    private final boolean isCommon;

    /** Guards everything below; also where idle workers wait. */
    private final ArrayDeque<ForkJoinTask<?>> queue = new ArrayDeque<ForkJoinTask<?>>();
    private final ArrayList<ForkJoinWorkerThread> workers = new ArrayList<ForkJoinWorkerThread>();
    private int idle;
    private int blocked;
    private int active;
    private int nextWorkerNumber;
    private long steals;
    private boolean shutdown;

    public ForkJoinPool() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()), defaultForkJoinWorkerThreadFactory, null, false);
    }

    public ForkJoinPool(int parallelism) {
        this(parallelism, defaultForkJoinWorkerThreadFactory, null, false);
    }

    public ForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory, Thread.UncaughtExceptionHandler handler,
                        boolean asyncMode) {
        this(parallelism, factory, handler, asyncMode, 0, 0x7fff, 1, null, 60000L, TimeUnit.MILLISECONDS);
    }

    public ForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory, Thread.UncaughtExceptionHandler handler,
                        boolean asyncMode, int corePoolSize, int maximumPoolSize, int minimumRunnable,
                        Predicate<? super ForkJoinPool> saturate, long keepAliveTime, TimeUnit unit) {
        if (parallelism <= 0 || parallelism > 0x7fff || maximumPoolSize < parallelism || keepAliveTime <= 0L) {
            throw new IllegalArgumentException();
        }
        if (factory == null) {
            throw new NullPointerException();
        }
        this.parallelism = parallelism;
        this.factory = factory;
        this.ueh = handler;
        this.asyncMode = asyncMode;
        this.keepAliveMillis = Math.max(1, unit.toMillis(keepAliveTime));
        this.workerNamePrefix = "ForkJoinPool-" + nextPoolNumber() + "-worker-";
        this.isCommon = false;
    }

    private ForkJoinPool(int parallelism, boolean common) {
        this.parallelism = parallelism;
        this.factory = defaultForkJoinWorkerThreadFactory;
        this.ueh = null;
        this.asyncMode = false;
        this.keepAliveMillis = 60000L;
        this.workerNamePrefix = "ForkJoinPool.commonPool-worker-";
        this.isCommon = common;
    }

    private static synchronized int nextPoolNumber() {
        return ++poolNumberSequence;
    }

    private static ForkJoinPool makeCommonPool() {
        int p = Runtime.getRuntime().availableProcessors() - 1;
        try {
            String prop = System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism");
            if (prop != null) {
                p = Integer.parseInt(prop);
            }
        } catch (RuntimeException ignored) {
        }
        return new ForkJoinPool(Math.max(1, Math.min(p, 0x7fff)), true);
    }

    public static ForkJoinPool commonPool() {
        return common;
    }

    // ---- workers

    String nextWorkerName() {
        synchronized (queue) {
            return workerNamePrefix + (++nextWorkerNumber);
        }
    }

    int registerWorker(ForkJoinWorkerThread w) {
        synchronized (queue) {
            workers.add(w);
            return workers.size() - 1;
        }
    }

    void deregisterWorker(ForkJoinWorkerThread w) {
        synchronized (queue) {
            workers.remove(w);
            queue.notifyAll(); // awaitTermination / awaitQuiescence
        }
        signalWork(); // a worker died with tasks still queued
    }

    /** Starts a worker when none is idle and the pool is below parallelism (plus workers in managedBlock). */
    private void signalWork() {
        synchronized (queue) {
            if (queue.isEmpty()) {
                return;
            }
            if (idle > 0) {
                queue.notify();
                return;
            }
            if (shutdown || workers.size() >= parallelism + blocked) {
                return;
            }
        }
        ForkJoinWorkerThread w = null;
        Throwable ex = null;
        try {
            w = factory.newThread(this);
            if (w != null) {
                w.start();
            }
        } catch (Throwable t) {
            ex = t;
        }
        if (w == null || ex != null) {
            if (w != null) {
                deregisterWorker(w);
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
        }
    }

    void runWorker(ForkJoinWorkerThread w) {
        for (;;) {
            ForkJoinTask<?> task;
            synchronized (queue) {
                long deadline = System.currentTimeMillis() + keepAliveMillis;
                while ((task = queue.pollFirst()) == null) {
                    if (shutdown) {
                        return;
                    }
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) {
                        return; // idled out; deregistered by the thread
                    }
                    idle++;
                    try {
                        queue.wait(left);
                    } catch (InterruptedException e) {
                        if (shutdown) {
                            return;
                        }
                    } finally {
                        idle--;
                    }
                }
                task.queuedIn = null;
                active++;
                steals++;
            }
            try {
                task.doExec();
            } finally {
                synchronized (queue) {
                    active--;
                    if (active == 0 && queue.isEmpty()) {
                        queue.notifyAll(); // awaitQuiescence
                    }
                }
            }
            afterTask(task);
        }
    }

    /** A Runnable given to execute() rethrows its exception out of the worker, as the JDK's does. */
    void afterTask(ForkJoinTask<?> task) {
        if (task instanceof ForkJoinTask.RunnableExecuteAction) {
            Throwable ex = ((ForkJoinTask.RunnableExecuteAction) task).failure();
            if (ex != null) {
                ForkJoinTask.sneakyRethrow(ex);
            }
        }
    }

    // ---- the queue

    void push(ForkJoinTask<?> task, boolean local) {
        if (task == null) {
            throw new NullPointerException();
        }
        synchronized (queue) {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            task.queuedIn = this;
            if (local && !asyncMode) {
                queue.addFirst(task);
            } else {
                queue.addLast(task);
            }
        }
        signalWork();
    }

    boolean tryRemove(ForkJoinTask<?> task) {
        synchronized (queue) {
            if (task.queuedIn == this && queue.removeFirstOccurrence(task)) {
                task.queuedIn = null;
                return true;
            }
            return false;
        }
    }

    ForkJoinTask<?> pollTask() {
        synchronized (queue) {
            ForkJoinTask<?> t = queue.pollFirst();
            if (t != null) {
                t.queuedIn = null;
            }
            return t;
        }
    }

    ForkJoinTask<?> peekTask() {
        synchronized (queue) {
            return queue.peekFirst();
        }
    }

    int surplusQueuedTaskCount() {
        synchronized (queue) {
            return Math.max(0, queue.size() - idle);
        }
    }

    // ---- submission

    public <T> T invoke(ForkJoinTask<T> task) {
        push(task, false);
        return task.join();
    }

    public void execute(ForkJoinTask<?> task) {
        push(task, false);
    }

    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        ForkJoinTask<?> job = task instanceof ForkJoinTask<?> ? (ForkJoinTask<?>) task
                : new ForkJoinTask.RunnableExecuteAction(task);
        push(job, false);
    }

    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        push(task, false);
        return task;
    }

    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        ForkJoinTask<T> job = new ForkJoinTask.AdaptedCallable<T>(task);
        push(job, false);
        return job;
    }

    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        ForkJoinTask<T> job = new ForkJoinTask.AdaptedRunnable<T>(task, result);
        push(job, false);
        return job;
    }

    public ForkJoinTask<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        ForkJoinTask<?> job = task instanceof ForkJoinTask<?> ? (ForkJoinTask<?>) task
                : new ForkJoinTask.AdaptedRunnableAction(task);
        push(job, false);
        return job;
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        try {
            for (Callable<T> t : tasks) {
                ForkJoinTask<T> f = new ForkJoinTask.AdaptedCallable<T>(t);
                futures.add(f);
                push(f, false);
            }
            for (Future<T> f : futures) {
                ((ForkJoinTask<?>) f).quietlyJoin();
            }
            return futures;
        } catch (Throwable t) {
            for (Future<T> f : futures) {
                f.cancel(false);
            }
            ForkJoinTask.sneakyRethrow(t);
            return null;
        }
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return super.invokeAll(tasks, timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return super.invokeAny(tasks);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return super.invokeAny(tasks, timeout, unit);
    }

    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ForkJoinTask.AdaptedRunnable<T>(runnable, value);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ForkJoinTask.AdaptedCallable<T>(callable);
    }

    // ---- state

    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return ueh;
    }

    public int getParallelism() {
        return parallelism;
    }

    public static int getCommonPoolParallelism() {
        return common.parallelism;
    }

    public int getPoolSize() {
        synchronized (queue) {
            return workers.size();
        }
    }

    public boolean getAsyncMode() {
        return asyncMode;
    }

    public int getRunningThreadCount() {
        synchronized (queue) {
            return Math.max(0, active - blocked);
        }
    }

    public int getActiveThreadCount() {
        synchronized (queue) {
            return active;
        }
    }

    public boolean isQuiescent() {
        synchronized (queue) {
            return active == 0 && queue.isEmpty();
        }
    }

    public long getStealCount() {
        synchronized (queue) {
            return steals;
        }
    }

    public long getQueuedTaskCount() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public int getQueuedSubmissionCount() {
        return 0;
    }

    public boolean hasQueuedSubmissions() {
        return false;
    }

    protected ForkJoinTask<?> pollSubmission() {
        return pollTask();
    }

    protected int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int n = 0;
        for (ForkJoinTask<?> t; (t = pollTask()) != null; n++) {
            c.add(t);
        }
        return n;
    }

    public String toString() {
        String level;
        synchronized (queue) {
            level = isTerminated() ? "Terminated" : shutdown ? "Shutting down" : "Running";
            return super.toString() + "[" + level + ", parallelism = " + parallelism + ", size = " + workers.size()
                    + ", active = " + active + ", running = " + Math.max(0, active - blocked) + ", steals = " + steals
                    + ", tasks = " + queue.size() + ", submissions = 0]";
        }
    }

    // ---- lifecycle

    public void shutdown() {
        if (isCommon) {
            return;
        }
        synchronized (queue) {
            shutdown = true;
            queue.notifyAll();
        }
    }

    public List<Runnable> shutdownNow() {
        if (isCommon) {
            return Collections.emptyList();
        }
        ArrayList<ForkJoinWorkerThread> ws;
        synchronized (queue) {
            shutdown = true;
            for (ForkJoinTask<?> t : queue) {
                t.cancel(false);
            }
            queue.clear();
            ws = new ArrayList<ForkJoinWorkerThread>(workers);
            queue.notifyAll();
        }
        for (ForkJoinWorkerThread w : ws) {
            w.interrupt();
        }
        return Collections.emptyList();
    }

    public boolean isTerminated() {
        synchronized (queue) {
            return shutdown && workers.isEmpty() && queue.isEmpty();
        }
    }

    public boolean isTerminating() {
        synchronized (queue) {
            return shutdown && !(workers.isEmpty() && queue.isEmpty());
        }
    }

    public boolean isShutdown() {
        synchronized (queue) {
            return shutdown;
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (isCommon) {
            awaitQuiescence(timeout, unit);
            return false;
        }
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (queue) {
            while (!(shutdown && workers.isEmpty() && queue.isEmpty())) {
                long left = (deadline - System.nanoTime()) / 1000000;
                if (left <= 0) {
                    return false;
                }
                queue.wait(left);
            }
            return true;
        }
    }

    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        Thread t = Thread.currentThread();
        boolean worker = t instanceof ForkJoinWorkerThread && ((ForkJoinWorkerThread) t).pool == this;
        for (;;) {
            if (worker) {
                ForkJoinTask<?> task = pollTask();
                if (task != null) {
                    task.doExec();
                    afterTask(task);
                    continue;
                }
            }
            synchronized (queue) {
                // a worker calling this counts itself as active
                if ((active == (worker ? 1 : 0)) && queue.isEmpty()) {
                    return true;
                }
                long left = (deadline - System.nanoTime()) / 1000000;
                if (left <= 0) {
                    return false;
                }
                try {
                    queue.wait(Math.min(left, 10));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    /** Blocks through blocker; a worker doing so lets the pool start one more worker meanwhile. */
    public static void managedBlock(ManagedBlocker blocker) throws InterruptedException {
        if (blocker == null) {
            throw new NullPointerException();
        }
        Thread t = Thread.currentThread();
        ForkJoinPool p = t instanceof ForkJoinWorkerThread ? ((ForkJoinWorkerThread) t).pool : null;
        if (p != null) {
            synchronized (p.queue) {
                p.blocked++;
            }
            p.signalWork();
        }
        try {
            while (!blocker.isReleasable()) {
                if (blocker.block()) {
                    break;
                }
            }
        } finally {
            if (p != null) {
                synchronized (p.queue) {
                    p.blocked--;
                }
            }
        }
    }
}
