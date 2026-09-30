package java.util.concurrent;

import java.util.ArrayList;
import java.util.List;

public class ThreadPoolExecutor extends AbstractExecutorService {
    private final BlockingQueue<Runnable> workQueue;
    private final ThreadFactory threadFactory;
    private int corePoolSize;
    private int maximumPoolSize;
    private long keepAliveNanos;
    private final ArrayList<Thread> workers = new ArrayList<Thread>();
    private int idle;
    private boolean shutdown;
    private RejectedExecutionHandler handler;
    private int completed;

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(), new AbortPolicy());
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new AbortPolicy());
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(), handler);
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = Math.max(1, Math.max(corePoolSize, maximumPoolSize));
        this.workQueue = workQueue;
        this.keepAliveNanos = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        synchronized (this) {
            if (shutdown) {
                handler.rejectedExecution(command, this);
                return;
            }
            if (workers.size() < corePoolSize || (idle == 0 && workers.size() < maximumPoolSize)) {
                addWorker(command);
                return;
            }
        }
        if (!workQueue.offer(command)) {
            synchronized (this) {
                if (workers.size() < maximumPoolSize) {
                    addWorker(command);
                    return;
                }
            }
            handler.rejectedExecution(command, this);
        }
    }

    private void addWorker(final Runnable first) {
        final Thread[] holder = new Thread[1];
        Thread t = threadFactory.newThread(new Runnable() {
            public void run() {
                workerLoop(first, holder[0]);
            }
        });
        holder[0] = t;
        workers.add(t);
        t.start();
    }

    protected void beforeExecute(Thread t, Runnable r) {
    }

    protected void afterExecute(Runnable r, Throwable t) {
    }

    protected void terminated() {
    }

    private void workerLoop(Runnable task, Thread self) {
        try {
            while (true) {
                if (task == null) {
                    synchronized (this) {
                        idle++;
                    }
                    try {
                        boolean core;
                        synchronized (this) {
                            core = workers.size() <= corePoolSize;
                        }
                        if (core && keepAliveNanos >= 0) {
                            task = workQueue.poll(1000, TimeUnit.MILLISECONDS);
                        } else {
                            task = workQueue.poll(Math.max(1, keepAliveNanos), TimeUnit.NANOSECONDS);
                        }
                    } catch (InterruptedException e) {
                        task = null;
                    } finally {
                        synchronized (this) {
                            idle--;
                        }
                    }
                    if (task == null) {
                        synchronized (this) {
                            if (shutdown && workQueue.isEmpty()) {
                                return;
                            }
                            if (workers.size() > corePoolSize) {
                                return;
                            }
                        }
                        continue;
                    }
                }
                Throwable thrown = null;
                beforeExecute(self, task);
                try {
                    task.run();
                } catch (Throwable ex) {
                    thrown = ex;
                } finally {
                    afterExecute(task, thrown);
                }
                synchronized (this) {
                    completed++;
                }
                task = null;
            }
        } finally {
            synchronized (this) {
                workers.remove(self);
                notifyAll();
                if (shutdown && workers.isEmpty()) {
                    terminated();
                }
            }
        }
    }

    public synchronized void shutdown() {
        shutdown = true;
        notifyAll();
    }

    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        ArrayList<Runnable> pending = new ArrayList<Runnable>();
        workQueue.drainTo(pending);
        for (Thread t : workers) {
            t.interrupt();
        }
        return pending;
    }

    public synchronized boolean isShutdown() {
        return shutdown;
    }

    public synchronized boolean isTerminated() {
        return shutdown && workers.isEmpty();
    }

    public synchronized boolean isTerminating() {
        return shutdown && !workers.isEmpty();
    }

    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!(shutdown && workers.isEmpty())) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return true;
    }

    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    public synchronized int getPoolSize() {
        return workers.size();
    }

    public synchronized int getActiveCount() {
        return workers.size() - idle;
    }

    public synchronized long getCompletedTaskCount() {
        return completed;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public void setKeepAliveTime(long time, TimeUnit unit) {
        keepAliveNanos = unit.toNanos(time);
    }

    public void allowCoreThreadTimeOut(boolean value) {
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        this.handler = handler;
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    public boolean remove(Runnable task) {
        return workQueue.remove(task);
    }

    public static class AbortPolicy implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r + " rejected from " + e);
        }
    }

    public static class DiscardPolicy implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
