package java.util.concurrent;

import java.util.Collection;

public class DelayQueue<E extends Delayed> extends PriorityBlockingQueue<E> {
    public DelayQueue() {
        super();
    }

    public synchronized E poll() {
        E first = peek();
        if (first == null || first.getDelay(TimeUnit.NANOSECONDS) > 0) {
            return null;
        }
        return super.poll();
    }

    public synchronized E take() throws InterruptedException {
        for (;;) {
            E first = peek();
            if (first == null) {
                wait();
            } else {
                long delay = first.getDelay(TimeUnit.NANOSECONDS);
                if (delay <= 0) {
                    return super.poll();
                }
                TimeUnit.NANOSECONDS.timedWait(this, delay);
            }
        }
    }

    public synchronized E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (;;) {
            E first = peek();
            long left = deadline - System.nanoTime();
            if (first != null && first.getDelay(TimeUnit.NANOSECONDS) <= 0) {
                return super.poll();
            }
            if (left <= 0) {
                return null;
            }
            long wait = first == null ? left : Math.min(left, first.getDelay(TimeUnit.NANOSECONDS));
            TimeUnit.NANOSECONDS.timedWait(this, Math.max(1, wait));
        }
    }
}
