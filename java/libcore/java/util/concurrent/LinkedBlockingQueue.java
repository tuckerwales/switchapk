package java.util.concurrent;

import java.util.Collection;

public class LinkedBlockingQueue<E> extends LinkedBlockingDeque<E> {
    private static final long serialVersionUID = -6903933977591709194L;

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
