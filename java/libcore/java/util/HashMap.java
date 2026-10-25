package java.util;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 362498820763181265L;
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int MAXIMUM_CAPACITY = 1 << 30;
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
        putMapEntries(m, false);
    }

    /** The smallest power of two at least cap (1 for 0), capped at MAXIMUM_CAPACITY. */
    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /** Sizes the table for m's entries first, as Java does, so capacity and order match Java's. */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            if (table == null) {
                float ft = ((float) s / loadFactor) + 1.0F;
                int t = (ft < (float) MAXIMUM_CAPACITY) ? (int) ft : MAXIMUM_CAPACITY;
                if (t > threshold) {
                    threshold = tableSizeFor(t);
                }
            } else {
                while (s > threshold && table.length < MAXIMUM_CAPACITY) {
                    resize();
                }
            }
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                putVal(hash(key), key, e.getValue(), false, evict);
            }
        }
    }

    final int capacity() {
        return (table != null) ? table.length : (threshold > 0) ? threshold : DEFAULT_INITIAL_CAPACITY;
    }

    final float loadFactor() {
        return loadFactor;
    }

    /** Java's serial form: the bucket count, the size, then each key and value in iteration order. */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        int buckets = capacity();
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        for (Iterator<Node<K, V>> it = nodeIterator(); it.hasNext();) {
            Node<K, V> e = it.next();
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        reinitialize();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new java.io.InvalidObjectException("Illegal load factor: " + loadFactor);
        }
        s.readInt();
        int mappings = s.readInt();
        if (mappings < 0) {
            throw new java.io.InvalidObjectException("Illegal mappings count: " + mappings);
        } else if (mappings > 0) {
            float lf = Math.min(Math.max(0.25f, loadFactor), 4.0f);
            float fc = (float) mappings / lf + 1.0f;
            int cap = (fc < DEFAULT_INITIAL_CAPACITY) ? DEFAULT_INITIAL_CAPACITY
                    : (fc >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : tableSizeFor((int) fc);
            float ft = (float) cap * lf;
            threshold = (cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ? (int) ft : Integer.MAX_VALUE;
            table = (Node<K, V>[]) new Node[cap];
            for (int i = 0; i < mappings; i++) {
                K key = (K) s.readObject();
                V value = (V) s.readObject();
                putVal(hash(key), key, value, false, false);
            }
        }
    }

    /** Back to the freshly constructed state (for clone and readObject). */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
        clearHook();
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
        return putVal(hash(key), key, value, false, true);
    }

    /** Java's resize: doubles the table, keeping each bucket's order as it splits into a low and a high half. */
    @SuppressWarnings("unchecked")
    final Node<K, V>[] resize() {
        Node<K, V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap;
        int newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            } else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY) {
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            newCap = oldThr;
        } else {
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int) (0.75f * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY) ? (int) ft : Integer.MAX_VALUE;
        }
        threshold = newThr;
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        table = newTab;
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K, V> e = oldTab[j];
                if (e == null) {
                    continue;
                }
                oldTab[j] = null;
                Node<K, V> loHead = null;
                Node<K, V> loTail = null;
                Node<K, V> hiHead = null;
                Node<K, V> hiTail = null;
                do {
                    Node<K, V> next = e.next;
                    if ((e.hash & oldCap) == 0) {
                        if (loTail == null) {
                            loHead = e;
                        } else {
                            loTail.next = e;
                        }
                        loTail = e;
                    } else {
                        if (hiTail == null) {
                            hiHead = e;
                        } else {
                            hiTail.next = e;
                        }
                        hiTail = e;
                    }
                    e = next;
                } while (e != null);
                if (loTail != null) {
                    loTail.next = null;
                    newTab[j] = loHead;
                }
                if (hiTail != null) {
                    hiTail.next = null;
                    newTab[j + oldCap] = hiHead;
                }
            }
        }
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

    /** Adds at the end of the bucket, as Java does, so iteration order matches Java's. */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        Node<K, V>[] tab = table;
        if (tab == null || tab.length == 0) {
            tab = resize();
        }
        int i = hash & (tab.length - 1);
        Node<K, V> last = null;
        for (Node<K, V> e = tab[i]; e != null; e = e.next) {
            if (e.hash == hash && (e.key == key || (key != null && key.equals(e.key)))) {
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null) {
                    e.value = value;
                }
                afterNodeAccess(e);
                return oldValue;
            }
            last = e;
        }
        Node<K, V> n = newNode(hash, key, value, null);
        if (last == null) {
            tab[i] = n;
        } else {
            last.next = n;
        }
        ++modCount;
        if (++size > threshold) {
            resize();
        }
        afterNodeInsertion(evict);
        return null;
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
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

    /* Table-order iteration state. The node, key, value and entry iterators each step inline in next() so a
     * loop over a plain HashMap costs one call per hasNext() and next(); LinkedHashMap overrides nodeIterator()
     * and the keyIterator()/valueIterator()/entryIterator() factories below. */
    abstract class HashIter {
        Node<K, V> next;
        Node<K, V> current;
        int expectedModCount;
        int index;

        HashIter() {
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

    final class HashIterator extends HashIter implements Iterator<Node<K, V>> {
        public Node<K, V> next() {
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
    }

    final class KeyIterator extends HashIter implements Iterator<K> {
        public K next() {
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
            return e.key;
        }
    }

    final class ValueIterator extends HashIter implements Iterator<V> {
        public V next() {
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
            return e.value;
        }
    }

    final class EntryIterator extends HashIter implements Iterator<Map.Entry<K, V>> {
        public Map.Entry<K, V> next() {
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
    }

    Iterator<K> keyIterator() {
        return new KeyIterator();
    }

    Iterator<V> valueIterator() {
        return new ValueIterator();
    }

    Iterator<Map.Entry<K, V>> entryIterator() {
        return new EntryIterator();
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
                    return keyIterator();
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
                    return valueIterator();
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
                    return entryIterator();
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
        return putVal(hash(key), key, value, true, true);
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
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }
}
