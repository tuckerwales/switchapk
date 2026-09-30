package java.util.concurrent;

import java.util.Deque;

public interface BlockingDeque<E> extends BlockingQueue<E>, Deque<E> {
    void putFirst(E e) throws InterruptedException;

    void putLast(E e) throws InterruptedException;

    E takeFirst() throws InterruptedException;

    E takeLast() throws InterruptedException;
}
