package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Blocking deque protected by its own monitor. Also backs the other blocking queues. */
public class LinkedBlockingDeque<E> extends AbstractQueue<E> implements BlockingDeque<E>, java.io.Serializable {
    private final ArrayDeque<E> q = new ArrayDeque<E>();
    private final int capacity;

    public LinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    public LinkedBlockingDeque(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
    }

    public LinkedBlockingDeque(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        addAll(c);
    }

    public synchronized boolean offerFirst(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (q.size() >= capacity) {
            return false;
        }
        q.addFirst(e);
        notifyAll();
        return true;
    }

    public synchronized boolean offerLast(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (q.size() >= capacity) {
            return false;
        }
        q.addLast(e);
        notifyAll();
        return true;
    }

    public void addFirst(E e) {
        if (!offerFirst(e)) {
            throw new IllegalStateException("Deque full");
        }
    }

    public void addLast(E e) {
        if (!offerLast(e)) {
            throw new IllegalStateException("Deque full");
        }
    }

    public synchronized void putFirst(E e) throws InterruptedException {
        while (q.size() >= capacity) {
            wait();
        }
        q.addFirst(e);
        notifyAll();
    }

    public synchronized void putLast(E e) throws InterruptedException {
        while (q.size() >= capacity) {
            wait();
        }
        q.addLast(e);
        notifyAll();
    }

    public synchronized boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (q.size() >= capacity) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        q.addLast(e);
        notifyAll();
        return true;
    }

    public synchronized E pollFirst() {
        E e = q.pollFirst();
        if (e != null) {
            notifyAll();
        }
        return e;
    }

    public synchronized E pollLast() {
        E e = q.pollLast();
        if (e != null) {
            notifyAll();
        }
        return e;
    }

    public E removeFirst() {
        E x = pollFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public E removeLast() {
        E x = pollLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public synchronized E takeFirst() throws InterruptedException {
        while (q.isEmpty()) {
            wait();
        }
        E e = q.pollFirst();
        notifyAll();
        return e;
    }

    public synchronized E takeLast() throws InterruptedException {
        while (q.isEmpty()) {
            wait();
        }
        E e = q.pollLast();
        notifyAll();
        return e;
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
        E e = q.pollFirst();
        notifyAll();
        return e;
    }

    public synchronized E getFirst() {
        return q.getFirst();
    }

    public synchronized E getLast() {
        return q.getLast();
    }

    public synchronized E peekFirst() {
        return q.peekFirst();
    }

    public synchronized E peekLast() {
        return q.peekLast();
    }

    public synchronized boolean removeFirstOccurrence(Object o) {
        return q.removeFirstOccurrence(o);
    }

    public synchronized boolean removeLastOccurrence(Object o) {
        return q.removeLastOccurrence(o);
    }

    public boolean add(E e) {
        addLast(e);
        return true;
    }

    public boolean offer(E e) {
        return offerLast(e);
    }

    public void put(E e) throws InterruptedException {
        putLast(e);
    }

    public E remove() {
        return removeFirst();
    }

    public E poll() {
        return pollFirst();
    }

    public E take() throws InterruptedException {
        return takeFirst();
    }

    public E element() {
        return getFirst();
    }

    public E peek() {
        return peekFirst();
    }

    public synchronized int remainingCapacity() {
        return capacity - q.size();
    }

    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    public synchronized int drainTo(Collection<? super E> c, int maxElements) {
        int n = 0;
        while (n < maxElements && !q.isEmpty()) {
            c.add(q.pollFirst());
            n++;
        }
        if (n > 0) {
            notifyAll();
        }
        return n;
    }

    public void push(E e) {
        addFirst(e);
    }

    public E pop() {
        return removeFirst();
    }

    public synchronized boolean remove(Object o) {
        return q.removeFirstOccurrence(o);
    }

    public synchronized int size() {
        return q.size();
    }

    public synchronized boolean contains(Object o) {
        return q.contains(o);
    }

    public synchronized Object[] toArray() {
        return q.toArray();
    }

    public synchronized void clear() {
        q.clear();
        notifyAll();
    }

    public synchronized Iterator<E> iterator() {
        return new ArrayList<E>(q).iterator();
    }

    public synchronized Iterator<E> descendingIterator() {
        return q.descendingIterator();
    }
}
