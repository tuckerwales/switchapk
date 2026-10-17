package java.util.concurrent;

import java.util.Collection;

public class ConcurrentLinkedDeque<E> extends LinkedBlockingDeque<E> {
    private static final long serialVersionUID = 876323262645176354L;

    public ConcurrentLinkedDeque() {
        super();
    }

    public ConcurrentLinkedDeque(Collection<? extends E> c) {
        super(c);
    }
}
