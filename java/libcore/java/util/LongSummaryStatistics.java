package java.util;

import java.util.function.LongConsumer;

public class LongSummaryStatistics implements LongConsumer {
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    public LongSummaryStatistics() {
    }

    public void accept(long value) {
        ++count;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    public void accept(int value) {
        accept((long) value);
    }
    public void combine(LongSummaryStatistics other) {
        count += other.count;
        sum += other.sum;
        min = Math.min(min, other.min);
        max = Math.max(max, other.max);
    }

    public final long getCount() {
        return count;
    }

    public final long getSum() {
        return sum;
    }

    public final long getMin() {
        return min;
    }

    public final long getMax() {
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
