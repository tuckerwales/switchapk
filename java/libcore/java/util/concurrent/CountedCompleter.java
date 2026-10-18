package java.util.concurrent;

/**
 * A ForkJoinTask completed when its pending count reaches zero, triggering its completer in turn (the JDK's
 * contract). compute() runs the work; completion comes from tryComplete() and friends, not from returning.
 */
public abstract class CountedCompleter<T> extends ForkJoinTask<T> {
    private static final long serialVersionUID = 5232453752276485070L;

    final CountedCompleter<?> completer;
    volatile int pending;

    protected CountedCompleter(CountedCompleter<?> completer, int initialPendingCount) {
        this.completer = completer;
        this.pending = initialPendingCount;
    }

    protected CountedCompleter(CountedCompleter<?> completer) {
        this.completer = completer;
    }

    protected CountedCompleter() {
        this.completer = null;
    }

    public abstract void compute();

    public void onCompletion(CountedCompleter<?> caller) {
    }

    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter<?> caller) {
        return true;
    }

    public final CountedCompleter<?> getCompleter() {
        return completer;
    }

    public final int getPendingCount() {
        return pending;
    }

    public final void setPendingCount(int count) {
        pending = count;
    }

    public final synchronized void addToPendingCount(int delta) {
        pending += delta;
    }

    public final synchronized boolean compareAndSetPendingCount(int expected, int count) {
        if (pending != expected) {
            return false;
        }
        pending = count;
        return true;
    }

    public final synchronized int decrementPendingCountUnlessZero() {
        int c = pending;
        if (c != 0) {
            pending = c - 1;
        }
        return c;
    }

    public final CountedCompleter<?> getRoot() {
        CountedCompleter<?> a = this, p;
        while ((p = a.completer) != null) {
            a = p;
        }
        return a;
    }

    public final void tryComplete() {
        CountedCompleter<?> a = this, s = a;
        for (int c;;) {
            if ((c = a.pending) == 0) {
                a.onCompletion(s);
                if ((a = (s = a).completer) == null) {
                    s.quietlyComplete();
                    return;
                }
            } else if (a.compareAndSetPendingCount(c, c - 1)) {
                return;
            }
        }
    }

    public final void propagateCompletion() {
        CountedCompleter<?> a = this, s;
        for (int c;;) {
            if ((c = a.pending) == 0) {
                if ((a = (s = a).completer) == null) {
                    s.quietlyComplete();
                    return;
                }
            } else if (a.compareAndSetPendingCount(c, c - 1)) {
                return;
            }
        }
    }

    public void complete(T rawResult) {
        setRawResult(rawResult);
        onCompletion(this);
        quietlyComplete();
        CountedCompleter<?> p = completer;
        if (p != null) {
            p.tryComplete();
        }
    }

    public final CountedCompleter<?> firstComplete() {
        for (int c;;) {
            if ((c = pending) == 0) {
                return this;
            } else if (compareAndSetPendingCount(c, c - 1)) {
                return null;
            }
        }
    }

    public final CountedCompleter<?> nextComplete() {
        CountedCompleter<?> p = completer;
        if (p != null) {
            return p.firstComplete();
        }
        quietlyComplete();
        return null;
    }

    public final void quietlyCompleteRoot() {
        for (CountedCompleter<?> a = this, p;;) {
            if ((p = a.completer) == null) {
                a.quietlyComplete();
                return;
            }
            a = p;
        }
    }

    public final void helpComplete(int maxTasks) {
        ForkJoinPool p = getPool();
        for (int i = 0; i < maxTasks && status == 0 && p != null; i++) {
            ForkJoinTask<?> t = p.pollTask();
            if (t == null) {
                break;
            }
            t.doExec();
            p.afterTask(t);
        }
    }

    void internalPropagateException(Throwable ex) {
        CountedCompleter<?> a = this, s = a;
        while (a.onExceptionalCompletion(ex, s) && (a = (s = a).completer) != null && a.status == 0
                && a.recordExceptionalCompletion(ex) == EXCEPTIONAL) {
        }
    }

    protected final boolean exec() {
        compute();
        return false;
    }

    public T getRawResult() {
        return null;
    }

    protected void setRawResult(T t) {
    }
}
