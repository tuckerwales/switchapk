package java.util.concurrent;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class ConcurrentSkipListSet<E> extends TreeSet<E> {
    private static final long serialVersionUID = -2479143111061671589L;

    public ConcurrentSkipListSet() {
        super();
    }

    public ConcurrentSkipListSet(Comparator<? super E> comparator) {
        super(comparator);
    }

    public ConcurrentSkipListSet(Collection<? extends E> c) {
        super(c);
    }
}
