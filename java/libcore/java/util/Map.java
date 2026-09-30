package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Map<K, V> {
    int size();

    boolean isEmpty();

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    V get(Object key);

    V put(K key, V value);

    V remove(Object key);

    void putAll(Map<? extends K, ? extends V> m);

    void clear();

    Set<K> keySet();

    Collection<V> values();

    Set<Map.Entry<K, V>> entrySet();

    interface Entry<K, V> {
        K getKey();

        V getValue();

        V setValue(V value);

        boolean equals(Object o);

        int hashCode();

        static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> comparingByKey() {
            return new Comparator<Map.Entry<K, V>>() {
                public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
                    return a.getKey().compareTo(b.getKey());
                }
            };
        }

        static <K, V extends Comparable<? super V>> Comparator<Map.Entry<K, V>> comparingByValue() {
            return new Comparator<Map.Entry<K, V>>() {
                public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
                    return a.getValue().compareTo(b.getValue());
                }
            };
        }

        static <K, V> Comparator<Map.Entry<K, V>> comparingByKey(final Comparator<? super K> cmp) {
            return new Comparator<Map.Entry<K, V>>() {
                public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
                    return cmp.compare(a.getKey(), b.getKey());
                }
            };
        }

        static <K, V> Comparator<Map.Entry<K, V>> comparingByValue(final Comparator<? super V> cmp) {
            return new Comparator<Map.Entry<K, V>>() {
                public int compare(Map.Entry<K, V> a, Map.Entry<K, V> b) {
                    return cmp.compare(a.getValue(), b.getValue());
                }
            };
        }
    }

    boolean equals(Object o);

    int hashCode();

    default V getOrDefault(Object key, V defaultValue) {
        V v = get(key);
        return (v != null || containsKey(key)) ? v : defaultValue;
    }

    default void forEach(BiConsumer<? super K, ? super V> action) {
        for (Map.Entry<K, V> entry : entrySet()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        for (Map.Entry<K, V> entry : entrySet()) {
            entry.setValue(function.apply(entry.getKey(), entry.getValue()));
        }
    }

    default V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            v = put(key, value);
        }
        return v;
    }

    default boolean remove(Object key, Object value) {
        Object curValue = get(key);
        if (!Objects.equals(curValue, value) || (curValue == null && !containsKey(key))) {
            return false;
        }
        remove(key);
        return true;
    }

    default boolean replace(K key, V oldValue, V newValue) {
        Object curValue = get(key);
        if (!Objects.equals(curValue, oldValue) || (curValue == null && !containsKey(key))) {
            return false;
        }
        put(key, newValue);
        return true;
    }

    default V replace(K key, V value) {
        V curValue;
        if (((curValue = get(key)) != null) || containsKey(key)) {
            curValue = put(key, value);
        }
        return curValue;
    }

    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V v;
        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }
        return v;
    }

    default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V oldValue;
        if ((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                put(key, newValue);
                return newValue;
            } else {
                remove(key);
                return null;
            }
        }
        return null;
    }

    default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V oldValue = get(key);
        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue == null) {
            if (oldValue != null || containsKey(key)) {
                remove(key);
            }
            return null;
        }
        put(key, newValue);
        return newValue;
    }

    default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        V oldValue = get(key);
        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
        if (newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        return newValue;
    }

    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> of() {
        return Collections.emptyMap();
    }

    static <K, V> Map<K, V> of(K k1, V v1) {
        return ofPairs(k1, v1);
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        return ofPairs(k1, v1, k2, v2);
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        return ofPairs(k1, v1, k2, v2, k3, v3);
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return ofPairs(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return ofPairs(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> ofPairs(Object... kv) {
        LinkedHashMap<K, V> m = new LinkedHashMap<K, V>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((K) kv[i], (V) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    static <K, V> Map.Entry<K, V> entry(K k, V v) {
        return new AbstractMap.SimpleImmutableEntry<K, V>(k, v);
    }

    static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(map));
    }
}
