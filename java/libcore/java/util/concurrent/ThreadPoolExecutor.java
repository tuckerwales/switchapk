package java.util.concurrent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Thread pool with OpenJDK's policy: below corePoolSize every task starts a thread; then tasks are
 * queued; when the queue refuses one, a thread up to maximumPoolSize starts, else the task is
 * rejected. Threads above the core size (or all, with allowCoreThreadTimeOut) exit after
 * keepAliveTime idle. A task that throws ends its worker thread, whose uncaught-exception handler
 * sees the exception, and a replacement thread starts, as in OpenJDK. Monitor-based.
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int STOP = 2;
    private static final int TIDYING = 3;
    private static final int TERMINATED = 4;

    private final BlockingQueue<Runnable> workQueue;
    private volatile ThreadFactory threadFactory;
    private volatile RejectedExecutionHandler handler;
    private volatile int corePoolSize;
    private volatile int maximumPoolSize;
    private volatile long keepAliveTime; // nanos
    private volatile boolean allowCoreThreadTimeOut;

    private final Object mainLock = new Object();
    private final HashSet<Worker> workers = new HashSet<Worker>(); // guarded by mainLock
    private int runState = RUNNING; // guarded by mainLock
    private int largestPoolSize; // guarded by mainLock
    private long completedTaskCount; // guarded by mainLock, finished workers only

    private final class Worker implements Runnable {
        final Thread thread;
        Runnable firstTask;
        volatile long completedTasks;
        boolean busy; // guarded by mainLock

        Worker(Runnable firstTask) {
            this.firstTask = firstTask;
            this.busy = firstTask != null;
            this.thread = threadFactory.newThread(this);
        }

        public void run() {
            runWorker(this);
        }
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(),
                new AbortPolicy());
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new AbortPolicy());
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(),
                handler);
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException();
        }
        if (workQueue == null || threadFactory == null || handler == null) throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    // ---- submission ----

    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException();
        if (workerCount() < corePoolSize && addWorker(command, true)) return;
        if (isRunning() && workQueue.offer(command)) {
            if (!isRunning() && workQueue.remove(command)) {
                reject(command);
            } else if (workerCount() == 0) {
                addWorker(null, false);
            }
        } else if (!addWorker(command, false)) {
            reject(command);
        }
    }

    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    private int workerCount() {
        synchronized (mainLock) {
            return workers.size();
        }
    }

    private boolean isRunning() {
        synchronized (mainLock) {
            return runState == RUNNING;
        }
    }

    private boolean addWorker(Runnable firstTask, boolean core) {
        Worker w;
        synchronized (mainLock) {
            if (runState >= STOP || (runState == SHUTDOWN && (firstTask != null || workQueue.isEmpty()))) {
                return false;
            }
            if (workers.size() >= (core ? corePoolSize : maximumPoolSize)) return false;
            w = new Worker(firstTask);
            if (w.thread == null) return false;
            workers.add(w);
            if (workers.size() > largestPoolSize) largestPoolSize = workers.size();
        }
        try {
            w.thread.start();
        } catch (Throwable t) {
            synchronized (mainLock) {
                workers.remove(w);
            }
            tryTerminate();
            return false;
        }
        return true;
    }

    // ---- workers ----

    /** The next task, or null when this worker should exit. */
    private Runnable getTask(Worker w) {
        boolean timedOut = false;
        for (;;) {
            boolean timed;
            synchronized (mainLock) {
                if (runState >= STOP || (runState == SHUTDOWN && workQueue.isEmpty())) return null;
                int wc = workers.size();
                timed = allowCoreThreadTimeOut || wc > corePoolSize;
                if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                    return null;
                }
                w.busy = false;
            }
            try {
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                if (r != null) return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask(w)) != null) {
                // OpenJDK: a task runs uninterrupted unless the pool is stopping
                boolean stopping;
                synchronized (mainLock) {
                    w.busy = true;
                    stopping = runState >= STOP;
                }
                if (!stopping) {
                    Thread.interrupted();
                    synchronized (mainLock) {
                        stopping = runState >= STOP;
                    }
                }
                if (stopping && !wt.isInterrupted()) wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    try {
                        task.run();
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        int min;
        boolean replace = false;
        synchronized (mainLock) {
            completedTaskCount += w.completedTasks;
            workers.remove(w);
            mainLock.notifyAll();
            if (runState < STOP) {
                if (completedAbruptly) {
                    replace = true;
                } else {
                    min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                    if (min == 0 && !workQueue.isEmpty()) min = 1;
                    replace = workers.size() < min;
                }
            }
        }
        tryTerminate();
        if (replace) addWorker(null, false);
    }

    private void interruptIdleWorkers() {
        synchronized (mainLock) {
            for (Worker w : workers) {
                if (!w.busy) w.thread.interrupt();
            }
        }
    }

    final void tryTerminate() {
        boolean run = false;
        synchronized (mainLock) {
            if (runState == RUNNING || runState >= TIDYING) return;
            if (runState == SHUTDOWN && !workQueue.isEmpty()) return;
            if (!workers.isEmpty()) {
                for (Worker w : workers) {
                    if (!w.busy) {
                        w.thread.interrupt();
                        break;
                    }
                }
                return;
            }
            runState = TIDYING;
            run = true;
        }
        if (run) {
            try {
                terminated();
            } finally {
                synchronized (mainLock) {
                    runState = TERMINATED;
                    mainLock.notifyAll();
                }
            }
        }
    }

    protected void beforeExecute(Thread t, Runnable r) {
    }

    protected void afterExecute(Runnable r, Throwable t) {
    }

    protected void terminated() {
    }

    // ---- lifecycle ----

    public void shutdown() {
        synchronized (mainLock) {
            if (runState < SHUTDOWN) runState = SHUTDOWN;
        }
        interruptIdleWorkers();
        onShutdown();
        tryTerminate();
    }

    /** Hook for ScheduledThreadPoolExecutor (package-private in OpenJDK). */
    void onShutdown() {
    }

    public List<Runnable> shutdownNow() {
        ArrayList<Runnable> tasks = new ArrayList<Runnable>();
        synchronized (mainLock) {
            if (runState < STOP) runState = STOP;
            for (Worker w : workers) w.thread.interrupt();
        }
        workQueue.drainTo(tasks);
        if (!workQueue.isEmpty()) {
            for (Runnable r : workQueue.toArray(new Runnable[0])) {
                if (workQueue.remove(r)) tasks.add(r);
            }
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        synchronized (mainLock) {
            return runState != RUNNING;
        }
    }

    public boolean isTerminating() {
        synchronized (mainLock) {
            return runState != RUNNING && runState < TERMINATED;
        }
    }

    public boolean isTerminated() {
        synchronized (mainLock) {
            return runState == TERMINATED;
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        synchronized (mainLock) {
            long deadline = System.nanoTime() + nanos;
            while (runState != TERMINATED) {
                long left = deadline - System.nanoTime();
                if (left <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(mainLock, left);
            }
            return true;
        }
    }

    @Override
    protected void finalize() {
    }

    // ---- configuration ----

    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null) throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null) throw new NullPointerException();
        this.handler = handler;
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0 || maximumPoolSize < corePoolSize) throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCount() > corePoolSize) {
            interruptIdleWorkers();
        } else if (delta > 0) {
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) break;
            }
        }
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public boolean prestartCoreThread() {
        return workerCount() < corePoolSize && addWorker(null, true);
    }

    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true)) ++n;
        return n;
    }

    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value) interruptIdleWorkers();
        }
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize) throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCount() > maximumPoolSize) interruptIdleWorkers();
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0) throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut()) {
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        }
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0) interruptIdleWorkers();
    }

    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate();
        return removed;
    }

    public void purge() {
        for (Runnable r : workQueue.toArray(new Runnable[0])) {
            if (r instanceof Future<?> && ((Future<?>) r).isCancelled()) workQueue.remove(r);
        }
        tryTerminate();
    }

    // ---- statistics ----

    public int getPoolSize() {
        synchronized (mainLock) {
            return runState >= TIDYING ? 0 : workers.size();
        }
    }

    public int getActiveCount() {
        synchronized (mainLock) {
            int n = 0;
            for (Worker w : workers) {
                if (w.busy) ++n;
            }
            return n;
        }
    }

    public int getLargestPoolSize() {
        synchronized (mainLock) {
            return largestPoolSize;
        }
    }

    public long getTaskCount() {
        synchronized (mainLock) {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.busy) ++n;
            }
            return n + workQueue.size();
        }
    }

    public long getCompletedTaskCount() {
        synchronized (mainLock) {
            long n = completedTaskCount;
            for (Worker w : workers) n += w.completedTasks;
            return n;
        }
    }

    public String toString() {
        long ncompleted;
        int nworkers;
        int nactive;
        String state;
        synchronized (mainLock) {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.busy) ++nactive;
            }
            state = runState == RUNNING ? "Running" : runState == TERMINATED ? "Terminated" : "Shutting down";
        }
        return super.toString() + "[" + state + ", pool size = " + nworkers + ", active threads = " + nactive
                + ", queued tasks = " + workQueue.size() + ", completed tasks = " + ncompleted + "]";
    }

    // ---- rejection policies ----

    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        public CallerRunsPolicy() {
        }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) r.run();
        }
    }

    public static class AbortPolicy implements RejectedExecutionHandler {
        public AbortPolicy() {
        }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        }
    }

    public static class DiscardPolicy implements RejectedExecutionHandler {
        public DiscardPolicy() {
        }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        public DiscardOldestPolicy() {
        }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
