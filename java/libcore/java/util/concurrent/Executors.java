package java.util.concurrent;

import java.util.Collection;
import java.util.List;

public class Executors {
    private Executors() {
    }

    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                threadFactory);
    }

    public static ExecutorService newWorkStealingPool() {
        return newFixedThreadPool(3);
    }

    public static ExecutorService newWorkStealingPool(int parallelism) {
        return newFixedThreadPool(parallelism);
    }

    public static ExecutorService newSingleThreadExecutor() {
        return newFixedThreadPool(1);
    }

    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return newFixedThreadPool(1, threadFactory);
    }

    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                threadFactory);
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new ScheduledThreadPoolExecutor(1);
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory threadFactory) {
        return new ScheduledThreadPoolExecutor(1, threadFactory);
    }

    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }

    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) {
        return new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
    }

    public static ExecutorService unconfigurableExecutorService(ExecutorService executor) {
        return executor;
    }

    public static ScheduledExecutorService unconfigurableScheduledExecutorService(ScheduledExecutorService executor) {
        return executor;
    }

    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }

    public static <T> Callable<T> callable(final Runnable task, final T result) {
        if (task == null) {
            throw new NullPointerException();
        }
        return new Callable<T>() {
            public T call() {
                task.run();
                return result;
            }
        };
    }

    public static Callable<Object> callable(Runnable task) {
        return callable(task, null);
    }

    static class DefaultThreadFactory implements ThreadFactory {
        private static int poolNumber = 1;
        private int threadNumber = 1;
        private final String namePrefix;

        DefaultThreadFactory() {
            synchronized (DefaultThreadFactory.class) {
                namePrefix = "pool-" + poolNumber++ + "-thread-";
            }
        }

        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber++);
            t.setDaemon(false);
            return t;
        }
    }
}
