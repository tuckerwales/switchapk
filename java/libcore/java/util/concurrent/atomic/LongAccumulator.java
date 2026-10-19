package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;

public class LongAccumulator extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 7249069246863182397L;
    private final LongBinaryOperator function;
    private final long identity;
    private long value;

    public LongAccumulator(LongBinaryOperator accumulatorFunction, long identity) {
        this.function = accumulatorFunction;
        this.identity = identity;
        this.value = identity;
    }

    public synchronized void accumulate(long x) {
        value = function.applyAsLong(value, x);
    }

    public synchronized long get() {
        return value;
    }

    public synchronized void reset() {
        value = identity;
    }

    public synchronized long getThenReset() {
        long v = value;
        value = identity;
        return v;
    }

    public String toString() {
        return Long.toString(get());
    }

    public long longValue() {
        return get();
    }

    public int intValue() {
        return (int) get();
    }

    public float floatValue() {
        return (float) get();
    }

    public double doubleValue() {
        return (double) get();
    }
}
