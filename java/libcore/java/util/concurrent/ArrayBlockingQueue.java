package java.util.concurrent;

import java.util.Collection;

public class ArrayBlockingQueue<E> extends LinkedBlockingDeque<E> {
    public ArrayBlockingQueue(int capacity) {
        super(capacity);
    }

    public ArrayBlockingQueue(int capacity, boolean fair) {
        super(capacity);
    }

    public ArrayBlockingQueue(int capacity, boolean fair, Collection<? extends E> c) {
        super(capacity);
        addAll(c);
    }
}
