package java.util.concurrent;

import java.util.List;

public class ScheduledThreadPoolExecutor extends ThreadPoolExecutor implements ScheduledExecutorService {
    private final DelayQueue<ScheduledTask<?>> delayed = new DelayQueue<ScheduledTask<?>>();
    private Thread scheduler;
    private volatile boolean stop;

    public ScheduledThreadPoolExecutor(int corePoolSize) {
        this(corePoolSize, Executors.defaultThreadFactory());
    }

    public ScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(Math.max(1, corePoolSize), Math.max(1, corePoolSize), 0, TimeUnit.NANOSECONDS,
                new LinkedBlockingQueue<Runnable>(), threadFactory);
    }

    public ScheduledThreadPoolExecutor(int corePoolSize, RejectedExecutionHandler handler) {
        this(corePoolSize);
    }

    public ScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        this(corePoolSize, threadFactory);
    }

    private synchronized void ensureScheduler() {
        if (scheduler == null) {
            scheduler = new Thread("ScheduledExecutor") {
                public void run() {
                    while (!stop) {
                        try {
                            final ScheduledTask<?> t = delayed.take();
                            if (!t.isCancelled()) {
                                ScheduledThreadPoolExecutor.super.execute(t);
                            }
                        } catch (InterruptedException e) {
                            if (stop) {
                                return;
                            }
                        }
                    }
                }
            };
            scheduler.setDaemon(true);
            scheduler.start();
        }
    }

    final class ScheduledTask<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {
        long time;
        final long period; // >0 fixed rate, <0 fixed delay

        ScheduledTask(Callable<V> c, long time, long period) {
            super(c);
            this.time = time;
            this.period = period;
        }

        public long getDelay(TimeUnit unit) {
            return unit.convert(time - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        public int compareTo(Delayed other) {
            long d = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
            return d < 0 ? -1 : (d > 0 ? 1 : 0);
        }

        public boolean isPeriodic() {
            return period != 0;
        }

        public void run() {
            if (!isPeriodic()) {
                super.run();
            } else if (runAndReset() && !isShutdown()) {
                time = period > 0 ? time + period : System.nanoTime() - period;
                delayed.put(this);
            }
        }
    }

    private <V> ScheduledTask<V> schedule0(Callable<V> c, long delay, TimeUnit unit, long period) {
        if (isShutdown()) {
            throw new RejectedExecutionException("shut down");
        }
        ScheduledTask<V> t = new ScheduledTask<V>(c, System.nanoTime() + unit.toNanos(Math.max(0, delay)), period);
        ensureScheduler();
        delayed.put(t);
        return t;
    }

    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedule0(Executors.callable(command), delay, unit, 0);
    }

    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return schedule0(callable, delay, unit, 0);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (period <= 0) {
            throw new IllegalArgumentException();
        }
        return schedule0(Executors.callable(command), initialDelay, unit, unit.toNanos(period));
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (delay <= 0) {
            throw new IllegalArgumentException();
        }
        return schedule0(Executors.callable(command), initialDelay, unit, -unit.toNanos(delay));
    }

    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.NANOSECONDS);
    }

    public Future<?> submit(Runnable task) {
        return schedule(task, 0, TimeUnit.NANOSECONDS);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, TimeUnit.NANOSECONDS);
    }

    public void shutdown() {
        stop = true;
        if (scheduler != null) {
            scheduler.interrupt();
        }
        super.shutdown();
    }

    public List<Runnable> shutdownNow() {
        stop = true;
        if (scheduler != null) {
            scheduler.interrupt();
        }
        delayed.clear();
        return super.shutdownNow();
    }

    public void setRemoveOnCancelPolicy(boolean value) {
    }

    public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
    }

    public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
    }
}
