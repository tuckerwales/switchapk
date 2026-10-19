package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * A zero-capacity handoff queue: an insert succeeds only when a taker receives the element. A
 * monitor-based version of OpenJDK's: waiting putters queue their elements in FIFO order and the
 * non-blocking offer succeeds only while more takers are waiting than elements already handed
 * over. Fair and unfair modes behave the same (FIFO).
 */
public class SynchronousQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    private static final class Node<E> {
        final E item;
        boolean taken;

        Node(E item) {
            this.item = item;
        }
    }

    private final transient Object lock = new Object();
    private final transient ArrayDeque<Node<E>> nodes = new ArrayDeque<Node<E>>(); // offered, not yet taken
    private transient int waitingTakers;

    public SynchronousQueue() {
    }

    public SynchronousQueue(boolean fair) {
    }

    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (!transfer(e, true, 0)) throw new InterruptedException();
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        return transfer(e, false, unit.toNanos(timeout));
    }

    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        synchronized (lock) {
            if (waitingTakers <= nodes.size()) return false;
            nodes.addLast(new Node<E>(e));
            lock.notifyAll();
            return true;
        }
    }

    /** Hands {@code e} to a taker, waiting up to {@code nanos} (forever when {@code forever}). */
    private boolean transfer(E e, boolean forever, long nanos) throws InterruptedException {
        Node<E> n = new Node<E>(e);
        synchronized (lock) {
            nodes.addLast(n);
            lock.notifyAll();
            long deadline = System.nanoTime() + nanos;
            try {
                while (!n.taken) {
                    if (forever && nanos == 0) {
                        lock.wait();
                    } else {
                        long left = deadline - System.nanoTime();
                        if (left <= 0) {
                            nodes.remove(n);
                            return false;
                        }
                        TimeUnit.NANOSECONDS.timedWait(lock, left);
                    }
                }
                return true;
            } catch (InterruptedException ie) {
                if (n.taken) {
                    Thread.currentThread().interrupt();
                    return true;
                }
                nodes.remove(n);
                throw ie;
            }
        }
    }

    public E take() throws InterruptedException {
        synchronized (lock) {
            waitingTakers++;
            try {
                while (nodes.isEmpty()) lock.wait();
                return takeFirst();
            } finally {
                waitingTakers--;
            }
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        synchronized (lock) {
            if (!nodes.isEmpty()) return takeFirst();
            if (nanos <= 0) return null;
            waitingTakers++;
            try {
                long deadline = System.nanoTime() + nanos;
                while (nodes.isEmpty()) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) return null;
                    TimeUnit.NANOSECONDS.timedWait(lock, left);
                }
                return takeFirst();
            } finally {
                waitingTakers--;
            }
        }
    }

    public E poll() {
        synchronized (lock) {
            return nodes.isEmpty() ? null : takeFirst();
        }
    }

    private E takeFirst() {
        Node<E> n = nodes.removeFirst();
        n.taken = true;
        lock.notifyAll();
        return n.item;
    }

    public boolean isEmpty() { return true; }

    public int size() { return 0; }

    public int remainingCapacity() { return 0; }

    public void clear() {}

    public boolean contains(Object o) { return false; }

    public boolean remove(Object o) { return false; }

    public boolean containsAll(Collection<?> c) { return c.isEmpty(); }

    public boolean removeAll(Collection<?> c) { return false; }

    public boolean retainAll(Collection<?> c) { return false; }

    public E peek() { return null; }

    public Iterator<E> iterator() { return Collections.emptyIterator(); }

    public Spliterator<E> spliterator() { return Spliterators.emptySpliterator(); }

    public Object[] toArray() { return new Object[0]; }

    public <T> T[] toArray(T[] a) {
        if (a.length > 0) a[0] = null;
        return a;
    }

    public String toString() { return "[]"; }

    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) throw new NullPointerException();
        if (c == this) throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null; n++) c.add(e);
        return n;
    }
}
