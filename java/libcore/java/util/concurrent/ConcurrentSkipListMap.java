package java.util.concurrent;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class ConcurrentSkipListMap<K, V> extends TreeMap<K, V> implements ConcurrentMap<K, V> {
    private static final long serialVersionUID = -8627078645895051609L;

    public ConcurrentSkipListMap() {
        super();
    }

    public ConcurrentSkipListMap(Comparator<? super K> comparator) {
        super(comparator);
    }

    public ConcurrentSkipListMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
