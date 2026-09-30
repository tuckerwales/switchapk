package java.util.concurrent;

import java.util.Collection;

public class ConcurrentLinkedDeque<E> extends LinkedBlockingDeque<E> {
    public ConcurrentLinkedDeque() {
        super();
    }

    public ConcurrentLinkedDeque(Collection<? extends E> c) {
        super(c);
    }
}
