package java.util.concurrent;

import java.util.Collection;

public class ConcurrentLinkedQueue<E> extends LinkedBlockingDeque<E> {
    public ConcurrentLinkedQueue() {
        super();
    }

    public ConcurrentLinkedQueue(Collection<? extends E> c) {
        super(c);
    }
}
