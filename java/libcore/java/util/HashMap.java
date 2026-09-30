package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, java.io.Serializable {
    static class Node<K, V> implements Map.Entry<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey() {
            return key;
        }

        public final V getValue() {
            return value;
        }

        public final String toString() {
            return key + "=" + value;
        }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
            }
            return false;
        }
    }

    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    transient Node<K, V>[] table;
    transient int size;
    transient int modCount;
    int threshold;
    final float loadFactor;
    private transient Set<Map.Entry<K, V>> entrySet;

    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
    }

    public HashMap(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    public HashMap() {
        this.loadFactor = 0.75f;
    }

    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = 0.75f;
        putAll(m);
    }

    static final int tableSizeFor(int cap) {
        int n = 1;
        while (n < cap) {
            n <<= 1;
        }
        return n;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    final Node<K, V> getNode(Object key) {
        Node<K, V>[] tab = table;
        if (tab == null) {
            return null;
        }
        int h = hash(key);
        for (Node<K, V> e = tab[h & (tab.length - 1)]; e != null; e = e.next) {
            if (e.hash == h && (e.key == key || (key != null && key.equals(e.key)))) {
                return e;
            }
        }
        return null;
    }

    public V get(Object key) {
        Node<K, V> e = getNode(key);
        if (e == null) {
            return null;
        }
        afterNodeAccess(e);
        return e.value;
    }

    public boolean containsKey(Object key) {
        return getNode(key) != null;
    }

    public V put(K key, V value) {
        return putVal(hash(key), key, value, false);
    }

    @SuppressWarnings("unchecked")
    final Node<K, V>[] resize() {
        Node<K, V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int newCap = oldCap > 0 ? oldCap << 1 : (threshold > 0 ? threshold : 16);
        if (newCap < 4) {
            newCap = 4;
        }
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        threshold = (int) (newCap * loadFactor);
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K, V> e = oldTab[j];
                while (e != null) {
                    Node<K, V> next = e.next;
                    int idx = e.hash & (newCap - 1);
                    e.next = newTab[idx];
                    newTab[idx] = e;
                    e = next;
                }
            }
        }
        table = newTab;
        return newTab;
    }

    Node<K, V> newNode(int hash, K key, V value, Node<K, V> next) {
        return new Node<K, V>(hash, key, value, next);
    }

    void afterNodeAccess(Node<K, V> p) {
    }

    void afterNodeInsertion(boolean evict) {
    }

    void afterNodeRemoval(Node<K, V> p) {
    }

    final V putVal(int hash, K key, V value, boolean onlyIfAbsent) {
        Node<K, V>[] tab = table;
        if (tab == null || tab.length == 0) {
            tab = resize();
        }
        int i = hash & (tab.length - 1);
        for (Node<K, V> e = tab[i]; e != null; e = e.next) {
            if (e.hash == hash && (e.key == key || (key != null && key.equals(e.key)))) {
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null) {
                    e.value = value;
                }
                afterNodeAccess(e);
                return oldValue;
            }
        }
        tab[i] = newNode(hash, key, value, tab[i]);
        ++modCount;
        if (++size > threshold) {
            resize();
        }
        afterNodeInsertion(true);
        return null;
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    public V remove(Object key) {
        Node<K, V> e = removeNode(hash(key), key, null, false);
        return e == null ? null : e.value;
    }

    final Node<K, V> removeNode(int hash, Object key, Object value, boolean matchValue) {
        Node<K, V>[] tab = table;
        if (tab == null) {
            return null;
        }
        int i = hash & (tab.length - 1);
        Node<K, V> prev = null;
        for (Node<K, V> e = tab[i]; e != null; prev = e, e = e.next) {
            if (e.hash == hash && (e.key == key || (key != null && key.equals(e.key)))) {
                if (matchValue && !Objects.equals(value, e.value)) {
                    return null;
                }
                if (prev == null) {
                    tab[i] = e.next;
                } else {
                    prev.next = e.next;
                }
                ++modCount;
                --size;
                afterNodeRemoval(e);
                return e;
            }
        }
        return null;
    }

    public void clear() {
        modCount++;
        if (table != null && size > 0) {
            size = 0;
            for (int i = 0; i < table.length; ++i) {
                table[i] = null;
            }
        }
        clearHook();
    }

    void clearHook() {
    }

    public boolean containsValue(Object value) {
        Node<K, V>[] tab = table;
        if (tab != null) {
            for (Node<K, V> e : tab) {
                for (; e != null; e = e.next) {
                    if (Objects.equals(value, e.value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Iterates nodes in map order. Overridden by LinkedHashMap. */
    Iterator<Node<K, V>> nodeIterator() {
        return new HashIterator();
    }

    class HashIterator implements Iterator<Node<K, V>> {
        Node<K, V> next;
        Node<K, V> current;
        int expectedModCount;
        int index;

        HashIterator() {
            expectedModCount = modCount;
            Node<K, V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) {
                do {
                } while (index < t.length && (next = t[index++]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        public final Node<K, V> next() {
            Node<K, V>[] t;
            Node<K, V> e = next;
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            if (e == null) {
                throw new NoSuchElementException();
            }
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {
                } while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K, V> p = current;
            if (p == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            current = null;
            removeNode(p.hash, p.key, null, false);
            expectedModCount = modCount;
        }
    }

    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new AbstractSet<K>() {
                public int size() {
                    return size;
                }

                public void clear() {
                    HashMap.this.clear();
                }

                public Iterator<K> iterator() {
                    final Iterator<Node<K, V>> it = nodeIterator();
                    return new Iterator<K>() {
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        public K next() {
                            return it.next().key;
                        }

                        public void remove() {
                            it.remove();
                        }
                    };
                }

                public boolean contains(Object o) {
                    return containsKey(o);
                }

                public boolean remove(Object key) {
                    return removeNode(hash(key), key, null, false) != null;
                }
            };
            keySet = ks;
        }
        return ks;
    }

    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new AbstractCollection<V>() {
                public int size() {
                    return size;
                }

                public void clear() {
                    HashMap.this.clear();
                }

                public Iterator<V> iterator() {
                    final Iterator<Node<K, V>> it = nodeIterator();
                    return new Iterator<V>() {
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        public V next() {
                            return it.next().value;
                        }

                        public void remove() {
                            it.remove();
                        }
                    };
                }

                public boolean contains(Object o) {
                    return containsValue(o);
                }
            };
            values = vs;
        }
        return vs;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> es = entrySet;
        if (es == null) {
            es = new AbstractSet<Map.Entry<K, V>>() {
                public int size() {
                    return size;
                }

                public void clear() {
                    HashMap.this.clear();
                }

                public Iterator<Map.Entry<K, V>> iterator() {
                    final Iterator<Node<K, V>> it = nodeIterator();
                    return new Iterator<Map.Entry<K, V>>() {
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        public Map.Entry<K, V> next() {
                            return it.next();
                        }

                        public void remove() {
                            it.remove();
                        }
                    };
                }

                public boolean contains(Object o) {
                    if (!(o instanceof Map.Entry)) {
                        return false;
                    }
                    Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                    Node<K, V> candidate = getNode(e.getKey());
                    return candidate != null && candidate.equals(e);
                }

                public boolean remove(Object o) {
                    if (o instanceof Map.Entry) {
                        Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                        return removeNode(hash(e.getKey()), e.getKey(), e.getValue(), true) != null;
                    }
                    return false;
                }
            };
            entrySet = es;
        }
        return es;
    }

    public V getOrDefault(Object key, V defaultValue) {
        Node<K, V> e = getNode(key);
        return e == null ? defaultValue : e.value;
    }

    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true);
    }

    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true) != null;
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Node<K, V> e = getNode(key);
        if (e != null && e.value != null) {
            afterNodeAccess(e);
            return e.value;
        }
        V v = mappingFunction.apply(key);
        if (v != null) {
            put(key, v);
        }
        return v;
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        int mc = modCount;
        Iterator<Node<K, V>> it = nodeIterator();
        while (it.hasNext()) {
            Node<K, V> e = it.next();
            action.accept(e.key, e.value);
        }
        if (modCount != mc) {
            throw new ConcurrentModificationException();
        }
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Iterator<Node<K, V>> it = nodeIterator();
        while (it.hasNext()) {
            Node<K, V> e = it.next();
            e.value = function.apply(e.key, e.value);
        }
    }

    @SuppressWarnings("unchecked")
    public Object clone() {
        HashMap<K, V> result;
        try {
            result = (HashMap<K, V>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
        result.table = null;
        result.entrySet = null;
        result.modCount = 0;
        result.size = 0;
        result.threshold = 0;
        result.keySet = null;
        result.values = null;
        result.clearHook();
        result.putAll(this);
        return result;
    }
}
