package java.util.concurrent.atomic;

public class AtomicLongArray implements java.io.Serializable {
    private final long[] array;

    public AtomicLongArray(int length) {
        array = new long[length];
    }

    public AtomicLongArray(long[] array) {
        this.array = array.clone();
    }

    public final int length() {
        return array.length;
    }

    public final synchronized long get(int i) {
        return array[i];
    }

    public final synchronized void set(int i, long newValue) {
        array[i] = newValue;
    }

    public final void lazySet(int i, long newValue) {
        set(i, newValue);
    }

    public final synchronized long getAndSet(int i, long newValue) {
        long old = array[i];
        array[i] = newValue;
        return old;
    }

    public final synchronized boolean compareAndSet(int i, long expect, long update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public final synchronized long getAndIncrement(int i) {
        return array[i]++;
    }

    public final synchronized long getAndDecrement(int i) {
        return array[i]--;
    }

    public final synchronized long getAndAdd(int i, long delta) {
        long old = array[i];
        array[i] += delta;
        return old;
    }

    public final synchronized long incrementAndGet(int i) {
        return ++array[i];
    }

    public final synchronized long decrementAndGet(int i) {
        return --array[i];
    }

    public final synchronized long addAndGet(int i, long delta) {
        array[i] += delta;
        return array[i];
    }

    public synchronized String toString() {
        return java.util.Arrays.toString(array);
    }
}
