package java.util.concurrent;

public enum TimeUnit {
    NANOSECONDS(1L),
    MICROSECONDS(1000L),
    MILLISECONDS(1000000L),
    SECONDS(1000000000L),
    MINUTES(60000000000L),
    HOURS(3600000000000L),
    DAYS(86400000000000L);

    private final long nanos;

    TimeUnit(long nanos) {
        this.nanos = nanos;
    }

    private static long convert(long d, long srcNanos, long dstNanos) {
        if (srcNanos == dstNanos) {
            return d;
        }
        if (srcNanos > dstNanos) {
            long r = srcNanos / dstNanos;
            long max = Long.MAX_VALUE / r;
            if (d > max) {
                return Long.MAX_VALUE;
            }
            if (d < -max) {
                return Long.MIN_VALUE;
            }
            return d * r;
        }
        return d / (dstNanos / srcNanos);
    }

    public long convert(long sourceDuration, TimeUnit sourceUnit) {
        return convert(sourceDuration, sourceUnit.nanos, nanos);
    }

    public long toNanos(long d) {
        return convert(d, nanos, 1L);
    }

    public long toMicros(long d) {
        return convert(d, nanos, 1000L);
    }

    public long toMillis(long d) {
        return convert(d, nanos, 1000000L);
    }

    public long toSeconds(long d) {
        return convert(d, nanos, 1000000000L);
    }

    public long toMinutes(long d) {
        return convert(d, nanos, 60000000000L);
    }

    public long toHours(long d) {
        return convert(d, nanos, 3600000000000L);
    }

    public long toDays(long d) {
        return convert(d, nanos, 86400000000000L);
    }

    public void timedWait(Object obj, long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = (int) (toNanos(timeout) - ms * 1000000L);
            obj.wait(ms, ns);
        }
    }

    public void timedJoin(Thread thread, long timeout) throws InterruptedException {
        if (timeout > 0) {
            thread.join(toMillis(timeout));
        }
    }

    public void sleep(long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = (int) (toNanos(timeout) - ms * 1000000L);
            Thread.sleep(ms, ns);
        }
    }
}
