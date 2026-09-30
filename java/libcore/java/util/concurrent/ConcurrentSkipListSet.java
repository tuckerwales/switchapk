package java.util.concurrent;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class ConcurrentSkipListSet<E> extends TreeSet<E> {
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
