package java.util.concurrent;

import java.util.Collection;

public class ConcurrentLinkedQueue<E> extends LinkedBlockingDeque<E> {
    private static final long serialVersionUID = 196745693267521676L;

    public ConcurrentLinkedQueue() {
        super();
    }

    public ConcurrentLinkedQueue(Collection<? extends E> c) {
        super(c);
    }
}
