package java.util.concurrent.atomic;

public class AtomicInteger extends Number implements java.io.Serializable {
    private volatile int value;

    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    public AtomicInteger() {
    }

    public final int get() {
        return value;
    }

    public final void set(int newValue) {
        value = newValue;
    }

    public final void lazySet(int newValue) {
        value = newValue;
    }

    public final int getPlain() {
        return value;
    }

    public final void setPlain(int newValue) {
        value = newValue;
    }

    public final int getAcquire() {
        return value;
    }

    public final void setRelease(int newValue) {
        value = newValue;
    }

    public final synchronized int getAndSet(int newValue) {
        int old = value;
        value = newValue;
        return old;
    }

    public final synchronized boolean compareAndSet(int expect, int update) {
        if (value == expect) {
            value = update;
            return true;
        }
        return false;
    }

    public final boolean weakCompareAndSet(int expect, int update) {
        return compareAndSet(expect, update);
    }

    public final synchronized int getAndIncrement() {
        return value++;
    }

    public final synchronized int getAndDecrement() {
        return value--;
    }

    public final synchronized int getAndAdd(int delta) {
        int old = value;
        value += delta;
        return old;
    }

    public final synchronized int incrementAndGet() {
        return ++value;
    }

    public final synchronized int decrementAndGet() {
        return --value;
    }

    public final synchronized int addAndGet(int delta) {
        value += delta;
        return value;
    }

    public final synchronized int getAndUpdate(java.util.function.IntUnaryOperator updateFunction) {
        int prev = value;
        value = updateFunction.applyAsInt(prev);
        return prev;
    }

    public final synchronized int updateAndGet(java.util.function.IntUnaryOperator updateFunction) {
        value = updateFunction.applyAsInt(value);
        return value;
    }

    public final synchronized int getAndAccumulate(int x, java.util.function.IntBinaryOperator accumulatorFunction) {
        int prev = value;
        value = accumulatorFunction.applyAsInt(prev, x);
        return prev;
    }

    public final synchronized int accumulateAndGet(int x, java.util.function.IntBinaryOperator accumulatorFunction) {
        value = accumulatorFunction.applyAsInt(value, x);
        return value;
    }

    public String toString() {
        return Integer.toString(get());
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
