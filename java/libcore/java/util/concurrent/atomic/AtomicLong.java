package java.util.concurrent.atomic;

public class AtomicLong extends Number implements java.io.Serializable {
    private volatile long value;

    public AtomicLong(long initialValue) {
        value = initialValue;
    }

    public AtomicLong() {
    }

    public final long get() {
        return value;
    }

    public final void set(long newValue) {
        value = newValue;
    }

    public final void lazySet(long newValue) {
        value = newValue;
    }

    public final long getPlain() {
        return value;
    }

    public final void setPlain(long newValue) {
        value = newValue;
    }

    public final long getAcquire() {
        return value;
    }

    public final void setRelease(long newValue) {
        value = newValue;
    }

    public final synchronized long getAndSet(long newValue) {
        long old = value;
        value = newValue;
        return old;
    }

    public final synchronized boolean compareAndSet(long expect, long update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(long expect, long update) {
        return compareAndSet(expect, update);
    }

    public final synchronized long getAndIncrement() {
        return value++;
    }

    public final synchronized long getAndDecrement() {
        return value--;
    }

    public final synchronized long getAndAdd(long delta) {
        long old = value;
        value += delta;
        return old;
    }

    public final synchronized long incrementAndGet() {
        return ++value;
    }

    public final synchronized long decrementAndGet() {
        return --value;
    }

    public final synchronized long addAndGet(long delta) {
        value += delta;
        return value;
    }

    public final synchronized long getAndUpdate(java.util.function.LongUnaryOperator updateFunction) {
        long prev = value;
        value = updateFunction.applyAsLong(prev);
        return prev;
    }

    public final synchronized long updateAndGet(java.util.function.LongUnaryOperator updateFunction) {
        value = updateFunction.applyAsLong(value);
        return value;
    }

    public final synchronized long getAndAccumulate(long x, java.util.function.LongBinaryOperator accumulatorFunction) {
        long prev = value;
        value = accumulatorFunction.applyAsLong(prev, x);
        return prev;
    }

    public final synchronized long accumulateAndGet(long x, java.util.function.LongBinaryOperator accumulatorFunction) {
        value = accumulatorFunction.applyAsLong(value, x);
        return value;
    }

    public String toString() {
        return Long.toString(get());
    }

    public int intValue() {
        return (int) get();
    }

    public long longValue() {
        return (long) get();
    }

    public float floatValue() {
        return (float) get();
    }

    public double doubleValue() {
        return (double) get();
    }
}
