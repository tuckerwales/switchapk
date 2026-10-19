package java.util;

/**
 * Red-black tree map. Lookups, insertions and removals are O(log n). Sub-maps, descending maps, key sets and
 * entry sets are live views, iterators are fail-fast, and entries handed out by the navigation methods
 * (firstEntry, ceilingEntry, ...) are immutable snapshots, all as the JDK specifies.
 */
public class TreeMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 919286545866124006L;
    private final Comparator<? super K> comparator;
    private transient Node<K, V> root;
    private transient int size;
    private transient int modCount;

    private static final boolean RED = false;
    private static final boolean BLACK = true;

    /** Java's serial form: the comparator (a field), the size, then each key and value in order. */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeInt(size);
        for (Node<K, V> e = first(); e != null; e = successor(e)) {
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        int n = s.readInt();
        if (n < 0) {
            throw new java.io.StreamCorruptedException("Illegal size: " + n);
        }
        for (int i = 0; i < n; i++) {
            put((K) s.readObject(), (V) s.readObject());
        }
    }

    static final class Node<K, V> implements Map.Entry<K, V> {
        final K key;
        V value;
        Node<K, V> left, right, parent;
        boolean color = BLACK;

        Node(K key, V value, Node<K, V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
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

    // ---- tree search

    final Node<K, V> getNode(Object key) {
        if (key == null && comparator == null) {
            throw new NullPointerException();
        }
        Node<K, V> p = root;
        while (p != null) {
            int c = compare(key, p.key);
            if (c < 0) {
                p = p.left;
            } else if (c > 0) {
                p = p.right;
            } else {
                return p;
            }
        }
        return null;
    }

    final Node<K, V> first() {
        Node<K, V> p = root;
        if (p != null) {
            while (p.left != null) {
                p = p.left;
            }
        }
        return p;
    }

    final Node<K, V> last() {
        Node<K, V> p = root;
        if (p != null) {
            while (p.right != null) {
                p = p.right;
            }
        }
        return p;
    }

    static <K, V> Node<K, V> successor(Node<K, V> t) {
        if (t == null) {
            return null;
        }
        if (t.right != null) {
            Node<K, V> p = t.right;
            while (p.left != null) {
                p = p.left;
            }
            return p;
        }
        Node<K, V> p = t.parent, ch = t;
        while (p != null && ch == p.right) {
            ch = p;
            p = p.parent;
        }
        return p;
    }

    static <K, V> Node<K, V> predecessor(Node<K, V> t) {
        if (t == null) {
            return null;
        }
        if (t.left != null) {
            Node<K, V> p = t.left;
            while (p.right != null) {
                p = p.right;
            }
            return p;
        }
        Node<K, V> p = t.parent, ch = t;
        while (p != null && ch == p.left) {
            ch = p;
            p = p.parent;
        }
        return p;
    }

    /** The least node with key >= key (inclusive) or > key. */
    final Node<K, V> ceilingNode(Object key, boolean inclusive) {
        if (key == null && comparator == null) {
            throw new NullPointerException();
        }
        Node<K, V> p = root, best = null;
        while (p != null) {
            int c = compare(key, p.key);
            if (c < 0 || (c == 0 && inclusive)) {
                if (c == 0) {
                    return p;
                }
                best = p;
                p = p.left;
            } else {
                p = p.right;
            }
        }
        return best;
    }

    /** The greatest node with key <= key (inclusive) or < key. */
    final Node<K, V> floorNode(Object key, boolean inclusive) {
        if (key == null && comparator == null) {
            throw new NullPointerException();
        }
        Node<K, V> p = root, best = null;
        while (p != null) {
            int c = compare(key, p.key);
            if (c > 0 || (c == 0 && inclusive)) {
                if (c == 0) {
                    return p;
                }
                best = p;
                p = p.right;
            } else {
                p = p.left;
            }
        }
        return best;
    }

    // ---- red-black insertion and deletion

    private static <K, V> boolean colorOf(Node<K, V> p) {
        return p == null ? BLACK : p.color;
    }

    private static <K, V> Node<K, V> parentOf(Node<K, V> p) {
        return p == null ? null : p.parent;
    }

    private static <K, V> void setColor(Node<K, V> p, boolean c) {
        if (p != null) {
            p.color = c;
        }
    }

    private static <K, V> Node<K, V> leftOf(Node<K, V> p) {
        return p == null ? null : p.left;
    }

    private static <K, V> Node<K, V> rightOf(Node<K, V> p) {
        return p == null ? null : p.right;
    }

    private void rotateLeft(Node<K, V> p) {
        if (p == null) {
            return;
        }
        Node<K, V> r = p.right;
        p.right = r.left;
        if (r.left != null) {
            r.left.parent = p;
        }
        r.parent = p.parent;
        if (p.parent == null) {
            root = r;
        } else if (p.parent.left == p) {
            p.parent.left = r;
        } else {
            p.parent.right = r;
        }
        r.left = p;
        p.parent = r;
    }

    private void rotateRight(Node<K, V> p) {
        if (p == null) {
            return;
        }
        Node<K, V> l = p.left;
        p.left = l.right;
        if (l.right != null) {
            l.right.parent = p;
        }
        l.parent = p.parent;
        if (p.parent == null) {
            root = l;
        } else if (p.parent.right == p) {
            p.parent.right = l;
        } else {
            p.parent.left = l;
        }
        l.right = p;
        p.parent = l;
    }

    private void fixAfterInsertion(Node<K, V> x) {
        x.color = RED;
        while (x != null && x != root && x.parent.color == RED) {
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                Node<K, V> y = rightOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == rightOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateLeft(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                Node<K, V> y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        root.color = BLACK;
    }

    /** Unlinks p. A node with two children swaps places (not keys) with its successor first, so other Node
     * references (iterators, entry views) stay attached to their own keys. */
    final void deleteNode(Node<K, V> p) {
        modCount++;
        size--;
        if (p.left != null && p.right != null) {
            swapWithSuccessor(p);
        }
        Node<K, V> replacement = p.left != null ? p.left : p.right;
        if (replacement != null) {
            replacement.parent = p.parent;
            if (p.parent == null) {
                root = replacement;
            } else if (p == p.parent.left) {
                p.parent.left = replacement;
            } else {
                p.parent.right = replacement;
            }
            p.left = p.right = p.parent = null;
            if (p.color == BLACK) {
                fixAfterDeletion(replacement);
            }
        } else if (p.parent == null) {
            root = null;
        } else {
            if (p.color == BLACK) {
                fixAfterDeletion(p);
            }
            if (p.parent != null) {
                if (p == p.parent.left) {
                    p.parent.left = null;
                } else if (p == p.parent.right) {
                    p.parent.right = null;
                }
                p.parent = null;
            }
        }
    }

    /** Exchanges the tree positions (links and colors) of p and its in-order successor s. */
    private void swapWithSuccessor(Node<K, V> p) {
        Node<K, V> s = successor(p);
        boolean pc = p.color;
        p.color = s.color;
        s.color = pc;
        Node<K, V> pp = p.parent, pl = p.left, pr = p.right;
        Node<K, V> sp = s.parent, sr = s.right; /* s has no left child */
        // s takes p's place
        s.parent = pp;
        if (pp == null) {
            root = s;
        } else if (pp.left == p) {
            pp.left = s;
        } else {
            pp.right = s;
        }
        s.left = pl;
        pl.parent = s;
        if (sp == p) {
            // s was p's right child
            s.right = p;
            p.parent = s;
        } else {
            s.right = pr;
            pr.parent = s;
            p.parent = sp;
            sp.left = p;
        }
        // p takes s's old children
        p.left = null;
        p.right = sr;
        if (sr != null) {
            sr.parent = p;
        }
    }

    private void fixAfterDeletion(Node<K, V> x) {
        while (x != root && colorOf(x) == BLACK) {
            if (x == leftOf(parentOf(x))) {
                Node<K, V> sib = rightOf(parentOf(x));
                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateLeft(parentOf(x));
                    sib = rightOf(parentOf(x));
                }
                if (colorOf(leftOf(sib)) == BLACK && colorOf(rightOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(rightOf(sib)) == BLACK) {
                        setColor(leftOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateRight(sib);
                        sib = rightOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(rightOf(sib), BLACK);
                    rotateLeft(parentOf(x));
                    x = root;
                }
            } else {
                Node<K, V> sib = leftOf(parentOf(x));
                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }
                if (colorOf(rightOf(sib)) == BLACK && colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }
        setColor(x, BLACK);
    }

    // ---- Map

    public int size() {
        return size;
    }

    public boolean containsKey(Object key) {
        return getNode(key) != null;
    }

    public boolean containsValue(Object value) {
        for (Node<K, V> e = first(); e != null; e = successor(e)) {
            if (Objects.equals(value, e.value)) {
                return true;
            }
        }
        return false;
    }

    public V get(Object key) {
        Node<K, V> p = getNode(key);
        return p == null ? null : p.value;
    }

    public Comparator<? super K> comparator() {
        return comparator;
    }

    public K firstKey() {
        return keyOrThrow(first());
    }

    public K lastKey() {
        return keyOrThrow(last());
    }

    public V put(K key, V value) {
        Node<K, V> t = root;
        if (t == null) {
            compare(key, key); // type and null check
            root = new Node<K, V>(key, value, null);
            size = 1;
            modCount++;
            return null;
        }
        if (key == null && comparator == null) {
            throw new NullPointerException();
        }
        Node<K, V> parent;
        int c;
        do {
            parent = t;
            c = compare(key, t.key);
            if (c < 0) {
                t = t.left;
            } else if (c > 0) {
                t = t.right;
            } else {
                return t.setValue(value);
            }
        } while (t != null);
        Node<K, V> e = new Node<K, V>(key, value, parent);
        if (c < 0) {
            parent.left = e;
        } else {
            parent.right = e;
        }
        fixAfterInsertion(e);
        size++;
        modCount++;
        return null;
    }

    public V remove(Object key) {
        Node<K, V> p = getNode(key);
        if (p == null) {
            return null;
        }
        V old = p.value;
        deleteNode(p);
        return old;
    }

    public void clear() {
        modCount++;
        size = 0;
        root = null;
    }

    public Object clone() {
        TreeMap<K, V> m;
        try {
            @SuppressWarnings("unchecked")
            TreeMap<K, V> c = (TreeMap<K, V>) super.clone();
            m = c;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
        m.root = null;
        m.size = 0;
        m.modCount = 0;
        m.entrySet = null;
        m.navigableKeySet = null;
        m.descendingMap = null;
        for (Node<K, V> e = first(); e != null; e = successor(e)) {
            m.put(e.key, e.value);
        }
        return m;
    }

    // ---- NavigableMap

    static <K, V> Map.Entry<K, V> exportEntry(Node<K, V> e) {
        return e == null ? null : new AbstractMap.SimpleImmutableEntry<K, V>(e);
    }

    static <K> K keyOrNull(Node<K, ?> e) {
        return e == null ? null : e.key;
    }

    static <K> K keyOrThrow(Node<K, ?> e) {
        if (e == null) {
            throw new NoSuchElementException();
        }
        return e.key;
    }

    public Map.Entry<K, V> firstEntry() {
        return exportEntry(first());
    }

    public Map.Entry<K, V> lastEntry() {
        return exportEntry(last());
    }

    public Map.Entry<K, V> pollFirstEntry() {
        Node<K, V> p = first();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null) {
            deleteNode(p);
        }
        return result;
    }

    public Map.Entry<K, V> pollLastEntry() {
        Node<K, V> p = last();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null) {
            deleteNode(p);
        }
        return result;
    }

    public Map.Entry<K, V> lowerEntry(K key) {
        return exportEntry(floorNode(key, false));
    }

    public K lowerKey(K key) {
        return keyOrNull(floorNode(key, false));
    }

    public Map.Entry<K, V> floorEntry(K key) {
        return exportEntry(floorNode(key, true));
    }

    public K floorKey(K key) {
        return keyOrNull(floorNode(key, true));
    }

    public Map.Entry<K, V> ceilingEntry(K key) {
        return exportEntry(ceilingNode(key, true));
    }

    public K ceilingKey(K key) {
        return keyOrNull(ceilingNode(key, true));
    }

    public Map.Entry<K, V> higherEntry(K key) {
        return exportEntry(ceilingNode(key, false));
    }

    public K higherKey(K key) {
        return keyOrNull(ceilingNode(key, false));
    }

    private transient EntrySet entrySet;
    private transient KeySet<K> navigableKeySet;
    private transient NavigableMap<K, V> descendingMap;

    public Set<K> keySet() {
        return navigableKeySet();
    }

    public NavigableSet<K> navigableKeySet() {
        KeySet<K> ks = navigableKeySet;
        return ks != null ? ks : (navigableKeySet = new KeySet<K>(this));
    }

    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    public Collection<V> values() {
        return new AbstractCollection<V>() {
            public Iterator<V> iterator() {
                return new ValueIterator(first());
            }

            public int size() {
                return TreeMap.this.size();
            }

            public boolean contains(Object o) {
                return containsValue(o);
            }

            public boolean remove(Object o) {
                for (Node<K, V> e = first(); e != null; e = successor(e)) {
                    if (Objects.equals(e.value, o)) {
                        deleteNode(e);
                        return true;
                    }
                }
                return false;
            }

            public void clear() {
                TreeMap.this.clear();
            }
        };
    }

    public Set<Map.Entry<K, V>> entrySet() {
        EntrySet es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator(first());
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Node<K, V> p = getNode(entry.getKey());
            return p != null && Objects.equals(p.value, entry.getValue());
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Node<K, V> p = getNode(entry.getKey());
            if (p != null && Objects.equals(p.value, entry.getValue())) {
                deleteNode(p);
                return true;
            }
            return false;
        }

        public int size() {
            return TreeMap.this.size();
        }

        public void clear() {
            TreeMap.this.clear();
        }
    }

    public NavigableMap<K, V> descendingMap() {
        NavigableMap<K, V> km = descendingMap;
        return km != null ? km : (descendingMap = new SubMap<K, V>(this, true, null, true, true, null, true, true));
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return new SubMap<K, V>(this, false, fromKey, fromInclusive, false, toKey, toInclusive, false);
    }

    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new SubMap<K, V>(this, true, null, true, false, toKey, inclusive, false);
    }

    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new SubMap<K, V>(this, false, fromKey, inclusive, true, null, true, false);
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

    // ---- iterators

    /** Walks nodes from first towards fence (exclusive, null for the end), ascending or descending. Nodes keep
     * their identity through deletions (deleteNode swaps positions, not keys), so the fence stays valid. */
    abstract class PrivateIterator<T> implements Iterator<T> {
        Node<K, V> next;
        Node<K, V> lastReturned;
        int expectedModCount;
        final Node<K, V> fence;
        final boolean descending;

        PrivateIterator(Node<K, V> first) {
            this(first, null, false);
        }

        PrivateIterator(Node<K, V> first, Node<K, V> fence, boolean descending) {
            expectedModCount = modCount;
            this.next = first;
            this.fence = fence;
            this.descending = descending;
        }

        public final boolean hasNext() {
            return next != null && next != fence;
        }

        final Node<K, V> nextNode() {
            Node<K, V> e = next;
            if (e == null || e == fence) {
                throw new NoSuchElementException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            next = descending ? predecessor(e) : successor(e);
            lastReturned = e;
            return e;
        }

        public void remove() {
            if (lastReturned == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            deleteNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    final class EntryIterator extends PrivateIterator<Map.Entry<K, V>> {
        EntryIterator(Node<K, V> first) {
            super(first);
        }

        public Map.Entry<K, V> next() {
            return nextNode();
        }
    }

    final class ValueIterator extends PrivateIterator<V> {
        ValueIterator(Node<K, V> first) {
            super(first);
        }

        public V next() {
            return nextNode().value;
        }
    }

    // ---- key set view, shared by the map and its sub-maps

    static final class KeySet<E> extends AbstractSet<E> implements NavigableSet<E> {
        private final NavigableMap<E, ?> m;

        KeySet(NavigableMap<E, ?> map) {
            m = map;
        }

        public Iterator<E> iterator() {
            final Iterator<? extends Map.Entry<E, ?>> it = m.entrySet().iterator();
            return new Iterator<E>() {
                public boolean hasNext() {
                    return it.hasNext();
                }

                public E next() {
                    return it.next().getKey();
                }

                public void remove() {
                    it.remove();
                }
            };
        }

        public Iterator<E> descendingIterator() {
            return descendingSet().iterator();
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

        public E first() {
            return m.firstKey();
        }

        public E last() {
            return m.lastKey();
        }

        public Comparator<? super E> comparator() {
            return m.comparator();
        }

        public E pollFirst() {
            Map.Entry<E, ?> e = m.pollFirstEntry();
            return e == null ? null : e.getKey();
        }

        public E pollLast() {
            Map.Entry<E, ?> e = m.pollLastEntry();
            return e == null ? null : e.getKey();
        }

        public boolean remove(Object o) {
            int old = size();
            m.remove(o);
            return size() != old;
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

        public SortedSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }

        public SortedSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }

        public SortedSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }

        public NavigableSet<E> descendingSet() {
            return new KeySet<E>(m.descendingMap());
        }
    }

    // ---- sub-maps

    /**
     * A range of the backing map, optionally in descending order. Bounds are in the map's own (ascending)
     * order: lo..hi, each either open-ended (fromStart / toEnd) or inclusive or not. Methods named abs* work in
     * ascending terms; the public ones swap them when descending.
     */
    static final class SubMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, java.io.Serializable {
        private static final long serialVersionUID = -6520786458950516097L;
        final TreeMap<K, V> m;
        final K lo, hi;
        final boolean fromStart, toEnd;
        final boolean loInclusive, hiInclusive;
        final boolean descending;

        SubMap(TreeMap<K, V> m, boolean fromStart, K lo, boolean loInclusive, boolean toEnd, K hi,
               boolean hiInclusive, boolean descending) {
            if (!fromStart && !toEnd) {
                if (m.compare(lo, hi) > 0) {
                    throw new IllegalArgumentException("fromKey > toKey");
                }
            } else {
                if (!fromStart) {
                    m.compare(lo, lo); // type and null check
                }
                if (!toEnd) {
                    m.compare(hi, hi);
                }
            }
            this.m = m;
            this.fromStart = fromStart;
            this.lo = lo;
            this.loInclusive = loInclusive;
            this.toEnd = toEnd;
            this.hi = hi;
            this.hiInclusive = hiInclusive;
            this.descending = descending;
        }

        // range checks

        final boolean tooLow(Object key) {
            if (!fromStart) {
                int c = m.compare(key, lo);
                if (c < 0 || (c == 0 && !loInclusive)) {
                    return true;
                }
            }
            return false;
        }

        final boolean tooHigh(Object key) {
            if (!toEnd) {
                int c = m.compare(key, hi);
                if (c > 0 || (c == 0 && !hiInclusive)) {
                    return true;
                }
            }
            return false;
        }

        final boolean inRange(Object key) {
            return !tooLow(key) && !tooHigh(key);
        }

        final boolean inClosedRange(Object key) {
            return (fromStart || m.compare(key, lo) >= 0) && (toEnd || m.compare(hi, key) >= 0);
        }

        final boolean inRange(Object key, boolean inclusive) {
            return inclusive ? inRange(key) : inClosedRange(key);
        }

        // ascending primitives

        final Node<K, V> absLowest() {
            Node<K, V> e = fromStart ? m.first() : m.ceilingNode(lo, loInclusive);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final Node<K, V> absHighest() {
            Node<K, V> e = toEnd ? m.last() : m.floorNode(hi, hiInclusive);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final Node<K, V> absCeiling(K key) {
            if (tooLow(key)) {
                return absLowest();
            }
            Node<K, V> e = m.ceilingNode(key, true);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final Node<K, V> absHigher(K key) {
            if (tooLow(key)) {
                return absLowest();
            }
            Node<K, V> e = m.ceilingNode(key, false);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final Node<K, V> absFloor(K key) {
            if (tooHigh(key)) {
                return absHighest();
            }
            Node<K, V> e = m.floorNode(key, true);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final Node<K, V> absLower(K key) {
            if (tooHigh(key)) {
                return absHighest();
            }
            Node<K, V> e = m.floorNode(key, false);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        /** The first node past the high end (ascending) or low end (descending), where iteration stops. */
        final Node<K, V> absHighFence() {
            return toEnd ? null : m.ceilingNode(hi, !hiInclusive);
        }

        final Node<K, V> absLowFence() {
            return fromStart ? null : m.floorNode(lo, !loInclusive);
        }

        // directional wrappers

        final Node<K, V> subLowest() {
            return descending ? absHighest() : absLowest();
        }

        final Node<K, V> subHighest() {
            return descending ? absLowest() : absHighest();
        }

        final Node<K, V> subCeiling(K key) {
            return descending ? absFloor(key) : absCeiling(key);
        }

        final Node<K, V> subHigher(K key) {
            return descending ? absLower(key) : absHigher(key);
        }

        final Node<K, V> subFloor(K key) {
            return descending ? absCeiling(key) : absFloor(key);
        }

        final Node<K, V> subLower(K key) {
            return descending ? absHigher(key) : absLower(key);
        }

        // Map

        public boolean isEmpty() {
            return (fromStart && toEnd) ? m.isEmpty() : absLowest() == null;
        }

        public int size() {
            if (fromStart && toEnd) {
                return m.size();
            }
            int n = 0;
            for (Iterator<?> it = entrySet().iterator(); it.hasNext(); it.next()) {
                n++;
            }
            return n;
        }

        public final boolean containsKey(Object key) {
            return inRange(key) && m.containsKey(key);
        }

        public final V put(K key, V value) {
            if (!inRange(key)) {
                throw new IllegalArgumentException("key out of range");
            }
            return m.put(key, value);
        }

        public final V get(Object key) {
            return !inRange(key) ? null : m.get(key);
        }

        public final V remove(Object key) {
            return !inRange(key) ? null : m.remove(key);
        }

        public void clear() {
            if (fromStart && toEnd) {
                m.clear();
                return;
            }
            for (Iterator<?> it = entrySet().iterator(); it.hasNext();) {
                it.next();
                it.remove();
            }
        }

        public Comparator<? super K> comparator() {
            return descending ? Collections.reverseOrder(m.comparator) : m.comparator;
        }

        public final K firstKey() {
            return keyOrThrow(subLowest());
        }

        public final K lastKey() {
            return keyOrThrow(subHighest());
        }

        public final Map.Entry<K, V> firstEntry() {
            return exportEntry(subLowest());
        }

        public final Map.Entry<K, V> lastEntry() {
            return exportEntry(subHighest());
        }

        public final Map.Entry<K, V> pollFirstEntry() {
            Node<K, V> e = subLowest();
            Map.Entry<K, V> result = exportEntry(e);
            if (e != null) {
                m.deleteNode(e);
            }
            return result;
        }

        public final Map.Entry<K, V> pollLastEntry() {
            Node<K, V> e = subHighest();
            Map.Entry<K, V> result = exportEntry(e);
            if (e != null) {
                m.deleteNode(e);
            }
            return result;
        }

        public final Map.Entry<K, V> ceilingEntry(K key) {
            return exportEntry(subCeiling(key));
        }

        public final K ceilingKey(K key) {
            return keyOrNull(subCeiling(key));
        }

        public final Map.Entry<K, V> higherEntry(K key) {
            return exportEntry(subHigher(key));
        }

        public final K higherKey(K key) {
            return keyOrNull(subHigher(key));
        }

        public final Map.Entry<K, V> floorEntry(K key) {
            return exportEntry(subFloor(key));
        }

        public final K floorKey(K key) {
            return keyOrNull(subFloor(key));
        }

        public final Map.Entry<K, V> lowerEntry(K key) {
            return exportEntry(subLower(key));
        }

        public final K lowerKey(K key) {
            return keyOrNull(subLower(key));
        }

        public final Set<K> keySet() {
            return navigableKeySet();
        }

        public final NavigableSet<K> navigableKeySet() {
            return new KeySet<K>(this);
        }

        public NavigableSet<K> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        public NavigableMap<K, V> descendingMap() {
            return new SubMap<K, V>(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive, !descending);
        }

        public Set<Map.Entry<K, V>> entrySet() {
            return new AbstractSet<Map.Entry<K, V>>() {
                public Iterator<Map.Entry<K, V>> iterator() {
                    if (descending) {
                        return m.new PrivateIterator<Map.Entry<K, V>>(absHighest(), absLowFence(), true) {
                            public Map.Entry<K, V> next() {
                                return nextNode();
                            }
                        };
                    }
                    return m.new PrivateIterator<Map.Entry<K, V>>(absLowest(), absHighFence(), false) {
                        public Map.Entry<K, V> next() {
                            return nextNode();
                        }
                    };
                }

                public int size() {
                    return SubMap.this.size();
                }

                public boolean isEmpty() {
                    return SubMap.this.isEmpty();
                }

                public boolean contains(Object o) {
                    if (!(o instanceof Map.Entry)) {
                        return false;
                    }
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                    Object key = entry.getKey();
                    if (!inRange(key)) {
                        return false;
                    }
                    Node<K, V> node = m.getNode(key);
                    return node != null && Objects.equals(node.value, entry.getValue());
                }

                public boolean remove(Object o) {
                    if (!(o instanceof Map.Entry)) {
                        return false;
                    }
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                    Object key = entry.getKey();
                    if (!inRange(key)) {
                        return false;
                    }
                    Node<K, V> node = m.getNode(key);
                    if (node != null && Objects.equals(node.value, entry.getValue())) {
                        m.deleteNode(node);
                        return true;
                    }
                    return false;
                }

                public void clear() {
                    SubMap.this.clear();
                }
            };
        }

        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
            if (descending) {
                // a descending view's "from" is its higher end
                if (!inRange(fromKey, fromInclusive)) {
                    throw new IllegalArgumentException("fromKey out of range");
                }
                if (!inRange(toKey, toInclusive)) {
                    throw new IllegalArgumentException("toKey out of range");
                }
                return new SubMap<K, V>(m, false, toKey, toInclusive, false, fromKey, fromInclusive, true);
            }
            if (!inRange(fromKey, fromInclusive)) {
                throw new IllegalArgumentException("fromKey out of range");
            }
            if (!inRange(toKey, toInclusive)) {
                throw new IllegalArgumentException("toKey out of range");
            }
            return new SubMap<K, V>(m, false, fromKey, fromInclusive, false, toKey, toInclusive, false);
        }

        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive)) {
                throw new IllegalArgumentException("toKey out of range");
            }
            if (descending) {
                return new SubMap<K, V>(m, false, toKey, inclusive, toEnd, hi, hiInclusive, true);
            }
            return new SubMap<K, V>(m, fromStart, lo, loInclusive, false, toKey, inclusive, false);
        }

        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive)) {
                throw new IllegalArgumentException("fromKey out of range");
            }
            if (descending) {
                return new SubMap<K, V>(m, fromStart, lo, loInclusive, false, fromKey, inclusive, true);
            }
            return new SubMap<K, V>(m, false, fromKey, inclusive, toEnd, hi, hiInclusive, false);
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
    }
}
