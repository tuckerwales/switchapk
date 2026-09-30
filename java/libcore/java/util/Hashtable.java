package java.util;

public class Hashtable<K, V> extends Dictionary<K, V> implements Map<K, V>, Cloneable, java.io.Serializable {
    private HashMap<K, V> map;

    public Hashtable(int initialCapacity, float loadFactor) {
        map = new HashMap<K, V>(initialCapacity, loadFactor);
    }

    public Hashtable(int initialCapacity) {
        map = new HashMap<K, V>(initialCapacity);
    }

    public Hashtable() {
        map = new HashMap<K, V>();
    }

    public Hashtable(Map<? extends K, ? extends V> t) {
        map = new HashMap<K, V>();
        putAll(t);
    }

    public synchronized int size() {
        return map.size();
    }

    public synchronized boolean isEmpty() {
        return map.isEmpty();
    }

    public synchronized Enumeration<K> keys() {
        return Collections.enumeration(new ArrayList<K>(map.keySet()));
    }

    public synchronized Enumeration<V> elements() {
        return Collections.enumeration(new ArrayList<V>(map.values()));
    }

    public synchronized boolean contains(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        return map.containsValue(value);
    }

    public boolean containsValue(Object value) {
        return contains(value);
    }

    public synchronized boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public synchronized V get(Object key) {
        return map.get(key);
    }

    public synchronized V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return map.put(key, value);
    }

    public synchronized V remove(Object key) {
        return map.remove(key);
    }

    public synchronized void putAll(Map<? extends K, ? extends V> t) {
        for (Map.Entry<? extends K, ? extends V> e : t.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    public synchronized void clear() {
        map.clear();
    }

    @SuppressWarnings("unchecked")
    public synchronized Object clone() {
        try {
            Hashtable<K, V> t = (Hashtable<K, V>) super.clone();
            t.map = (HashMap<K, V>) map.clone();
            return t;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public synchronized String toString() {
        return map.toString();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    public Collection<V> values() {
        return map.values();
    }

    public synchronized boolean equals(Object o) {
        return map.equals(o);
    }

    public synchronized int hashCode() {
        return map.hashCode();
    }
}
