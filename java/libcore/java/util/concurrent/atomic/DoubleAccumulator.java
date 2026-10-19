package java.util.concurrent.atomic;

import java.util.function.DoubleBinaryOperator;

public class DoubleAccumulator extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 7249069246863182397L;
    private final DoubleBinaryOperator function;
    private final double identity;
    private double value;

    public DoubleAccumulator(DoubleBinaryOperator accumulatorFunction, double identity) {
        this.function = accumulatorFunction;
        this.identity = identity;
        this.value = identity;
    }

    public synchronized void accumulate(double x) {
        value = function.applyAsDouble(value, x);
    }

    public synchronized double get() {
        return value;
    }

    public synchronized void reset() {
        value = identity;
    }

    public synchronized double getThenReset() {
        double v = value;
        value = identity;
        return v;
    }

    public String toString() {
        return Double.toString(get());
    }

    public double doubleValue() {
        return get();
    }

    public long longValue() {
        return (long) get();
    }

    public int intValue() {
        return (int) get();
    }

    public float floatValue() {
        return (float) get();
    }
}
