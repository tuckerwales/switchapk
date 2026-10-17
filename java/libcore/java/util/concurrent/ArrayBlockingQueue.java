package java.util.concurrent;

import java.util.Collection;

public class ArrayBlockingQueue<E> extends LinkedBlockingDeque<E> {
    private static final long serialVersionUID = -817911632652898426L;

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
