package java.util;

import java.util.function.DoubleConsumer;

public class DoubleSummaryStatistics implements DoubleConsumer {
    private long count;
    private double sum;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    public DoubleSummaryStatistics() {
    }

    public void accept(double value) {
        ++count;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    public void combine(DoubleSummaryStatistics other) {
        count += other.count;
        sum += other.sum;
        min = Math.min(min, other.min);
        max = Math.max(max, other.max);
    }

    public final long getCount() {
        return count;
    }

    public final double getSum() {
        return sum;
    }

    public final double getMin() {
        return min;
    }

    public final double getMax() {
        return max;
    }

    public final double getAverage() {
        return getCount() > 0 ? (double) getSum() / getCount() : 0.0d;
    }

    public String toString() {
        return getClass().getSimpleName() + "{count=" + count + ", sum=" + sum + ", min=" + min + ", average="
                + getAverage() + ", max=" + max + "}";
    }
}
