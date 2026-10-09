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
        return String.format("%s{count=%d, sum=%f, min=%f, average=%f, max=%f}", getClass().getSimpleName(),
                count, getSum(), min, getAverage(), max);
    }

    public DoubleSummaryStatistics(long count, double min, double max, double sum) throws IllegalArgumentException {
        if (count < 0L) {
            throw new IllegalArgumentException("Negative count value");
        } else if (count > 0L) {
            if (min > max) {
                throw new IllegalArgumentException("Minimum greater than maximum");
            }
            this.count = count;
            this.sum = sum;
            this.min = min;
            this.max = max;
        }
    }
}
