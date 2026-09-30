package java.util;

/**
 * Sorted map backed by a sorted array of entries. Lookups are O(log n);
 * insertions and removals are O(n), which is fine for the map sizes typical
 * of applications. Sub-map views are snapshots.
 */
public class TreeMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Cloneable, java.io.Serializable {
    private final Comparator<? super K> comparator;
    private ArrayList<Entry<K, V>> entries = new ArrayList<Entry<K, V>>();
    private transient int modCount;

    static final class Entry<K, V> implements Map.Entry<K, V> {
        final K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V v) {
            V old = value;
            value = v;
            return old;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
        }

        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    public TreeMap() {
        comparator = null;
    }

    public TreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    public TreeMap(Map<? extends K, ? extends V> m) {
        comparator = null;
        putAll(m);
    }

    public TreeMap(SortedMap<K, ? extends V> m) {
        comparator = m.comparator();
        putAll(m);
    }

    @SuppressWarnings("unchecked")
    final int compare(Object k1, Object k2) {
        return comparator == null ? ((Comparable<Object>) k1).compareTo(k2) : comparator.compare((K) k1, (K) k2);
    }

    /** Returns index if found, else -(insertion point) - 1. */
    private int search(Object key) {
        if (key == null && comparator == null) {
            throw new NullPointerException();
        }
        int lo = 0, hi = entries.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = compare(entries.get(mid).key, key);
            if (c < 0) {
                lo = mid + 1;
            } else if (c > 0) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -(lo + 1);
    }

    public int size() {
        return entries.size();
    }

    public boolean containsKey(Object key) {
        return search(key) >= 0;
    }

    public V get(Object key) {
        int i = search(key);
        return i >= 0 ? entries.get(i).value : null;
    }

    public Comparator<? super K> comparator() {
        return comparator;
    }

    public K firstKey() {
        if (entries.isEmpty()) {
            throw new NoSuchElementException();
        }
        return entries.get(0).key;
    }

    public K lastKey() {
        if (entries.isEmpty()) {
            throw new NoSuchElementException();
        }
        return entries.get(entries.size() - 1).key;
    }

    public V put(K key, V value) {
        if (entries.isEmpty()) {
            compare(key, key); // type / null check
        }
        int i = search(key);
        if (i >= 0) {
            return entries.get(i).setValue(value);
        }
        entries.add(-(i + 1), new Entry<K, V>(key, value));
        modCount++;
        return null;
    }

    public V remove(Object key) {
        int i = search(key);
        if (i < 0) {
            return null;
        }
        modCount++;
        return entries.remove(i).value;
    }

    public void clear() {
        modCount++;
        entries.clear();
    }

    @SuppressWarnings("unchecked")
    public Object clone() {
        TreeMap<K, V> m = new TreeMap<K, V>(comparator);
        for (Entry<K, V> e : entries) {
            m.entries.add(new Entry<K, V>(e.key, e.value));
        }
        return m;
    }

    private Map.Entry<K, V> at(int i) {
        return (i >= 0 && i < entries.size()) ? entries.get(i) : null;
    }

    private static <K> K key(Map.Entry<K, ?> e) {
        return e == null ? null : e.getKey();
    }

    public Map.Entry<K, V> firstEntry() {
        return at(0);
    }

    public Map.Entry<K, V> lastEntry() {
        return at(entries.size() - 1);
    }

    public Map.Entry<K, V> pollFirstEntry() {
        if (entries.isEmpty()) {
            return null;
        }
        modCount++;
        return entries.remove(0);
    }

    public Map.Entry<K, V> pollLastEntry() {
        if (entries.isEmpty()) {
            return null;
        }
        modCount++;
        return entries.remove(entries.size() - 1);
    }

    public Map.Entry<K, V> lowerEntry(K key) {
        int i = search(key);
        return at(i >= 0 ? i - 1 : -(i + 1) - 1);
    }

    public K lowerKey(K key) {
        return key(lowerEntry(key));
    }

    public Map.Entry<K, V> floorEntry(K key) {
        int i = search(key);
        return at(i >= 0 ? i : -(i + 1) - 1);
    }

    public K floorKey(K key) {
        return key(floorEntry(key));
    }

    public Map.Entry<K, V> ceilingEntry(K key) {
        int i = search(key);
        return at(i >= 0 ? i : -(i + 1));
    }

    public K ceilingKey(K key) {
        return key(ceilingEntry(key));
    }

    public Map.Entry<K, V> higherEntry(K key) {
        int i = search(key);
        return at(i >= 0 ? i + 1 : -(i + 1));
    }

    public K higherKey(K key) {
        return key(higherEntry(key));
    }

    private TreeMap<K, V> range(K from, boolean fromInc, boolean hasFrom, K to, boolean toInc, boolean hasTo) {
        TreeMap<K, V> m = new TreeMap<K, V>(comparator);
        for (Entry<K, V> e : entries) {
            if (hasFrom) {
                int c = compare(e.key, from);
                if (c < 0 || (c == 0 && !fromInc)) {
                    continue;
                }
            }
            if (hasTo) {
                int c = compare(e.key, to);
                if (c > 0 || (c == 0 && !toInc)) {
                    continue;
                }
            }
            m.entries.add(e);
        }
        return m;
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return range(fromKey, fromInclusive, true, toKey, toInclusive, true);
    }

    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return range(null, false, false, toKey, inclusive, true);
    }

    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return range(fromKey, inclusive, true, null, false, false);
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    public SortedMap<K, V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    public SortedMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    public NavigableMap<K, V> descendingMap() {
        TreeMap<K, V> m = new TreeMap<K, V>(Collections.reverseOrder(comparatorOrNatural()));
        for (int i = entries.size() - 1; i >= 0; i--) {
            m.entries.add(entries.get(i));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private Comparator<? super K> comparatorOrNatural() {
        return comparator != null ? comparator : (Comparator<? super K>) Collections.NATURAL_ORDER;
    }

    public NavigableSet<K> navigableKeySet() {
        TreeSet<K> s = new TreeSet<K>(comparator);
        for (Entry<K, V> e : entries) {
            s.add(e.key);
        }
        return s;
    }

    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    private abstract class Itr<T> implements Iterator<T> {
        int index = 0;
        int last = -1;
        int expected = modCount;

        public boolean hasNext() {
            return index < entries.size();
        }

        Entry<K, V> nextEntry() {
            if (expected != modCount) {
                throw new ConcurrentModificationException();
            }
            if (index >= entries.size()) {
                throw new NoSuchElementException();
            }
            last = index;
            return entries.get(index++);
        }

        public void remove() {
            if (last < 0) {
                throw new IllegalStateException();
            }
            entries.remove(last);
            index = last;
            last = -1;
            modCount++;
            expected = modCount;
        }
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K, V>>() {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new Itr<Map.Entry<K, V>>() {
                    public Map.Entry<K, V> next() {
                        return nextEntry();
                    }
                };
            }

            public int size() {
                return entries.size();
            }

            public void clear() {
                TreeMap.this.clear();
            }
        };
    }

    public Set<K> keySet() {
        return new AbstractSet<K>() {
            public Iterator<K> iterator() {
                return new Itr<K>() {
                    public K next() {
                        return nextEntry().key;
                    }
                };
            }

            public int size() {
                return entries.size();
            }

            public boolean contains(Object o) {
                return containsKey(o);
            }

            public boolean remove(Object o) {
                int i = search(o);
                if (i < 0) {
                    return false;
                }
                entries.remove(i);
                modCount++;
                return true;
            }

            public void clear() {
                TreeMap.this.clear();
            }
        };
    }

    public Collection<V> values() {
        return new AbstractCollection<V>() {
            public Iterator<V> iterator() {
                return new Itr<V>() {
                    public V next() {
                        return nextEntry().value;
                    }
                };
            }

            public int size() {
                return entries.size();
            }

            public void clear() {
                TreeMap.this.clear();
            }
        };
    }
}
