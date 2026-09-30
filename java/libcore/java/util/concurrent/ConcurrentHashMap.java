package java.util.concurrent;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ConcurrentHashMap<K, V> extends HashMap<K, V> implements ConcurrentMap<K, V> {
    public ConcurrentHashMap() {
        super();
    }

    public ConcurrentHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        super(initialCapacity, loadFactor);
    }

    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        super(m);
    }

    public synchronized V get(Object key) {
        return super.get(key);
    }

    public synchronized V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return super.put(key, value);
    }

    public synchronized V putIfAbsent(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return super.putIfAbsent(key, value);
    }

    public synchronized V remove(Object key) {
        return super.remove(key);
    }

    public synchronized boolean remove(Object key, Object value) {
        return super.remove(key, value);
    }

    public synchronized boolean replace(K key, V oldValue, V newValue) {
        return super.replace(key, oldValue, newValue);
    }

    public synchronized V replace(K key, V value) {
        return super.replace(key, value);
    }

    public synchronized boolean containsKey(Object key) {
        return super.containsKey(key);
    }

    public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return super.computeIfAbsent(key, mappingFunction);
    }

    public synchronized V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return super.computeIfPresent(key, remappingFunction);
    }

    public synchronized V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return super.compute(key, remappingFunction);
    }

    public synchronized V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return super.merge(key, value, remappingFunction);
    }

    public synchronized void clear() {
        super.clear();
    }

    public synchronized int size() {
        return super.size();
    }

    public boolean contains(Object value) {
        return containsValue(value);
    }

    public Enumeration<K> keys() {
        return Collections.enumeration(new java.util.ArrayList<K>(keySet()));
    }

    public Enumeration<V> elements() {
        return Collections.enumeration(new java.util.ArrayList<V>(values()));
    }

    public long mappingCount() {
        return size();
    }

    public static <K> Set<K> newKeySet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<K, Boolean>());
    }

    public static <K> Set<K> newKeySet(int initialCapacity) {
        return newKeySet();
    }

    public Set<K> keySet(V mappedValue) {
        return keySet();
    }
}
