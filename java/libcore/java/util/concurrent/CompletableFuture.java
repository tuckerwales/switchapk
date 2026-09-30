package java.util.concurrent;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {
    private boolean done;
    private T result;
    private Throwable exception;
    private final ArrayList<Runnable> callbacks = new ArrayList<Runnable>();

    public CompletableFuture() {
    }

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return supplyAsync(supplier, ForkJoinPool.commonPool());
    }

    public static <U> CompletableFuture<U> supplyAsync(final Supplier<U> supplier, Executor executor) {
        final CompletableFuture<U> f = new CompletableFuture<U>();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    f.complete(supplier.get());
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            }
        });
        return f;
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return runAsync(runnable, ForkJoinPool.commonPool());
    }

    public static CompletableFuture<Void> runAsync(final Runnable runnable, Executor executor) {
        return supplyAsync(new Supplier<Void>() {
            public Void get() {
                runnable.run();
                return null;
            }
        }, executor);
    }

    public static <U> CompletableFuture<U> completedFuture(U value) {
        CompletableFuture<U> f = new CompletableFuture<U>();
        f.complete(value);
        return f;
    }

    public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        CompletableFuture<U> f = new CompletableFuture<U>();
        f.completeExceptionally(ex);
        return f;
    }

    private void onDone(Runnable r) {
        boolean runNow;
        synchronized (this) {
            runNow = done;
            if (!runNow) {
                callbacks.add(r);
            }
        }
        if (runNow) {
            r.run();
        }
    }

    private boolean finish(T value, Throwable ex) {
        ArrayList<Runnable> cbs;
        synchronized (this) {
            if (done) {
                return false;
            }
            done = true;
            result = value;
            exception = ex;
            notifyAll();
            cbs = new ArrayList<Runnable>(callbacks);
            callbacks.clear();
        }
        for (Runnable r : cbs) {
            r.run();
        }
        return true;
    }

    public boolean complete(T value) {
        return finish(value, null);
    }

    public boolean completeExceptionally(Throwable ex) {
        return finish(null, ex);
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isCompletedExceptionally() {
        return done && exception != null;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return finish(null, new CancellationException());
    }

    public synchronized boolean isCancelled() {
        return done && exception instanceof CancellationException;
    }

    private T report() throws ExecutionException {
        if (exception instanceof CancellationException) {
            throw (CancellationException) exception;
        }
        if (exception != null) {
            throw new ExecutionException(exception instanceof CompletionException && exception.getCause() != null
                    ? exception.getCause() : exception);
        }
        return result;
    }

    public synchronized T get() throws InterruptedException, ExecutionException {
        while (!done) {
            wait();
        }
        return report();
    }

    public synchronized T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!done) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                throw new TimeoutException();
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return report();
    }

    public synchronized T join() {
        while (!done) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        if (exception != null) {
            throw exception instanceof CompletionException ? (CompletionException) exception : new CompletionException(exception);
        }
        return result;
    }

    public synchronized T getNow(T valueIfAbsent) {
        if (!done) {
            return valueIfAbsent;
        }
        return join();
    }

    public <U> CompletableFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
        final CompletableFuture<U> dst = new CompletableFuture<U>();
        onDone(new Runnable() {
            public void run() {
                if (exception != null) {
                    dst.completeExceptionally(exception);
                    return;
                }
                try {
                    dst.complete(fn.apply(result));
                } catch (Throwable t) {
                    dst.completeExceptionally(t);
                }
            }
        });
        return dst;
    }

    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApply(fn);
    }

    public CompletableFuture<Void> thenAccept(final Consumer<? super T> action) {
        return thenApply(new Function<T, Void>() {
            public Void apply(T t) {
                action.accept(t);
                return null;
            }
        });
    }

    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAccept(action);
    }

    public CompletableFuture<Void> thenRun(final Runnable action) {
        return thenApply(new Function<T, Void>() {
            public Void apply(T t) {
                action.run();
                return null;
            }
        });
    }

    public <U> CompletableFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
        final CompletableFuture<U> dst = new CompletableFuture<U>();
        onDone(new Runnable() {
            public void run() {
                if (exception != null) {
                    dst.completeExceptionally(exception);
                    return;
                }
                try {
                    fn.apply(result).whenComplete(new BiConsumer<U, Throwable>() {
                        public void accept(U u, Throwable t) {
                            if (t != null) {
                                dst.completeExceptionally(t);
                            } else {
                                dst.complete(u);
                            }
                        }
                    });
                } catch (Throwable t) {
                    dst.completeExceptionally(t);
                }
            }
        });
        return dst;
    }

    public <U, V> CompletableFuture<V> thenCombine(final CompletionStage<? extends U> other,
            final BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCompose(new Function<T, CompletionStage<V>>() {
            public CompletionStage<V> apply(final T t) {
                return other.thenApply(new Function<U, V>() {
                    public V apply(U u) {
                        return fn.apply(t, u);
                    }
                });
            }
        });
    }

    public CompletableFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        final CompletableFuture<T> dst = new CompletableFuture<T>();
        onDone(new Runnable() {
            public void run() {
                if (exception == null) {
                    dst.complete(result);
                    return;
                }
                try {
                    dst.complete(fn.apply(exception));
                } catch (Throwable t) {
                    dst.completeExceptionally(t);
                }
            }
        });
        return dst;
    }

    public <U> CompletableFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        final CompletableFuture<U> dst = new CompletableFuture<U>();
        onDone(new Runnable() {
            public void run() {
                try {
                    dst.complete(fn.apply(result, exception));
                } catch (Throwable t) {
                    dst.completeExceptionally(t);
                }
            }
        });
        return dst;
    }

    public CompletableFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        final CompletableFuture<T> dst = new CompletableFuture<T>();
        onDone(new Runnable() {
            public void run() {
                try {
                    action.accept(result, exception);
                } catch (Throwable t) {
                    if (exception == null) {
                        dst.completeExceptionally(t);
                        return;
                    }
                }
                dst.finish(result, exception);
            }
        });
        return dst;
    }

    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    public static CompletableFuture<Void> allOf(final CompletableFuture<?>... cfs) {
        final CompletableFuture<Void> dst = new CompletableFuture<Void>();
        if (cfs.length == 0) {
            dst.complete(null);
            return dst;
        }
        final int[] remaining = {cfs.length};
        for (CompletableFuture<?> f : cfs) {
            f.onDone(new Runnable() {
                public void run() {
                    boolean last;
                    synchronized (remaining) {
                        last = --remaining[0] == 0;
                    }
                    if (last) {
                        for (CompletableFuture<?> g : cfs) {
                            if (g.exception != null) {
                                dst.completeExceptionally(g.exception);
                                return;
                            }
                        }
                        dst.complete(null);
                    }
                }
            });
        }
        return dst;
    }

    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        final CompletableFuture<Object> dst = new CompletableFuture<Object>();
        for (final CompletableFuture<?> f : cfs) {
            f.onDone(new Runnable() {
                public void run() {
                    dst.finish(f.result, f.exception);
                }
            });
        }
        return dst;
    }

    public String toString() {
        return super.toString() + (done ? (exception != null ? "[Completed exceptionally]" : "[Completed normally]") : "[Incomplete]");
    }
}
