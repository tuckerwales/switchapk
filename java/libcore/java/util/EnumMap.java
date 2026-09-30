package java.util;

@SuppressWarnings("rawtypes")
public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V> implements java.io.Serializable, Cloneable {
    private final Class<K> keyType;
    private K[] keyUniverse;
    private Object[] vals;
    private int size = 0;
    private static final Object NULL = new Object();

    public EnumMap(Class<K> keyType) {
        this.keyType = keyType;
        keyUniverse = keyType.getEnumConstants();
        vals = new Object[keyUniverse.length];
    }

    public EnumMap(EnumMap<K, ? extends V> m) {
        keyType = m.keyType;
        keyUniverse = m.keyUniverse;
        vals = m.vals.clone();
        size = m.size;
    }

    @SuppressWarnings("unchecked")
    public EnumMap(Map<K, ? extends V> m) {
        if (m instanceof EnumMap) {
            EnumMap<K, ? extends V> em = (EnumMap<K, ? extends V>) m;
            keyType = em.keyType;
            keyUniverse = em.keyUniverse;
            vals = em.vals.clone();
            size = em.size;
        } else {
            if (m.isEmpty()) {
                throw new IllegalArgumentException("Specified map is empty");
            }
            keyType = (Class<K>) m.keySet().iterator().next().getDeclaringClass();
            keyUniverse = keyType.getEnumConstants();
            vals = new Object[keyUniverse.length];
            putAll(m);
        }
    }

    private boolean isValidKey(Object key) {
        if (key == null) {
            return false;
        }
        Class<?> keyClass = key.getClass();
        return keyClass == keyType || keyClass.getSuperclass() == keyType;
    }

    public int size() {
        return size;
    }

    public boolean containsValue(Object value) {
        Object v = value == null ? NULL : value;
        for (Object val : vals) {
            if (v.equals(val)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum<?>) key).ordinal()] != null;
    }

    @SuppressWarnings("unchecked")
    private V unmask(Object v) {
        return v == NULL ? null : (V) v;
    }

    public V get(Object key) {
        return isValidKey(key) ? unmask(vals[((Enum<?>) key).ordinal()]) : null;
    }

    public V put(K key, V value) {
        if (!isValidKey(key)) {
            throw new ClassCastException(String.valueOf(key));
        }
        int index = key.ordinal();
        Object oldValue = vals[index];
        vals[index] = value == null ? NULL : value;
        if (oldValue == null) {
            size++;
        }
        return unmask(oldValue);
    }

    public V remove(Object key) {
        if (!isValidKey(key)) {
            return null;
        }
        int index = ((Enum<?>) key).ordinal();
        Object oldValue = vals[index];
        vals[index] = null;
        if (oldValue != null) {
            size--;
        }
        return unmask(oldValue);
    }

    public void clear() {
        Arrays.fill(vals, null);
        size = 0;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new Iterator<Map.Entry<K, V>>() {
                    int index = advance(0);
                    int last = -1;

                    int advance(int i) {
                        while (i < vals.length && vals[i] == null) {
                            i++;
                        }
                        return i;
                    }

                    public boolean hasNext() {
                        return index < vals.length;
                    }

                    public Map.Entry<K, V> next() {
                        if (index >= vals.length) {
                            throw new NoSuchElementException();
                        }
                        last = index;
                        final int i = index;
                        index = advance(index + 1);
                        return new Map.Entry<K, V>() {
                            public K getKey() {
                                return keyUniverse[i];
                            }

                            public V getValue() {
                                return unmask(vals[i]);
                            }

                            public V setValue(V value) {
                                V old = unmask(vals[i]);
                                vals[i] = value == null ? NULL : value;
                                return old;
                            }

                            public int hashCode() {
                                return keyUniverse[i].hashCode() ^ Objects.hashCode(getValue());
                            }

                            public boolean equals(Object o) {
                                if (!(o instanceof Map.Entry)) {
                                    return false;
                                }
                                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                                return keyUniverse[i] == e.getKey() && Objects.equals(getValue(), e.getValue());
                            }

                            public String toString() {
                                return keyUniverse[i] + "=" + getValue();
                            }
                        };
                    }

                    public void remove() {
                        if (last < 0) {
                            throw new IllegalStateException();
                        }
                        if (vals[last] != null) {
                            vals[last] = null;
                            size--;
                        }
                        last = -1;
                    }
                };
            }

            public int size() {
                return size;
            }
        };
    }

    public EnumMap<K, V> clone() {
        return new EnumMap<K, V>(this);
    }
}
