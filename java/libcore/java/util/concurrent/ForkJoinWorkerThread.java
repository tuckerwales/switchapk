package java.util.concurrent;

/** A daemon thread run by a ForkJoinPool: takes queued tasks until the pool shuts down or it idles out. */
public class ForkJoinWorkerThread extends Thread {
    final ForkJoinPool pool;
    final int index;

    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        super(pool.nextWorkerName());
        this.pool = pool;
        this.index = pool.registerWorker(this);
        setDaemon(true);
        Thread.UncaughtExceptionHandler h = pool.getUncaughtExceptionHandler();
        if (h != null) {
            setUncaughtExceptionHandler(h);
        }
    }

    public ForkJoinPool getPool() {
        return pool;
    }

    public int getPoolIndex() {
        return index;
    }

    protected void onStart() {
    }

    protected void onTermination(Throwable exception) {
    }

    public void run() {
        Throwable exception = null;
        try {
            onStart();
            pool.runWorker(this);
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            try {
                onTermination(exception);
            } catch (Throwable ex) {
                if (exception == null) {
                    exception = ex;
                }
            } finally {
                pool.deregisterWorker(this);
            }
        }
        if (exception != null) {
            ForkJoinTask.sneakyRethrow(exception);
        }
    }
}
