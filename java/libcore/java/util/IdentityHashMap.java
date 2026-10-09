package java.util;

public class IdentityHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, java.io.Serializable, Cloneable {
    private static final class Key {
        final Object obj;

        Key(Object obj) {
            this.obj = obj;
        }

        public int hashCode() {
            return System.identityHashCode(obj);
        }

        public boolean equals(Object o) {
            return o instanceof Key && ((Key) o).obj == obj;
        }
    }

    private final HashMap<Key, Map.Entry<K, V>> map = new HashMap<Key, Map.Entry<K, V>>();

    public IdentityHashMap() {
    }

    public IdentityHashMap(int expectedMaxSize) {
    }

    public IdentityHashMap(Map<? extends K, ? extends V> m) {
        putAll(m);
    }

    public int size() {
        return map.size();
    }

    public V get(Object key) {
        Map.Entry<K, V> e = map.get(new Key(key));
        return e == null ? null : e.getValue();
    }

    public boolean containsKey(Object key) {
        return map.containsKey(new Key(key));
    }

    public V put(K key, V value) {
        Key k = new Key(key);
        Map.Entry<K, V> e = map.get(k);
        if (e != null) {
            return e.setValue(value);
        }
        map.put(k, new AbstractMap.SimpleEntry<K, V>(key, value));
        return null;
    }

    public V remove(Object key) {
        Map.Entry<K, V> e = map.remove(new Key(key));
        return e == null ? null : e.getValue();
    }

    public void clear() {
        map.clear();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {
            public Iterator<Map.Entry<K, V>> iterator() {
                return map.values().iterator();
            }

            public int size() {
                return map.size();
            }
        };
    }

    public Object clone() {
        IdentityHashMap<K, V> m = new IdentityHashMap<K, V>();
        m.putAll(this);
        return m;
    }
}
