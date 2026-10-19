package java.util.concurrent;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A future completed by value, exception or cancellation, with dependent stages. Results are
 * encoded as OpenJDK does: a raw exception from completeExceptionally stays as given, an
 * exception thrown by a stage function or relayed from a source is wrapped in
 * CompletionException. Async stages run their function on the given executor (the common pool
 * when it has more than one thread, else a new thread per task); relaying a source's exception
 * is synchronous.
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {
    /** A null value or an exception. */
    static final class AltResult {
        final Throwable ex;

        AltResult(Throwable x) {
            ex = x;
        }
    }

    static final AltResult NIL = new AltResult(null);

    volatile Object result;
    private ArrayList<Runnable> dependents; // guarded by this

    public CompletableFuture() {
    }

    CompletableFuture(Object r) {
        result = r;
    }

    // ---- encoding ----

    static Object encodeValue(Object v) {
        return v == null ? NIL : v;
    }

    static AltResult encodeThrowable(Throwable x) {
        return new AltResult(x instanceof CompletionException ? x : new CompletionException(x));
    }

    static Object encodeThrowable(Throwable x, Object r) {
        if (!(x instanceof CompletionException)) {
            x = new CompletionException(x);
        } else if (r instanceof AltResult && x == ((AltResult) r).ex) {
            return r;
        }
        return new AltResult(x);
    }

    static Object encodeRelay(Object r) {
        Throwable x;
        if (r instanceof AltResult && (x = ((AltResult) r).ex) != null && !(x instanceof CompletionException)) {
            r = new AltResult(new CompletionException(x));
        }
        return r;
    }

    static Throwable exOf(Object r) {
        return r instanceof AltResult ? ((AltResult) r).ex : null;
    }

    @SuppressWarnings("unchecked")
    static <V> V valueOf(Object r) {
        return r instanceof AltResult ? null : (V) r;
    }

    // ---- completion ----

    boolean internalComplete(Object r) {
        ArrayList<Runnable> run;
        synchronized (this) {
            if (result != null) return false;
            result = r;
            notifyAll();
            run = dependents;
            dependents = null;
        }
        if (run != null) {
            // a stack, as OpenJDK: the latest dependent runs first
            for (int i = run.size() - 1; i >= 0; i--) run.get(i).run();
        }
        return true;
    }

    final boolean completeValue(Object v) {
        return internalComplete(encodeValue(v));
    }

    final boolean completeThrowable(Throwable x) {
        return internalComplete(encodeThrowable(x));
    }

    final boolean completeThrowable(Throwable x, Object r) {
        return internalComplete(encodeThrowable(x, r));
    }

    final boolean completeRelay(Object r) {
        return internalComplete(encodeRelay(r));
    }

    /** Runs {@code d} once this future is complete (now, if it already is). */
    final void onDone(Runnable d) {
        synchronized (this) {
            if (result == null) {
                if (dependents == null) dependents = new ArrayList<Runnable>();
                dependents.add(d);
                return;
            }
        }
        d.run();
    }

    /** Runs {@code body} inline, or on {@code e}; a rejected task fails {@code dst}. */
    static void fire(Executor e, Runnable body, CompletableFuture<?> dst) {
        if (e == null) {
            body.run();
            return;
        }
        try {
            e.execute(body);
        } catch (Throwable ex) {
            dst.completeThrowable(ex);
        }
    }

    // ---- default executor ----

    private static final boolean USE_COMMON_POOL = ForkJoinPool.getCommonPoolParallelism() > 1;

    static final class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            if (r == null) throw new NullPointerException();
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.start();
        }
    }

    private static final Executor ASYNC_POOL = USE_COMMON_POOL
            ? ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();

    static Executor screenExecutor(Executor e) {
        if (e == null) throw new NullPointerException();
        if (!USE_COMMON_POOL && e == ForkJoinPool.commonPool()) return ASYNC_POOL;
        return e;
    }

    public Executor defaultExecutor() {
        return ASYNC_POOL;
    }

    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CompletableFuture<U>();
    }

    // ---- factories ----

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return asyncSupplyStage(ASYNC_POOL, supplier);
    }

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        return asyncSupplyStage(screenExecutor(executor), supplier);
    }

    static <U> CompletableFuture<U> asyncSupplyStage(Executor e, final Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<U> d = new CompletableFuture<U>();
        e.execute(supplyTask(d, f));
        return d;
    }

    static <U> Runnable supplyTask(final CompletableFuture<U> d, final Supplier<? extends U> f) {
        return new Runnable() {
            public void run() {
                if (d.result == null) {
                    try {
                        d.completeValue(f.get());
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                }
            }
        };
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return asyncRunStage(ASYNC_POOL, runnable);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        return asyncRunStage(screenExecutor(executor), runnable);
    }

    static CompletableFuture<Void> asyncRunStage(Executor e, final Runnable f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<Void> d = new CompletableFuture<Void>();
        e.execute(new Runnable() {
            public void run() {
                if (d.result == null) {
                    try {
                        f.run();
                        d.completeValue(null);
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                }
            }
        });
        return d;
    }

    public static <U> CompletableFuture<U> completedFuture(U value) {
        return new CompletableFuture<U>(encodeValue(value));
    }

    public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        return new CompletableFuture<U>(new AltResult(ex));
    }

    public static <U> CompletionStage<U> completedStage(U value) {
        return new MinimalStage<U>(encodeValue(value));
    }

    public static <U> CompletionStage<U> failedStage(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        return new MinimalStage<U>(new AltResult(ex));
    }

    // ---- Future ----

    public boolean isDone() {
        return result != null;
    }

    private Object waitingGet(boolean interruptible) throws InterruptedException {
        synchronized (this) {
            boolean interrupted = false;
            while (result == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    if (interruptible) throw e;
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return result;
        }
    }

    private static Object reportGet(Object r) throws InterruptedException, ExecutionException {
        if (r == null) throw new InterruptedException();
        if (r instanceof AltResult) {
            Throwable x;
            Throwable cause;
            if ((x = ((AltResult) r).ex) == null) return null;
            if (x instanceof CancellationException) throw (CancellationException) x;
            if (x instanceof CompletionException && (cause = x.getCause()) != null) x = cause;
            throw new ExecutionException(x);
        }
        return r;
    }

    private static Object reportJoin(Object r) {
        if (r instanceof AltResult) {
            Throwable x;
            if ((x = ((AltResult) r).ex) == null) return null;
            if (x instanceof CancellationException) throw (CancellationException) x;
            if (x instanceof CompletionException) throw (CompletionException) x;
            throw new CompletionException(x);
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    public T get() throws InterruptedException, ExecutionException {
        Object r = result;
        return (T) reportGet(r == null ? waitingGet(true) : r);
    }

    @SuppressWarnings("unchecked")
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        Object r;
        synchronized (this) {
            long deadline = System.nanoTime() + nanos;
            while ((r = result) == null) {
                if (Thread.interrupted()) throw new InterruptedException();
                long left = deadline - System.nanoTime();
                if (left <= 0) throw new TimeoutException();
                TimeUnit.NANOSECONDS.timedWait(this, left);
            }
        }
        return (T) reportGet(r);
    }

    @SuppressWarnings("unchecked")
    public T join() {
        Object r = result;
        if (r == null) {
            try {
                r = waitingGet(false);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        return (T) reportJoin(r);
    }

    @SuppressWarnings("unchecked")
    public T getNow(T valueIfAbsent) {
        Object r = result;
        return r == null ? valueIfAbsent : (T) reportJoin(r);
    }

    public boolean complete(T value) {
        return completeValue(value);
    }

    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        return internalComplete(new AltResult(ex));
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = result == null && internalComplete(new AltResult(new CancellationException()));
        return cancelled || isCancelled();
    }

    public boolean isCancelled() {
        Object r = result;
        return r instanceof AltResult && ((AltResult) r).ex instanceof CancellationException;
    }

    public boolean isCompletedExceptionally() {
        Object r = result;
        return r instanceof AltResult && r != NIL;
    }

    public void obtrudeValue(T value) {
        forceResult(encodeValue(value));
    }

    public void obtrudeException(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        forceResult(new AltResult(ex));
    }

    private void forceResult(Object r) {
        ArrayList<Runnable> run;
        synchronized (this) {
            result = r;
            notifyAll();
            run = dependents;
            dependents = null;
        }
        if (run != null) {
            // a stack, as OpenJDK: the latest dependent runs first
            for (int i = run.size() - 1; i >= 0; i--) run.get(i).run();
        }
    }

    public synchronized int getNumberOfDependents() {
        return dependents == null ? 0 : dependents.size();
    }

    // ---- single-source stages ----

    private <V> CompletableFuture<V> uniApplyStage(final Executor e, final Function<? super T, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<V> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                final Object r = result;
                Throwable x = exOf(r);
                if (x != null) {
                    d.completeThrowable(x, r);
                    return;
                }
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            d.completeValue(f.apply(CompletableFuture.<T>valueOf(r)));
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        });
        return d;
    }

    private static <A> Function<A, Void> consumerFn(final Consumer<? super A> c) {
        if (c == null) throw new NullPointerException();
        return new Function<A, Void>() {
            public Void apply(A a) {
                c.accept(a);
                return null;
            }
        };
    }

    private static <A> Function<A, Void> runnableFn(final Runnable r) {
        if (r == null) throw new NullPointerException();
        return new Function<A, Void>() {
            public Void apply(A a) {
                r.run();
                return null;
            }
        };
    }

    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return uniApplyStage(null, fn);
    }

    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return uniApplyStage(defaultExecutor(), fn);
    }

    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return uniApplyStage(screenExecutor(executor), fn);
    }

    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniApplyStage(null, CompletableFuture.<T>consumerFn(action));
    }

    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return uniApplyStage(defaultExecutor(), CompletableFuture.<T>consumerFn(action));
    }

    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return uniApplyStage(screenExecutor(executor), CompletableFuture.<T>consumerFn(action));
    }

    public CompletableFuture<Void> thenRun(Runnable action) {
        return uniApplyStage(null, CompletableFuture.<T>runnableFn(action));
    }

    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return uniApplyStage(defaultExecutor(), CompletableFuture.<T>runnableFn(action));
    }

    public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return uniApplyStage(screenExecutor(executor), CompletableFuture.<T>runnableFn(action));
    }

    private <V> CompletableFuture<V> uniComposeStage(final Executor e,
            final Function<? super T, ? extends CompletionStage<V>> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<V> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                final Object r = result;
                Throwable x = exOf(r);
                if (x != null) {
                    d.completeThrowable(x, r);
                    return;
                }
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            final CompletableFuture<V> g = f.apply(CompletableFuture.<T>valueOf(r))
                                    .toCompletableFuture();
                            g.onDone(new Runnable() {
                                public void run() {
                                    d.completeRelay(g.result);
                                }
                            });
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        });
        return d;
    }

    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(null, fn);
    }

    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(defaultExecutor(), fn);
    }

    public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn,
            Executor executor) {
        return uniComposeStage(screenExecutor(executor), fn);
    }

    private <V> CompletableFuture<V> uniHandleStage(final Executor e,
            final BiFunction<? super T, Throwable, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<V> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                final Object r = result;
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            d.completeValue(f.apply(CompletableFuture.<T>valueOf(r), exOf(r)));
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        });
        return d;
    }

    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(null, fn);
    }

    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(defaultExecutor(), fn);
    }

    public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return uniHandleStage(screenExecutor(executor), fn);
    }

    private CompletableFuture<T> uniWhenCompleteStage(final Executor e,
            final BiConsumer<? super T, ? super Throwable> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<T> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                final Object r = result;
                fire(e, new Runnable() {
                    public void run() {
                        Throwable x = exOf(r);
                        try {
                            f.accept(CompletableFuture.<T>valueOf(r), x);
                            if (x == null) {
                                d.internalComplete(r);
                                return;
                            }
                        } catch (Throwable ex) {
                            if (x == null) x = ex;
                            else if (x != ex) x.addSuppressed(ex);
                        }
                        d.completeThrowable(x, r);
                    }
                }, d);
            }
        });
        return d;
    }

    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(null, action);
    }

    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(defaultExecutor(), action);
    }

    public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
            Executor executor) {
        return uniWhenCompleteStage(screenExecutor(executor), action);
    }

    private CompletableFuture<T> uniExceptionallyStage(final Executor e, final Function<Throwable, ? extends T> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<T> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                final Object r = result;
                final Throwable x = exOf(r);
                if (x == null) {
                    d.internalComplete(r);
                    return;
                }
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            d.completeValue(f.apply(x));
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        });
        return d;
    }

    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(null, fn);
    }

    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(defaultExecutor(), fn);
    }

    public CompletableFuture<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
        return uniExceptionallyStage(screenExecutor(executor), fn);
    }

    private CompletableFuture<T> uniComposeExceptionallyStage(final Executor e,
            final Function<Throwable, ? extends CompletionStage<T>> f) {
        if (f == null) throw new NullPointerException();
        final CompletableFuture<T> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                final Object r = result;
                final Throwable x = exOf(r);
                if (x == null) {
                    d.internalComplete(r);
                    return;
                }
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            final CompletableFuture<T> g = f.apply(x).toCompletableFuture();
                            g.onDone(new Runnable() {
                                public void run() {
                                    d.completeRelay(g.result);
                                }
                            });
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        });
        return d;
    }

    public CompletableFuture<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return uniComposeExceptionallyStage(null, fn);
    }

    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
        return uniComposeExceptionallyStage(defaultExecutor(), fn);
    }

    public CompletableFuture<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn,
            Executor executor) {
        return uniComposeExceptionallyStage(screenExecutor(executor), fn);
    }

    // ---- two-source stages ----

    private <U, V> CompletableFuture<V> biApplyStage(final Executor e, CompletionStage<U> o,
            final BiFunction<? super T, ? super U, ? extends V> f) {
        final CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null) throw new NullPointerException();
        final CompletableFuture<V> d = newIncompleteFuture();
        final Runnable both = new Runnable() {
            public void run() {
                final Object r = result;
                final Object s = b.result;
                Throwable x;
                if ((x = exOf(r)) != null) {
                    d.completeThrowable(x, r);
                    return;
                }
                if ((x = exOf(s)) != null) {
                    d.completeThrowable(x, s);
                    return;
                }
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            d.completeValue(f.apply(CompletableFuture.<T>valueOf(r), CompletableFuture.<U>valueOf(s)));
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        };
        onDone(new Runnable() {
            public void run() {
                b.onDone(both);
            }
        });
        return d;
    }

    public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(null, other, fn);
    }

    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(defaultExecutor(), other, fn);
    }

    public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return biApplyStage(screenExecutor(executor), other, fn);
    }

    private static <A, B> BiFunction<A, B, Void> biConsumerFn(final BiConsumer<? super A, ? super B> c) {
        if (c == null) throw new NullPointerException();
        return new BiFunction<A, B, Void>() {
            public Void apply(A a, B b) {
                c.accept(a, b);
                return null;
            }
        };
    }

    private static <A, B> BiFunction<A, B, Void> biRunnableFn(final Runnable r) {
        if (r == null) throw new NullPointerException();
        return new BiFunction<A, B, Void>() {
            public Void apply(A a, B b) {
                r.run();
                return null;
            }
        };
    }

    public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biApplyStage(null, other, CompletableFuture.<T, U>biConsumerFn(action));
    }

    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biApplyStage(defaultExecutor(), other, CompletableFuture.<T, U>biConsumerFn(action));
    }

    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action, Executor executor) {
        return biApplyStage(screenExecutor(executor), other, CompletableFuture.<T, U>biConsumerFn(action));
    }

    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return biApplyStage(null, other, CompletableFuture.<T, Object>biRunnableFn(action));
    }

    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return biApplyStage(defaultExecutor(), other, CompletableFuture.<T, Object>biRunnableFn(action));
    }

    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return biApplyStage(screenExecutor(executor), other, CompletableFuture.<T, Object>biRunnableFn(action));
    }

    @SuppressWarnings("unchecked")
    private <U, V> CompletableFuture<V> orApplyStage(final Executor e, CompletionStage<U> o,
            final Function<? super U, ? extends V> f) {
        final CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null) throw new NullPointerException();
        final CompletableFuture<V> d = newIncompleteFuture();
        final CompletableFuture<?>[] srcs = {this, b};
        final boolean[] claimed = new boolean[1];
        Runnable either = new Runnable() {
            public void run() {
                final Object r;
                synchronized (claimed) {
                    if (claimed[0]) return;
                    Object a = srcs[0].result;
                    r = a != null ? a : srcs[1].result;
                    if (r == null) return;
                    claimed[0] = true;
                }
                Throwable x = exOf(r);
                if (x != null) {
                    d.completeThrowable(x, r);
                    return;
                }
                fire(e, new Runnable() {
                    public void run() {
                        try {
                            d.completeValue(f.apply((U) valueOf(r)));
                        } catch (Throwable ex) {
                            d.completeThrowable(ex);
                        }
                    }
                }, d);
            }
        };
        onDone(either);
        b.onDone(either);
        return d;
    }

    public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(null, other, fn);
    }

    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(defaultExecutor(), other, fn);
    }

    public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn,
            Executor executor) {
        return orApplyStage(screenExecutor(executor), other, fn);
    }

    public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orApplyStage(null, other, CompletableFuture.<T>consumerFn(action));
    }

    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other,
            Consumer<? super T> action) {
        return orApplyStage(defaultExecutor(), other, CompletableFuture.<T>consumerFn(action));
    }

    public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action,
            Executor executor) {
        return orApplyStage(screenExecutor(executor), other, CompletableFuture.<T>consumerFn(action));
    }

    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return orApplyStage(null, other, CompletableFuture.<Object>runnableFn(action));
    }

    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return orApplyStage(defaultExecutor(), other, CompletableFuture.<Object>runnableFn(action));
    }

    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action,
            Executor executor) {
        return orApplyStage(screenExecutor(executor), other, CompletableFuture.<Object>runnableFn(action));
    }

    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // ---- aggregates ----

    public static CompletableFuture<Void> allOf(final CompletableFuture<?>... cfs) {
        final CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (cfs.length == 0) {
            d.result = NIL;
            return d;
        }
        for (CompletableFuture<?> f : cfs) {
            if (f == null) throw new NullPointerException();
        }
        final int[] remaining = {cfs.length};
        Runnable one = new Runnable() {
            public void run() {
                synchronized (remaining) {
                    if (--remaining[0] != 0) return;
                }
                for (CompletableFuture<?> g : cfs) {
                    Object r = g.result;
                    Throwable x = exOf(r);
                    if (x != null) {
                        d.completeThrowable(x, r);
                        return;
                    }
                }
                d.completeValue(null);
            }
        };
        for (CompletableFuture<?> f : cfs) f.onDone(one);
        return d;
    }

    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        final CompletableFuture<Object> d = new CompletableFuture<Object>();
        for (CompletableFuture<?> f : cfs) {
            if (f == null) throw new NullPointerException();
        }
        for (CompletableFuture<?> f : cfs) {
            Object r = f.result;
            if (r != null) {
                d.result = encodeRelay(r);
                return d;
            }
        }
        for (final CompletableFuture<?> f : cfs) {
            f.onDone(new Runnable() {
                public void run() {
                    d.completeRelay(f.result);
                }
            });
        }
        return d;
    }

    // ---- Java 9 additions ----

    public CompletableFuture<T> copy() {
        final CompletableFuture<T> d = newIncompleteFuture();
        onDone(new Runnable() {
            public void run() {
                d.completeRelay(result);
            }
        });
        return d;
    }

    public CompletionStage<T> minimalCompletionStage() {
        final MinimalStage<T> d = new MinimalStage<T>();
        onDone(new Runnable() {
            public void run() {
                d.completeRelay(result);
            }
        });
        return d;
    }

    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
        if (supplier == null || executor == null) throw new NullPointerException();
        executor.execute(supplyTask(this, supplier));
        return this;
    }

    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
        return completeAsync(supplier, defaultExecutor());
    }

    public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
        if (unit == null) throw new NullPointerException();
        if (result == null) {
            final ScheduledFuture<?> timer = Delayer.delay(new Runnable() {
                public void run() {
                    if (result == null) completeExceptionally(new TimeoutException());
                }
            }, timeout, unit);
            whenComplete(canceller(timer));
        }
        return this;
    }

    public CompletableFuture<T> completeOnTimeout(final T value, long timeout, TimeUnit unit) {
        if (unit == null) throw new NullPointerException();
        if (result == null) {
            final ScheduledFuture<?> timer = Delayer.delay(new Runnable() {
                public void run() {
                    complete(value);
                }
            }, timeout, unit);
            whenComplete(canceller(timer));
        }
        return this;
    }

    private static BiConsumer<Object, Throwable> canceller(final ScheduledFuture<?> f) {
        return new BiConsumer<Object, Throwable>() {
            public void accept(Object ignore, Throwable ex) {
                if (f != null && !f.isDone()) f.cancel(false);
            }
        };
    }

    public static Executor delayedExecutor(final long delay, final TimeUnit unit, final Executor executor) {
        if (unit == null || executor == null) throw new NullPointerException();
        return new Executor() {
            public void execute(final Runnable r) {
                if (r == null) throw new NullPointerException();
                Delayer.delay(new Runnable() {
                    public void run() {
                        executor.execute(r);
                    }
                }, delay, unit);
            }
        };
    }

    public static Executor delayedExecutor(long delay, TimeUnit unit) {
        return delayedExecutor(delay, unit, ASYNC_POOL);
    }

    /** One daemon thread for timeouts and delayed executors, created on first use. */
    static final class Delayer {
        static ScheduledThreadPoolExecutor sDelayer;

        static synchronized ScheduledFuture<?> delay(Runnable command, long delay, TimeUnit unit) {
            if (sDelayer == null) {
                sDelayer = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "CompletableFutureDelayScheduler");
                        t.setDaemon(true);
                        return t;
                    }
                });
                sDelayer.setRemoveOnCancelPolicy(true);
            }
            return sDelayer.schedule(command, delay, unit);
        }
    }

    /** A stage without the Future and completion methods (OpenJDK MinimalStage). */
    static final class MinimalStage<T> extends CompletableFuture<T> {
        MinimalStage() {
        }

        MinimalStage(Object r) {
            super(r);
        }

        @Override public <U> CompletableFuture<U> newIncompleteFuture() { return new MinimalStage<U>(); }

        @Override public T get() { throw new UnsupportedOperationException(); }

        @Override public T get(long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }

        @Override public T getNow(T valueIfAbsent) { throw new UnsupportedOperationException(); }

        @Override public T join() { throw new UnsupportedOperationException(); }

        @Override public boolean complete(T value) { throw new UnsupportedOperationException(); }

        @Override public boolean completeExceptionally(Throwable ex) { throw new UnsupportedOperationException(); }

        @Override public boolean cancel(boolean mayInterruptIfRunning) { throw new UnsupportedOperationException(); }

        @Override public void obtrudeValue(T value) { throw new UnsupportedOperationException(); }

        @Override public void obtrudeException(Throwable ex) { throw new UnsupportedOperationException(); }

        @Override public boolean isDone() { throw new UnsupportedOperationException(); }

        @Override public boolean isCancelled() { throw new UnsupportedOperationException(); }

        @Override public boolean isCompletedExceptionally() { throw new UnsupportedOperationException(); }

        @Override public int getNumberOfDependents() { throw new UnsupportedOperationException(); }

        @Override public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier, Executor executor) {
            throw new UnsupportedOperationException();
        }

        @Override public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
            throw new UnsupportedOperationException();
        }

        @Override public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public CompletableFuture<T> toCompletableFuture() {
            Object r = result;
            if (r != null) return new CompletableFuture<T>(encodeRelay(r));
            final CompletableFuture<T> d = new CompletableFuture<T>();
            onDone(new Runnable() {
                public void run() {
                    d.completeRelay(result);
                }
            });
            return d;
        }
    }

    public String toString() {
        Object r = result;
        int count;
        synchronized (this) {
            count = dependents == null ? 0 : dependents.size();
        }
        return super.toString() + (r == null
                ? (count == 0 ? "[Not completed]" : "[Not completed, " + count + " dependents]")
                : (r instanceof AltResult && ((AltResult) r).ex != null
                        ? "[Completed exceptionally: " + ((AltResult) r).ex + "]" : "[Completed normally]"));
    }
}
