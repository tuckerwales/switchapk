package java.util.concurrent;

import java.util.Collection;

public class LinkedBlockingQueue<E> extends LinkedBlockingDeque<E> {
    public LinkedBlockingQueue() {
        super();
    }

    public LinkedBlockingQueue(int capacity) {
        super(capacity);
    }

    public LinkedBlockingQueue(Collection<? extends E> c) {
        super(c);
    }
}
