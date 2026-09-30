package java.util.concurrent.atomic;

public class LongAdder extends Number implements java.io.Serializable {
    private long value;

    public LongAdder() {
    }

    public synchronized void add(long x) {
        value += x;
    }

    public void increment() {
        add(1L);
    }

    public void decrement() {
        add(-1L);
    }

    public synchronized long sum() {
        return value;
    }

    public synchronized void reset() {
        value = 0;
    }

    public synchronized long sumThenReset() {
        long v = value;
        value = 0;
        return v;
    }

    public String toString() {
        return Long.toString(sum());
    }

    public long longValue() {
        return sum();
    }

    public int intValue() {
        return (int) sum();
    }

    public float floatValue() {
        return (float) sum();
    }

    public double doubleValue() {
        return (double) sum();
    }
}
