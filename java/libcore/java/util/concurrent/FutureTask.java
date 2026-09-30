package java.util.concurrent;

public class FutureTask<V> implements RunnableFuture<V> {
    private static final int NEW = 0, RUNNING = 1, DONE = 2, CANCELLED = 3;
    private Callable<V> callable;
    private int state;
    private V result;
    private Throwable exception;
    private Thread runner;

    public FutureTask(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        this.callable = callable;
    }

    public FutureTask(final Runnable runnable, final V result) {
        this(Executors.callable(runnable, result));
    }

    public synchronized boolean isCancelled() {
        return state == CANCELLED;
    }

    public synchronized boolean isDone() {
        return state >= DONE;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (state >= DONE) {
                return false;
            }
            state = CANCELLED;
            if (mayInterruptIfRunning && runner != null) {
                runner.interrupt();
            }
            notifyAll();
        }
        done();
        return true;
    }

    private V report() throws ExecutionException {
        if (state == CANCELLED) {
            throw new CancellationException();
        }
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        return result;
    }

    public synchronized V get() throws InterruptedException, ExecutionException {
        while (state < DONE) {
            wait();
        }
        return report();
    }

    public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (state < DONE) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                throw new TimeoutException();
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return report();
    }

    protected void done() {
    }

    protected void set(V v) {
        synchronized (this) {
            if (state >= DONE) {
                return;
            }
            result = v;
            state = DONE;
            notifyAll();
        }
        done();
    }

    protected void setException(Throwable t) {
        synchronized (this) {
            if (state >= DONE) {
                return;
            }
            exception = t;
            state = DONE;
            notifyAll();
        }
        done();
    }

    public void run() {
        synchronized (this) {
            if (state != NEW) {
                return;
            }
            state = RUNNING;
            runner = Thread.currentThread();
        }
        try {
            V v = callable.call();
            set(v);
        } catch (Throwable ex) {
            setException(ex);
        } finally {
            runner = null;
        }
    }

    protected boolean runAndReset() {
        synchronized (this) {
            if (state != NEW) {
                return false;
            }
            runner = Thread.currentThread();
        }
        try {
            callable.call();
            return true;
        } catch (Throwable ex) {
            setException(ex);
            return false;
        } finally {
            runner = null;
        }
    }
}
