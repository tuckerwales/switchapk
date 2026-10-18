package java.util.concurrent;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A thread-safe sorted map with the JDK's ConcurrentSkipListMap contract, built on one lock around a TreeMap
 * rather than a lock-free skip list. Every operation is atomic; iterators are weakly consistent (they walk a
 * snapshot taken when created, never throw ConcurrentModificationException, and remove through the map);
 * null keys and values are refused. Sub-maps and descending maps are live views sharing the lock.
 */
public class ConcurrentSkipListMap<K, V> extends AbstractMap<K, V>
        implements ConcurrentNavigableMap<K, V>, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = -8627078645895051609L;

    /** The JDK's only serial field. */
    private final Comparator<? super K> comparator;
    /** The whole map; also the lock. */
    private transient TreeMap<K, V> base;
    /** The part this instance shows: base itself, or a sub-map or descending view of it. */
    private transient NavigableMap<K, V> view;

    public ConcurrentSkipListMap() {
        this((Comparator<? super K>) null);
    }

    public ConcurrentSkipListMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
        base = new TreeMap<K, V>(comparator);
        view = base;
    }

    public ConcurrentSkipListMap(Map<? extends K, ? extends V> m) {
        this((Comparator<? super K>) null);
        putAll(m);
    }

    public ConcurrentSkipListMap(SortedMap<K, ? extends V> m) {
        this(m.comparator());
        putAll(m);
    }

    private ConcurrentSkipListMap(TreeMap<K, V> base, NavigableMap<K, V> view) {
        this.comparator = base.comparator();
        this.base = base;
        this.view = view;
    }

    private ConcurrentSkipListMap<K, V> wrap(NavigableMap<K, V> v) {
        return new ConcurrentSkipListMap<K, V>(base, v);
    }

    private static void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
    }

    private static void checkValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

    private static <K, V> Map.Entry<K, V> snapshot(Map.Entry<K, V> e) {
        return e == null ? null : new AbstractMap.SimpleImmutableEntry<K, V>(e);
    }

    /** The view's entries in order, as immutable snapshots. */
    private ArrayList<Map.Entry<K, V>> entries() {
        synchronized (base) {
            ArrayList<Map.Entry<K, V>> out = new ArrayList<Map.Entry<K, V>>(view.size());
            for (Map.Entry<K, V> e : view.entrySet()) {
                out.add(new AbstractMap.SimpleImmutableEntry<K, V>(e));
            }
            return out;
        }
    }

    // ---- Map

    public boolean containsKey(Object key) {
        checkKey(key);
        synchronized (base) {
            return view.containsKey(key);
        }
    }

    public V get(Object key) {
        checkKey(key);
        synchronized (base) {
            return view.get(key);
        }
    }

    public V getOrDefault(Object key, V defaultValue) {
        V v = get(key);
        return v == null ? defaultValue : v;
    }

    public V put(K key, V value) {
        checkKey(key);
        checkValue(value);
        synchronized (base) {
            return view.put(key, value);
        }
    }

    public V remove(Object key) {
        checkKey(key);
        synchronized (base) {
            return view.remove(key);
        }
    }

    public boolean containsValue(Object value) {
        checkValue(value);
        synchronized (base) {
            return view.containsValue(value);
        }
    }

    public int size() {
        synchronized (base) {
            return view.size();
        }
    }

    public boolean isEmpty() {
        synchronized (base) {
            return view.isEmpty();
        }
    }

    public void clear() {
        synchronized (base) {
            view.clear();
        }
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        checkKey(key);
        Objects.requireNonNull(mappingFunction);
        synchronized (base) {
            V v = view.get(key);
            if (v != null) {
                return v;
            }
            v = mappingFunction.apply(key);
            if (v != null) {
                view.put(key, v);
            }
            return v;
        }
    }

    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        checkKey(key);
        Objects.requireNonNull(remappingFunction);
        synchronized (base) {
            V old = view.get(key);
            if (old == null) {
                return null;
            }
            V v = remappingFunction.apply(key, old);
            if (v == null) {
                view.remove(key);
            } else {
                view.put(key, v);
            }
            return v;
        }
    }

    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        checkKey(key);
        Objects.requireNonNull(remappingFunction);
        synchronized (base) {
            V v = remappingFunction.apply(key, view.get(key));
            if (v == null) {
                view.remove(key);
            } else {
                view.put(key, v);
            }
            return v;
        }
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        checkKey(key);
        checkValue(value);
        Objects.requireNonNull(remappingFunction);
        synchronized (base) {
            V old = view.get(key);
            V v = old == null ? value : remappingFunction.apply(old, value);
            if (v == null) {
                view.remove(key);
            } else {
                view.put(key, v);
            }
            return v;
        }
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        for (Map.Entry<K, V> e : entries()) {
            action.accept(e.getKey(), e.getValue());
        }
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        synchronized (base) {
            for (Map.Entry<K, V> e : view.entrySet()) {
                V v = function.apply(e.getKey(), e.getValue());
                checkValue(v);
                e.setValue(v);
            }
        }
    }

    // ---- ConcurrentMap

    public V putIfAbsent(K key, V value) {
        checkKey(key);
        checkValue(value);
        synchronized (base) {
            V old = view.get(key);
            if (old == null) {
                view.put(key, value);
            }
            return old;
        }
    }

    public boolean remove(Object key, Object value) {
        checkKey(key);
        if (value == null) {
            return false;
        }
        synchronized (base) {
            V old = view.get(key);
            if (old != null && old.equals(value)) {
                view.remove(key);
                return true;
            }
            return false;
        }
    }

    public boolean replace(K key, V oldValue, V newValue) {
        checkKey(key);
        checkValue(oldValue);
        checkValue(newValue);
        synchronized (base) {
            V old = view.get(key);
            if (old != null && old.equals(oldValue)) {
                view.put(key, newValue);
                return true;
            }
            return false;
        }
    }

    public V replace(K key, V value) {
        checkKey(key);
        checkValue(value);
        synchronized (base) {
            V old = view.get(key);
            if (old != null) {
                view.put(key, value);
            }
            return old;
        }
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Map)) {
            return false;
        }
        Map<?, ?> m = (Map<?, ?>) o;
        ArrayList<Map.Entry<K, V>> mine = entries();
        try {
            for (Map.Entry<K, V> e : mine) {
                if (!e.getValue().equals(m.get(e.getKey()))) {
                    return false;
                }
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object k = e.getKey(), v = e.getValue();
                if (k == null || v == null || !v.equals(get(k))) {
                    return false;
                }
            }
            return true;
        } catch (ClassCastException | NullPointerException unused) {
            return false;
        }
    }

    // ---- SortedMap / NavigableMap

    public Comparator<? super K> comparator() {
        synchronized (base) {
            return view.comparator();
        }
    }

    public K firstKey() {
        synchronized (base) {
            return view.firstKey();
        }
    }

    public K lastKey() {
        synchronized (base) {
            return view.lastKey();
        }
    }

    public V putFirst(K key, V value) {
        throw new UnsupportedOperationException();
    }

    public V putLast(K key, V value) {
        throw new UnsupportedOperationException();
    }

    public Map.Entry<K, V> lowerEntry(K key) {
        checkKey(key);
        synchronized (base) {
            return snapshot(view.lowerEntry(key));
        }
    }

    public K lowerKey(K key) {
        checkKey(key);
        synchronized (base) {
            return view.lowerKey(key);
        }
    }

    public Map.Entry<K, V> floorEntry(K key) {
        checkKey(key);
        synchronized (base) {
            return snapshot(view.floorEntry(key));
        }
    }

    public K floorKey(K key) {
        checkKey(key);
        synchronized (base) {
            return view.floorKey(key);
        }
    }

    public Map.Entry<K, V> ceilingEntry(K key) {
        checkKey(key);
        synchronized (base) {
            return snapshot(view.ceilingEntry(key));
        }
    }

    public K ceilingKey(K key) {
        checkKey(key);
        synchronized (base) {
            return view.ceilingKey(key);
        }
    }

    public Map.Entry<K, V> higherEntry(K key) {
        checkKey(key);
        synchronized (base) {
            return snapshot(view.higherEntry(key));
        }
    }

    public K higherKey(K key) {
        checkKey(key);
        synchronized (base) {
            return view.higherKey(key);
        }
    }

    public Map.Entry<K, V> firstEntry() {
        synchronized (base) {
            return snapshot(view.firstEntry());
        }
    }

    public Map.Entry<K, V> lastEntry() {
        synchronized (base) {
            return snapshot(view.lastEntry());
        }
    }

    public Map.Entry<K, V> pollFirstEntry() {
        synchronized (base) {
            return snapshot(view.pollFirstEntry());
        }
    }

    public Map.Entry<K, V> pollLastEntry() {
        synchronized (base) {
            return snapshot(view.pollLastEntry());
        }
    }

    public ConcurrentNavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        checkKey(fromKey);
        checkKey(toKey);
        synchronized (base) {
            return wrap(view.subMap(fromKey, fromInclusive, toKey, toInclusive));
        }
    }

    public ConcurrentNavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        checkKey(toKey);
        synchronized (base) {
            return wrap(view.headMap(toKey, inclusive));
        }
    }

    public ConcurrentNavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        checkKey(fromKey);
        synchronized (base) {
            return wrap(view.tailMap(fromKey, inclusive));
        }
    }

    public ConcurrentNavigableMap<K, V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    public ConcurrentNavigableMap<K, V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    public ConcurrentNavigableMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    public ConcurrentNavigableMap<K, V> descendingMap() {
        synchronized (base) {
            return wrap(view.descendingMap());
        }
    }

    public NavigableSet<K> keySet() {
        return new KeySet<K>(this);
    }

    public NavigableSet<K> navigableKeySet() {
        return new KeySet<K>(this);
    }

    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    // ---- views

    /** Weakly consistent: walks a snapshot; remove() removes the last key from the map. */
    private final class SnapshotIterator<T> implements Iterator<T> {
        private final Iterator<Map.Entry<K, V>> it = entries().iterator();
        private final int kind; // 0 entries, 1 keys, 2 values
        private Map.Entry<K, V> last;

        SnapshotIterator(int kind) {
            this.kind = kind;
        }

        public boolean hasNext() {
            return it.hasNext();
        }

        @SuppressWarnings("unchecked")
        public T next() {
            last = it.next();
            return (T) (kind == 0 ? last : kind == 1 ? last.getKey() : last.getValue());
        }

        public void remove() {
            if (last == null) {
                throw new IllegalStateException();
            }
            ConcurrentSkipListMap.this.remove(last.getKey());
            last = null;
        }
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new SnapshotIterator<Map.Entry<K, V>>(0);
            }

            public int size() {
                return ConcurrentSkipListMap.this.size();
            }

            public boolean isEmpty() {
                return ConcurrentSkipListMap.this.isEmpty();
            }

            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry)) {
                    return false;
                }
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                if (e.getKey() == null) {
                    return false;
                }
                V v = ConcurrentSkipListMap.this.get(e.getKey());
                return v != null && v.equals(e.getValue());
            }

            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry)) {
                    return false;
                }
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                return e.getKey() != null && ConcurrentSkipListMap.this.remove(e.getKey(), e.getValue());
            }

            public void clear() {
                ConcurrentSkipListMap.this.clear();
            }
        };
    }

    public Collection<V> values() {
        return new AbstractCollection<V>() {
            public Iterator<V> iterator() {
                return new SnapshotIterator<V>(2);
            }

            public int size() {
                return ConcurrentSkipListMap.this.size();
            }

            public boolean isEmpty() {
                return ConcurrentSkipListMap.this.isEmpty();
            }

            public boolean contains(Object o) {
                return o != null && containsValue(o);
            }

            public void clear() {
                ConcurrentSkipListMap.this.clear();
            }
        };
    }

    Iterator<K> keyIterator() {
        return new SnapshotIterator<K>(1);
    }

    static final class KeySet<E> extends AbstractSet<E> implements NavigableSet<E> {
        final ConcurrentNavigableMap<E, ?> m;

        KeySet(ConcurrentNavigableMap<E, ?> map) {
            m = map;
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public boolean contains(Object o) {
            return m.containsKey(o);
        }

        public boolean remove(Object o) {
            return m.remove(o) != null;
        }

        public void clear() {
            m.clear();
        }

        public E lower(E e) {
            return m.lowerKey(e);
        }

        public E floor(E e) {
            return m.floorKey(e);
        }

        public E ceiling(E e) {
            return m.ceilingKey(e);
        }

        public E higher(E e) {
            return m.higherKey(e);
        }

        public Comparator<? super E> comparator() {
            return m.comparator();
        }

        public E first() {
            return m.firstKey();
        }

        public E last() {
            return m.lastKey();
        }

        public E pollFirst() {
            Map.Entry<E, ?> e = m.pollFirstEntry();
            return e == null ? null : e.getKey();
        }

        public E pollLast() {
            Map.Entry<E, ?> e = m.pollLastEntry();
            return e == null ? null : e.getKey();
        }

        @SuppressWarnings("unchecked")
        public Iterator<E> iterator() {
            return ((ConcurrentSkipListMap<E, ?>) m).keyIterator();
        }

        public Iterator<E> descendingIterator() {
            return descendingSet().iterator();
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Set)) {
                return false;
            }
            Collection<?> c = (Collection<?>) o;
            try {
                return containsAll(c) && c.containsAll(this);
            } catch (ClassCastException | NullPointerException unused) {
                return false;
            }
        }

        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
            return new KeySet<E>(m.subMap(fromElement, fromInclusive, toElement, toInclusive));
        }

        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return new KeySet<E>(m.headMap(toElement, inclusive));
        }

        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return new KeySet<E>(m.tailMap(fromElement, inclusive));
        }

        public NavigableSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }

        public NavigableSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }

        public NavigableSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }

        public NavigableSet<E> descendingSet() {
            return new KeySet<E>(m.descendingMap());
        }
    }

    // ---- copying and serialization

    @SuppressWarnings("unchecked")
    public ConcurrentSkipListMap<K, V> clone() {
        ConcurrentSkipListMap<K, V> c = new ConcurrentSkipListMap<K, V>(comparator);
        synchronized (base) {
            c.base.putAll(view);
        }
        return c;
    }

    /** Java's serial form: the comparator, then each key and value in order, then a null. */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        for (Map.Entry<K, V> e : entries()) {
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
        s.writeObject(null);
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        base = new TreeMap<K, V>(comparator);
        view = base;
        for (;;) {
            Object k = s.readObject();
            if (k == null) {
                break;
            }
            Object v = s.readObject();
            if (v == null) {
                throw new NullPointerException();
            }
            base.put((K) k, (V) v);
        }
    }
}
