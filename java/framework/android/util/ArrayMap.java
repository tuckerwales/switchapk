package android.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** ArrayMap with the Android API, backed by a LinkedHashMap plus index access. */
public final class ArrayMap<K, V> implements Map<K, V> {
    private final LinkedHashMap<K, V> mMap;
    private Object[] mKeyCache;

    public ArrayMap() { mMap = new LinkedHashMap<>(); }
    public ArrayMap(int capacity) { mMap = new LinkedHashMap<>(Math.max(capacity, 1)); }
    public ArrayMap(ArrayMap<K, V> map) { mMap = new LinkedHashMap<>(map.mMap); }

    private Object[] keys() {
        if (mKeyCache == null) mKeyCache = mMap.keySet().toArray();
        return mKeyCache;
    }

    private void dirty() { mKeyCache = null; }

    public void clear() { mMap.clear(); dirty(); }
    public void erase() { clear(); }
    public void ensureCapacity(int minimumCapacity) {}
    public boolean containsKey(Object key) { return mMap.containsKey(key); }
    public int indexOfKey(Object key) {
        Object[] k = keys();
        for (int i = 0; i < k.length; i++) if (key == null ? k[i] == null : key.equals(k[i])) return i;
        return -1;
    }
    public int indexOfValue(Object value) {
        Object[] k = keys();
        for (int i = 0; i < k.length; i++) {
            Object v = mMap.get(k[i]);
            if (value == null ? v == null : value.equals(v)) return i;
        }
        return -1;
    }
    public boolean containsValue(Object value) { return mMap.containsValue(value); }
    public V get(Object key) { return mMap.get(key); }
    @SuppressWarnings("unchecked")
    public K keyAt(int index) { return (K) keys()[index]; }
    public V valueAt(int index) { return mMap.get(keys()[index]); }
    public V setValueAt(int index, V value) { return mMap.put(keyAt(index), value); }
    public boolean isEmpty() { return mMap.isEmpty(); }
    public V put(K key, V value) {
        if (!mMap.containsKey(key)) dirty();
        return mMap.put(key, value);
    }
    public void append(K key, V value) { put(key, value); }
    public void putAll(ArrayMap<? extends K, ? extends V> array) { mMap.putAll(array.mMap); dirty(); }
    public void putAll(Map<? extends K, ? extends V> map) { mMap.putAll(map); dirty(); }
    public V remove(Object key) { dirty(); return mMap.remove(key); }
    public V removeAt(int index) { return remove(keys()[index]); }
    public boolean removeAll(Collection<?> collection) {
        boolean changed = false;
        for (Object o : collection) if (mMap.containsKey(o)) { remove(o); changed = true; }
        return changed;
    }
    public boolean retainAll(Collection<?> collection) {
        dirty();
        return mMap.keySet().retainAll(collection);
    }
    public boolean containsAll(Collection<?> collection) { return mMap.keySet().containsAll(collection); }
    public int size() { return mMap.size(); }
    public Set<Map.Entry<K, V>> entrySet() { dirty(); return mMap.entrySet(); }
    public Set<K> keySet() { dirty(); return mMap.keySet(); }
    public Collection<V> values() { return mMap.values(); }
    public boolean equals(Object o) { return o instanceof Map && mMap.equals(o); }
    public int hashCode() { return mMap.hashCode(); }
    public String toString() { return mMap.toString(); }
}
