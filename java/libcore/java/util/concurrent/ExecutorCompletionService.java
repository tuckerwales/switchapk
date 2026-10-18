package java.util.concurrent;

/** Runs tasks on an executor and queues each Future as its task completes. */
public class ExecutorCompletionService<V> implements CompletionService<V> {
    private final Executor executor;
    private final AbstractExecutorService aes;
    private final BlockingQueue<Future<V>> completionQueue;

    private static final class QueueingFuture<V> extends FutureTask<Void> {
        private final Future<V> task;
        private final BlockingQueue<Future<V>> completionQueue;

        QueueingFuture(RunnableFuture<V> task, BlockingQueue<Future<V>> completionQueue) {
            super(task, null);
            this.task = task;
            this.completionQueue = completionQueue;
        }

        protected void done() {
            completionQueue.add(task);
        }
    }

    public ExecutorCompletionService(Executor executor) {
        this(executor, new LinkedBlockingQueue<Future<V>>());
    }

    public ExecutorCompletionService(Executor executor, BlockingQueue<Future<V>> completionQueue) {
        if (executor == null || completionQueue == null) {
            throw new NullPointerException();
        }
        this.executor = executor;
        this.aes = executor instanceof AbstractExecutorService ? (AbstractExecutorService) executor : null;
        this.completionQueue = completionQueue;
    }

    private RunnableFuture<V> newTaskFor(Callable<V> task) {
        return aes == null ? new FutureTask<V>(task) : aes.newTaskFor(task);
    }

    private RunnableFuture<V> newTaskFor(Runnable task, V result) {
        return aes == null ? new FutureTask<V>(task, result) : aes.newTaskFor(task, result);
    }

    public Future<V> submit(Callable<V> task) {
        if (task == null) {
            throw new NullPointerException();
        }
        RunnableFuture<V> f = newTaskFor(task);
        executor.execute(new QueueingFuture<V>(f, completionQueue));
        return f;
    }

    public Future<V> submit(Runnable task, V result) {
        if (task == null) {
            throw new NullPointerException();
        }
        RunnableFuture<V> f = newTaskFor(task, result);
        executor.execute(new QueueingFuture<V>(f, completionQueue));
        return f;
    }

    public Future<V> take() throws InterruptedException {
        return completionQueue.take();
    }

    public Future<V> poll() {
        return completionQueue.poll();
    }

    public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }
}
