package java.util;

/** Note: entries are held strongly in this implementation. */
public class WeakHashMap<K, V> extends HashMap<K, V> implements Map<K, V> {
    public WeakHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public WeakHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public WeakHashMap() {
        super();
    }

    public WeakHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }
}
