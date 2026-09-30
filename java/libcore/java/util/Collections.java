package java.util;

import java.util.function.Consumer;

public class Collections {
    private Collections() {
    }

    @SuppressWarnings("rawtypes")
    static final Comparator NATURAL_ORDER = new Comparator<Object>() {
        @SuppressWarnings("unchecked")
        public int compare(Object a, Object b) {
            return ((Comparable<Object>) a).compareTo(b);
        }
    };

    @SuppressWarnings("rawtypes")
    static final Comparator REVERSE_ORDER = new Comparator<Object>() {
        @SuppressWarnings("unchecked")
        public int compare(Object a, Object b) {
            return ((Comparable<Object>) b).compareTo(a);
        }
    };

    @SuppressWarnings("rawtypes")
    public static final Set EMPTY_SET = new EmptySet<Object>();
    @SuppressWarnings("rawtypes")
    public static final List EMPTY_LIST = new EmptyList<Object>();
    @SuppressWarnings("rawtypes")
    public static final Map EMPTY_MAP = new EmptyMap<Object, Object>();

    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        list.sort(null);
    }

    public static <T> void sort(List<T> list, Comparator<? super T> c) {
        list.sort(c);
    }

    @SuppressWarnings("unchecked")
    public static <T> int binarySearch(List<? extends Comparable<? super T>> list, T key) {
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Comparable<? super T> midVal = list.get(mid);
            int cmp = midVal.compareTo(key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @SuppressWarnings("unchecked")
    public static <T> int binarySearch(List<? extends T> list, T key, Comparator<? super T> c) {
        if (c == null) {
            return binarySearch((List<? extends Comparable<? super T>>) list, key);
        }
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = list.get(mid);
            int cmp = c.compare(midVal, key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void reverse(List<?> list) {
        List l = list;
        for (int i = 0, mid = l.size() >> 1, j = l.size() - 1; i < mid; i++, j--) {
            l.set(i, l.set(j, l.get(i)));
        }
    }

    private static Random r;

    public static void shuffle(List<?> list) {
        if (r == null) {
            r = new Random();
        }
        shuffle(list, r);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void shuffle(List<?> list, Random rnd) {
        List l = list;
        for (int i = l.size(); i > 1; i--) {
            int j = rnd.nextInt(i);
            l.set(i - 1, l.set(j, l.get(i - 1)));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void swap(List<?> list, int i, int j) {
        final List l = list;
        l.set(i, l.set(j, l.get(i)));
    }

    public static <T> void fill(List<? super T> list, T obj) {
        for (int i = 0; i < list.size(); i++) {
            list.set(i, obj);
        }
    }

    public static <T> void copy(List<? super T> dest, List<? extends T> src) {
        if (src.size() > dest.size()) {
            throw new IndexOutOfBoundsException("Source does not fit in dest");
        }
        for (int i = 0; i < src.size(); i++) {
            dest.set(i, src.get(i));
        }
    }

    public static <T extends Object & Comparable<? super T>> T min(Collection<? extends T> coll) {
        Iterator<? extends T> i = coll.iterator();
        T candidate = i.next();
        while (i.hasNext()) {
            T next = i.next();
            if (next.compareTo(candidate) < 0) {
                candidate = next;
            }
        }
        return candidate;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T min(Collection<? extends T> coll, Comparator<? super T> comp) {
        if (comp == null) {
            return (T) min((Collection) coll);
        }
        Iterator<? extends T> i = coll.iterator();
        T candidate = i.next();
        while (i.hasNext()) {
            T next = i.next();
            if (comp.compare(next, candidate) < 0) {
                candidate = next;
            }
        }
        return candidate;
    }

    public static <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll) {
        Iterator<? extends T> i = coll.iterator();
        T candidate = i.next();
        while (i.hasNext()) {
            T next = i.next();
            if (next.compareTo(candidate) > 0) {
                candidate = next;
            }
        }
        return candidate;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T max(Collection<? extends T> coll, Comparator<? super T> comp) {
        if (comp == null) {
            return (T) max((Collection) coll);
        }
        Iterator<? extends T> i = coll.iterator();
        T candidate = i.next();
        while (i.hasNext()) {
            T next = i.next();
            if (comp.compare(next, candidate) > 0) {
                candidate = next;
            }
        }
        return candidate;
    }

    public static void rotate(List<?> list, int distance) {
        int size = list.size();
        if (size == 0) {
            return;
        }
        distance = distance % size;
        if (distance < 0) {
            distance += size;
        }
        if (distance == 0) {
            return;
        }
        reverse(list.subList(0, size - distance));
        reverse(list.subList(size - distance, size));
        reverse(list);
    }

    public static <T> boolean replaceAll(List<T> list, T oldVal, T newVal) {
        boolean result = false;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i), oldVal)) {
                list.set(i, newVal);
                result = true;
            }
        }
        return result;
    }

    public static int indexOfSubList(List<?> source, List<?> target) {
        int sourceSize = source.size();
        int targetSize = target.size();
        outer:
        for (int i = 0; i <= sourceSize - targetSize; i++) {
            for (int j = 0; j < targetSize; j++) {
                if (!Objects.equals(target.get(j), source.get(i + j))) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static int frequency(Collection<?> c, Object o) {
        int result = 0;
        for (Object e : c) {
            if (Objects.equals(o, e)) {
                result++;
            }
        }
        return result;
    }

    public static boolean disjoint(Collection<?> c1, Collection<?> c2) {
        for (Object e : c1) {
            if (c2.contains(e)) {
                return false;
            }
        }
        return true;
    }

    @SafeVarargs
    public static <T> boolean addAll(Collection<? super T> c, T... elements) {
        boolean result = false;
        for (T element : elements) {
            result |= c.add(element);
        }
        return result;
    }

    public static <T> List<T> nCopies(int n, T o) {
        ArrayList<T> l = new ArrayList<T>(n);
        for (int i = 0; i < n; i++) {
            l.add(o);
        }
        return unmodifiableList(l);
    }

    @SuppressWarnings("unchecked")
    public static <T> Comparator<T> reverseOrder() {
        return (Comparator<T>) REVERSE_ORDER;
    }

    @SuppressWarnings("unchecked")
    public static <T> Comparator<T> reverseOrder(final Comparator<T> cmp) {
        if (cmp == null) {
            return (Comparator<T>) REVERSE_ORDER;
        }
        return new Comparator<T>() {
            public int compare(T t1, T t2) {
                return cmp.compare(t2, t1);
            }

            public Comparator<T> reversed() {
                return cmp;
            }
        };
    }

    public static <T> Enumeration<T> enumeration(final Collection<T> c) {
        return new Enumeration<T>() {
            private final Iterator<T> i = c.iterator();

            public boolean hasMoreElements() {
                return i.hasNext();
            }

            public T nextElement() {
                return i.next();
            }
        };
    }

    public static <T> ArrayList<T> list(Enumeration<T> e) {
        ArrayList<T> l = new ArrayList<T>();
        while (e.hasMoreElements()) {
            l.add(e.nextElement());
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    public static <T> Enumeration<T> emptyEnumeration() {
        return (Enumeration<T>) enumeration(EMPTY_LIST);
    }

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> emptyIterator() {
        return (Iterator<T>) EMPTY_LIST.iterator();
    }

    @SuppressWarnings("unchecked")
    public static <T> ListIterator<T> emptyListIterator() {
        return (ListIterator<T>) EMPTY_LIST.listIterator();
    }

    @SuppressWarnings("unchecked")
    public static final <T> Set<T> emptySet() {
        return (Set<T>) EMPTY_SET;
    }

    @SuppressWarnings("unchecked")
    public static <E> SortedSet<E> emptySortedSet() {
        return new TreeSet<E>();
    }

    @SuppressWarnings("unchecked")
    public static final <T> List<T> emptyList() {
        return (List<T>) EMPTY_LIST;
    }

    @SuppressWarnings("unchecked")
    public static final <K, V> Map<K, V> emptyMap() {
        return (Map<K, V>) EMPTY_MAP;
    }

    public static <T> Set<T> singleton(T o) {
        HashSet<T> s = new HashSet<T>(2);
        s.add(o);
        return unmodifiableSet(s);
    }

    public static <T> List<T> singletonList(T o) {
        ArrayList<T> l = new ArrayList<T>(1);
        l.add(o);
        return unmodifiableList(l);
    }

    public static <K, V> Map<K, V> singletonMap(K key, V value) {
        HashMap<K, V> m = new HashMap<K, V>(2);
        m.put(key, value);
        return unmodifiableMap(m);
    }

    private static class EmptySet<E> extends AbstractSet<E> implements java.io.Serializable {
        public Iterator<E> iterator() {
            return new ArrayList<E>(0).iterator();
        }

        public int size() {
            return 0;
        }

        public boolean contains(Object obj) {
            return false;
        }
    }

    private static class EmptyList<E> extends AbstractList<E> implements RandomAccess, java.io.Serializable {
        public int size() {
            return 0;
        }

        public E get(int index) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }

        public boolean contains(Object obj) {
            return false;
        }
    }

    private static class EmptyMap<K, V> extends AbstractMap<K, V> implements java.io.Serializable {
        public Set<Map.Entry<K, V>> entrySet() {
            return emptySet();
        }

        public int size() {
            return 0;
        }

        public V get(Object key) {
            return null;
        }

        public boolean containsKey(Object key) {
            return false;
        }
    }

    /* ---- unmodifiable wrappers ---- */

    static class UnmodifiableCollection<E> implements Collection<E>, java.io.Serializable {
        final Collection<? extends E> c;

        UnmodifiableCollection(Collection<? extends E> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            this.c = c;
        }

        public int size() {
            return c.size();
        }

        public boolean isEmpty() {
            return c.isEmpty();
        }

        public boolean contains(Object o) {
            return c.contains(o);
        }

        public Object[] toArray() {
            return c.toArray();
        }

        public <T> T[] toArray(T[] a) {
            return c.toArray(a);
        }

        public String toString() {
            return c.toString();
        }

        public Iterator<E> iterator() {
            final Iterator<? extends E> i = c.iterator();
            return new Iterator<E>() {
                public boolean hasNext() {
                    return i.hasNext();
                }

                public E next() {
                    return i.next();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public boolean containsAll(Collection<?> coll) {
            return c.containsAll(coll);
        }

        public boolean addAll(Collection<? extends E> coll) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> coll) {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> coll) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        public void forEach(Consumer<? super E> action) {
            ((Collection<E>) c).forEach(action);
        }
    }

    public static <T> Collection<T> unmodifiableCollection(Collection<? extends T> c) {
        return new UnmodifiableCollection<T>(c);
    }

    static class UnmodifiableSet<E> extends UnmodifiableCollection<E> implements Set<E> {
        UnmodifiableSet(Set<? extends E> s) {
            super(s);
        }

        public boolean equals(Object o) {
            return o == this || c.equals(o);
        }

        public int hashCode() {
            return c.hashCode();
        }
    }

    public static <T> Set<T> unmodifiableSet(Set<? extends T> s) {
        return new UnmodifiableSet<T>(s);
    }

    public static <T> SortedSet<T> unmodifiableSortedSet(SortedSet<T> s) {
        return s;
    }

    static class UnmodifiableList<E> extends UnmodifiableCollection<E> implements List<E>, RandomAccess {
        final List<? extends E> list;

        UnmodifiableList(List<? extends E> list) {
            super(list);
            this.list = list;
        }

        public boolean equals(Object o) {
            return o == this || list.equals(o);
        }

        public int hashCode() {
            return list.hashCode();
        }

        public E get(int index) {
            return list.get(index);
        }

        public E set(int index, E element) {
            throw new UnsupportedOperationException();
        }

        public void add(int index, E element) {
            throw new UnsupportedOperationException();
        }

        public E remove(int index) {
            throw new UnsupportedOperationException();
        }

        public int indexOf(Object o) {
            return list.indexOf(o);
        }

        public int lastIndexOf(Object o) {
            return list.lastIndexOf(o);
        }

        public boolean addAll(int index, Collection<? extends E> c) {
            throw new UnsupportedOperationException();
        }

        public ListIterator<E> listIterator() {
            return listIterator(0);
        }

        public ListIterator<E> listIterator(final int index) {
            final ListIterator<? extends E> i = list.listIterator(index);
            return new ListIterator<E>() {
                public boolean hasNext() {
                    return i.hasNext();
                }

                public E next() {
                    return i.next();
                }

                public boolean hasPrevious() {
                    return i.hasPrevious();
                }

                public E previous() {
                    return i.previous();
                }

                public int nextIndex() {
                    return i.nextIndex();
                }

                public int previousIndex() {
                    return i.previousIndex();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                public void set(E e) {
                    throw new UnsupportedOperationException();
                }

                public void add(E e) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public List<E> subList(int fromIndex, int toIndex) {
            return new UnmodifiableList<E>(list.subList(fromIndex, toIndex));
        }

        public void sort(Comparator<? super E> c) {
            throw new UnsupportedOperationException();
        }
    }

    public static <T> List<T> unmodifiableList(List<? extends T> list) {
        return new UnmodifiableList<T>(list);
    }

    static class UnmodifiableMap<K, V> implements Map<K, V>, java.io.Serializable {
        private final Map<? extends K, ? extends V> m;

        UnmodifiableMap(Map<? extends K, ? extends V> m) {
            if (m == null) {
                throw new NullPointerException();
            }
            this.m = m;
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public boolean containsKey(Object key) {
            return m.containsKey(key);
        }

        public boolean containsValue(Object val) {
            return m.containsValue(val);
        }

        public V get(Object key) {
            return m.get(key);
        }

        public V put(K key, V value) {
            throw new UnsupportedOperationException();
        }

        public V remove(Object key) {
            throw new UnsupportedOperationException();
        }

        public void putAll(Map<? extends K, ? extends V> map) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        public Set<K> keySet() {
            return unmodifiableSet((Set<K>) m.keySet());
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public Set<Map.Entry<K, V>> entrySet() {
            return unmodifiableSet((Set) m.entrySet());
        }

        @SuppressWarnings("unchecked")
        public Collection<V> values() {
            return unmodifiableCollection((Collection<V>) m.values());
        }

        public boolean equals(Object o) {
            return o == this || m.equals(o);
        }

        public int hashCode() {
            return m.hashCode();
        }

        public String toString() {
            return m.toString();
        }
    }

    public static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> m) {
        return new UnmodifiableMap<K, V>(m);
    }

    public static <K, V> SortedMap<K, V> unmodifiableSortedMap(SortedMap<K, ? extends V> m) {
        @SuppressWarnings("unchecked")
        SortedMap<K, V> r = (SortedMap<K, V>) m;
        return r;
    }

    /* ---- synchronized wrappers (the VM serializes bytecode execution, so these delegate) ---- */

    public static <T> Collection<T> synchronizedCollection(Collection<T> c) {
        return c;
    }

    public static <T> Set<T> synchronizedSet(Set<T> s) {
        return s;
    }

    public static <T> SortedSet<T> synchronizedSortedSet(SortedSet<T> s) {
        return s;
    }

    public static <T> List<T> synchronizedList(List<T> list) {
        return new SynchronizedList<T>(list);
    }

    public static <K, V> Map<K, V> synchronizedMap(Map<K, V> m) {
        return new SynchronizedMap<K, V>(m);
    }

    public static <K, V> SortedMap<K, V> synchronizedSortedMap(SortedMap<K, V> m) {
        return m;
    }

    static class SynchronizedList<E> extends AbstractList<E> implements RandomAccess {
        final List<E> list;

        SynchronizedList(List<E> list) {
            this.list = list;
        }

        public synchronized E get(int index) {
            return list.get(index);
        }

        public synchronized int size() {
            return list.size();
        }

        public synchronized E set(int index, E element) {
            return list.set(index, element);
        }

        public synchronized void add(int index, E element) {
            list.add(index, element);
            modCount++;
        }

        public synchronized E remove(int index) {
            modCount++;
            return list.remove(index);
        }

        public synchronized boolean remove(Object o) {
            modCount++;
            return list.remove(o);
        }

        public synchronized void clear() {
            modCount++;
            list.clear();
        }
    }

    static class SynchronizedMap<K, V> extends AbstractMap<K, V> {
        final Map<K, V> m;

        SynchronizedMap(Map<K, V> m) {
            this.m = m;
        }

        public synchronized int size() {
            return m.size();
        }

        public synchronized boolean containsKey(Object key) {
            return m.containsKey(key);
        }

        public synchronized V get(Object key) {
            return m.get(key);
        }

        public synchronized V put(K key, V value) {
            return m.put(key, value);
        }

        public synchronized V remove(Object key) {
            return m.remove(key);
        }

        public synchronized void clear() {
            m.clear();
        }

        public Set<Map.Entry<K, V>> entrySet() {
            return m.entrySet();
        }

        public Set<K> keySet() {
            return m.keySet();
        }

        public Collection<V> values() {
            return m.values();
        }
    }

    public static <E> Collection<E> checkedCollection(Collection<E> c, Class<E> type) {
        return c;
    }

    public static <E> List<E> checkedList(List<E> list, Class<E> type) {
        return list;
    }

    public static <E> Set<E> newSetFromMap(Map<E, Boolean> map) {
        final Map<E, Boolean> m = map;
        return new AbstractSet<E>() {
            public Iterator<E> iterator() {
                return m.keySet().iterator();
            }

            public int size() {
                return m.size();
            }

            public boolean add(E e) {
                return m.put(e, Boolean.TRUE) == null;
            }

            public boolean remove(Object o) {
                return m.remove(o) != null;
            }

            public boolean contains(Object o) {
                return m.containsKey(o);
            }

            public void clear() {
                m.clear();
            }
        };
    }

    public static <T> Queue<T> asLifoQueue(Deque<T> deque) {
        return deque;
    }
}
