package java.util;

public abstract class AbstractMap<K, V> implements Map<K, V> {
    transient Set<K> keySet;
    transient Collection<V> values;

    protected AbstractMap() {
    }

    public int size() {
        return entrySet().size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsValue(Object value) {
        for (Entry<K, V> e : entrySet()) {
            if (Objects.equals(value, e.getValue())) {
                return true;
            }
        }
        return false;
    }

    public boolean containsKey(Object key) {
        for (Entry<K, V> e : entrySet()) {
            if (Objects.equals(key, e.getKey())) {
                return true;
            }
        }
        return false;
    }

    public V get(Object key) {
        for (Entry<K, V> e : entrySet()) {
            if (Objects.equals(key, e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    public V remove(Object key) {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        while (i.hasNext()) {
            Entry<K, V> e = i.next();
            if (Objects.equals(key, e.getKey())) {
                V old = e.getValue();
                i.remove();
                return old;
            }
        }
        return null;
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    public void clear() {
        entrySet().clear();
    }

    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new AbstractSet<K>() {
                public Iterator<K> iterator() {
                    return new Iterator<K>() {
                        private final Iterator<Entry<K, V>> i = entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public K next() {
                            return i.next().getKey();
                        }

                        public void remove() {
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return AbstractMap.this.size();
                }

                public boolean contains(Object k) {
                    return AbstractMap.this.containsKey(k);
                }

                public void clear() {
                    AbstractMap.this.clear();
                }
            };
            keySet = ks;
        }
        return ks;
    }

    public Collection<V> values() {
        Collection<V> vals = values;
        if (vals == null) {
            vals = new AbstractCollection<V>() {
                public Iterator<V> iterator() {
                    return new Iterator<V>() {
                        private final Iterator<Entry<K, V>> i = entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public V next() {
                            return i.next().getValue();
                        }

                        public void remove() {
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return AbstractMap.this.size();
                }

                public boolean contains(Object v) {
                    return AbstractMap.this.containsValue(v);
                }

                public void clear() {
                    AbstractMap.this.clear();
                }
            };
            values = vals;
        }
        return vals;
    }

    public abstract Set<Entry<K, V>> entrySet();

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Map)) {
            return false;
        }
        Map<?, ?> m = (Map<?, ?>) o;
        if (m.size() != size()) {
            return false;
        }
        try {
            for (Entry<K, V> e : entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key) == null && m.containsKey(key))) {
                        return false;
                    }
                } else if (!value.equals(m.get(key))) {
                    return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        int h = 0;
        for (Entry<K, V> entry : entrySet()) {
            h += entry.hashCode();
        }
        return h;
    }

    public String toString() {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        if (!i.hasNext()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<K, V> e = i.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
            if (!i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    protected Object clone() throws CloneNotSupportedException {
        @SuppressWarnings("unchecked")
        AbstractMap<K, V> result = (AbstractMap<K, V>) super.clone();
        result.keySet = null;
        result.values = null;
        return result;
    }

    public static class SimpleEntry<K, V> implements Entry<K, V>, java.io.Serializable {
        private final K key;
        private V value;

        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public SimpleEntry(Entry<? extends K, ? extends V> entry) {
            this.key = entry.getKey();
            this.value = entry.getValue();
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
        }

        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    public static class SimpleImmutableEntry<K, V> implements Entry<K, V>, java.io.Serializable {
        private final K key;
        private final V value;

        public SimpleImmutableEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public SimpleImmutableEntry(Entry<? extends K, ? extends V> entry) {
            this.key = entry.getKey();
            this.value = entry.getValue();
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
        }

        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }

        public String toString() {
            return key + "=" + value;
        }
    }
}
