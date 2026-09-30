package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

public class PriorityBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private final PriorityQueue<E> q;

    public PriorityBlockingQueue() {
        q = new PriorityQueue<E>();
    }

    public PriorityBlockingQueue(int initialCapacity) {
        q = new PriorityQueue<E>(initialCapacity);
    }

    public PriorityBlockingQueue(int initialCapacity, Comparator<? super E> comparator) {
        q = new PriorityQueue<E>(initialCapacity, comparator);
    }

    public synchronized boolean offer(E e) {
        q.offer(e);
        notifyAll();
        return true;
    }

    public void put(E e) {
        offer(e);
    }

    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    public synchronized E poll() {
        return q.poll();
    }

    public synchronized E take() throws InterruptedException {
        while (q.isEmpty()) {
            wait();
        }
        return q.poll();
    }

    public synchronized E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (q.isEmpty()) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return null;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return q.poll();
    }

    public synchronized E peek() {
        return q.peek();
    }

    public synchronized int size() {
        return q.size();
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    public synchronized boolean remove(Object o) {
        return q.remove(o);
    }

    public synchronized boolean contains(Object o) {
        return q.contains(o);
    }

    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    public synchronized int drainTo(Collection<? super E> c, int maxElements) {
        int n = 0;
        while (n < maxElements && !q.isEmpty()) {
            c.add(q.poll());
            n++;
        }
        return n;
    }

    public synchronized Iterator<E> iterator() {
        return new ArrayList<E>(q).iterator();
    }

    public synchronized void clear() {
        q.clear();
    }
}
