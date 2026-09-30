package java.util.concurrent;

public class ForkJoinPool extends ThreadPoolExecutor {
    private static ForkJoinPool common;

    public ForkJoinPool() {
        this(3);
    }

    public ForkJoinPool(int parallelism) {
        super(parallelism, parallelism, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    public static synchronized ForkJoinPool commonPool() {
        if (common == null) {
            common = new ForkJoinPool(3);
        }
        return common;
    }

    public static int getCommonPoolParallelism() {
        return 3;
    }

    public int getParallelism() {
        return getCorePoolSize();
    }
}
