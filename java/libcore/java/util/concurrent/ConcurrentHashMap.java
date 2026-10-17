package java.util.concurrent;

import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

/**
 * Hash table with OpenJDK's ConcurrentHashMap layout, so iteration order matches OpenJDK for the same operations:
 * power-of-two table, spread hashes, new nodes appended at the bin tail, resizes that split each bin into lo/hi lists
 * (nodes before the bin's last run are copied in reverse, as in OpenJDK's transfer), and the same sizeCtl rules.
 *
 * Concurrency is simpler than OpenJDK's: every update runs under one map-wide lock (a private object), while reads
 * (get, iterators, views, bulk operations) run without locking over the current table. Updates keep the table
 * readable at every step like OpenJDK's: a resize fills the new table bin by bin and leaves ForwardingNodes in the
 * old one, so readers and iterators that still hold the old table follow them. Iterators are weakly consistent and
 * never throw ConcurrentModificationException. Bins are never treeified (OpenJDK turns bins of more than 8 colliding
 * entries in tables of 64 or more into red-black trees, whose iteration order can differ); the small-table resize
 * that OpenJDK's treeifyBin does instead is kept. Bulk operations run sequentially in the caller's thread whatever
 * the parallelism threshold.
 */
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int DEFAULT_CAPACITY = 16;
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;
    private static final float LOAD_FACTOR = 0.75f;
    static final int TREEIFY_THRESHOLD = 8;
    static final int MIN_TREEIFY_CAPACITY = 64;

    static final int MOVED = -1; // hash for forwarding nodes
    static final int TREEBIN = -2; // unused here: bins are never treeified
    static final int RESERVED = -3; // hash for transient reservations
    static final int HASH_BITS = 0x7fffffff;

    /** OpenJDK's serial form: a Segment[] and two ints kept from the pre-Java 8 implementation. */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("segments", Segment[].class),
        new ObjectStreamField("segmentMask", Integer.TYPE),
        new ObjectStreamField("segmentShift", Integer.TYPE),
    };

    // ---- nodes ----

    /** A key-value entry in a bin. As in OpenJDK, setValue is not supported on nodes handed out by bulk ops. */
    static class Node<K, V> implements Map.Entry<K, V> {
        final int hash;
        final K key;
        volatile V val;
        volatile Node<K, V> next;

        Node(int hash, K key, V val) {
            this.hash = hash;
            this.key = key;
            this.val = val;
        }

        Node(int hash, K key, V val, Node<K, V> next) {
            this(hash, key, val);
            this.next = next;
        }

        public final K getKey() {
            return key;
        }

        public final V getValue() {
            return val;
        }

        public final int hashCode() {
            return key.hashCode() ^ val.hashCode();
        }

        public final String toString() {
            return key + "=" + val;
        }

        public final V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        public final boolean equals(Object o) {
            Object k;
            Object v;
            Object u;
            Map.Entry<?, ?> e;
            return ((o instanceof Map.Entry) && (k = (e = (Map.Entry<?, ?>) o).getKey()) != null
                    && (v = e.getValue()) != null && (k == key || k.equals(key)) && (v == (u = val) || v.equals(u)));
        }

        Node<K, V> find(int h, Object k) {
            Node<K, V> e = this;
            if (k != null) {
                do {
                    K ek;
                    if (e.hash == h && ((ek = e.key) == k || (ek != null && k.equals(ek)))) {
                        return e;
                    }
                } while ((e = e.next) != null);
            }
            return null;
        }
    }

    /** Placed at the head of each old-table bin once the bin has moved to nextTable during a resize. */
    static final class ForwardingNode<K, V> extends Node<K, V> {
        final Node<K, V>[] nextTable;

        ForwardingNode(Node<K, V>[] tab) {
            super(MOVED, null, null);
            this.nextTable = tab;
        }

        Node<K, V> find(int h, Object k) {
            outer:
            for (Node<K, V>[] tab = nextTable;;) {
                Node<K, V> e;
                int n;
                if (k == null || tab == null || (n = tab.length) == 0 || (e = tab[(n - 1) & h]) == null) {
                    return null;
                }
                for (;;) {
                    int eh;
                    K ek;
                    if ((eh = e.hash) == h && ((ek = e.key) == k || (ek != null && k.equals(ek)))) {
                        return e;
                    }
                    if (eh < 0) {
                        if (e instanceof ForwardingNode) {
                            tab = ((ForwardingNode<K, V>) e).nextTable;
                            continue outer;
                        }
                        return e.find(h, k);
                    }
                    if ((e = e.next) == null) {
                        return null;
                    }
                }
            }
        }
    }

    /** Holds an empty bin while computeIfAbsent/compute run their function, so recursive updates are detected. */
    static final class ReservationNode<K, V> extends Node<K, V> {
        ReservationNode() {
            super(RESERVED, null, null);
        }

        Node<K, V> find(int h, Object k) {
            return null;
        }
    }

    static final int spread(int h) {
        return (h ^ (h >>> 16)) & HASH_BITS;
    }

    private static final int tableSizeFor(int c) {
        int n = -1 >>> Integer.numberOfLeadingZeros(c - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    // ---- fields ----

    transient volatile Node<K, V>[] table;
    private transient volatile Node<K, V>[] nextTable;
    private transient volatile long baseCount;
    /**
     * 0 or the initial table size before the table exists, -1 while it is being created, else the element count at
     * which the next resize happens. Updates hold {@link #lock}, so the transient negative states of OpenJDK's
     * concurrent resizes are never visible to them.
     */
    private transient volatile int sizeCtl;
    /** Serializes every update; never the map itself, so callers synchronizing on the map cannot stall it. */
    private transient Object lock = new Object();

    private transient KeySetView<K, V> keySet;
    private transient ValuesView<K, V> values;
    private transient EntrySetView<K, V> entrySet;

    // ---- constructors ----

    public ConcurrentHashMap() {
    }

    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, LOAD_FACTOR, 1);
    }

    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        this.sizeCtl = DEFAULT_CAPACITY;
        putAll(m);
    }

    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, 1);
    }

    public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0) {
            throw new IllegalArgumentException();
        }
        if (initialCapacity < concurrencyLevel) {
            initialCapacity = concurrencyLevel;
        }
        long size = (long) (1.0 + (long) initialCapacity / loadFactor);
        this.sizeCtl = (size >= (long) MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : tableSizeFor((int) size);
    }

    // ---- Map ----

    public int size() {
        long n = sumCount();
        return ((n < 0L) ? 0 : (n > (long) Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) n);
    }

    public boolean isEmpty() {
        return sumCount() <= 0L;
    }

    public V get(Object key) {
        Node<K, V>[] tab;
        Node<K, V> e;
        Node<K, V> p;
        int n;
        int eh;
        K ek;
        int h = spread(key.hashCode());
        if ((tab = table) != null && (n = tab.length) > 0 && (e = tab[(n - 1) & h]) != null) {
            if ((eh = e.hash) == h) {
                if ((ek = e.key) == key || (ek != null && key.equals(ek))) {
                    return e.val;
                }
            } else if (eh < 0) {
                return (p = e.find(h, key)) != null ? p.val : null;
            }
            while ((e = e.next) != null) {
                if (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                    return e.val;
                }
            }
        }
        return null;
    }

    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    public boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        Node<K, V>[] t;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                V v;
                if ((v = p.val) == value || (v != null && value.equals(v))) {
                    return true;
                }
            }
        }
        return false;
    }

    public V put(K key, V value) {
        return putVal(key, value, false);
    }

    final V putVal(K key, V value, boolean onlyIfAbsent) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        int hash = spread(key.hashCode());
        synchronized (lock) {
            int binCount = 0;
            for (Node<K, V>[] tab = table;;) {
                Node<K, V> f;
                int n;
                int i;
                int fh;
                K fk;
                V fv;
                if (tab == null || (n = tab.length) == 0) {
                    tab = initTable();
                } else if ((f = tab[i = (n - 1) & hash]) == null) {
                    tab[i] = new Node<K, V>(hash, key, value);
                    break;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                } else if (onlyIfAbsent && fh == hash && ((fk = f.key) == key || (fk != null && key.equals(fk)))
                        && (fv = f.val) != null) {
                    return fv;
                } else {
                    V oldVal = null;
                    if (tab[i] == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K, V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == hash && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent) {
                                        e.val = value;
                                    }
                                    break;
                                }
                                Node<K, V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K, V>(hash, key, value);
                                    break;
                                }
                            }
                        } else if (f instanceof ReservationNode) {
                            throw new IllegalStateException("Recursive update");
                        }
                    }
                    if (binCount != 0) {
                        if (binCount >= TREEIFY_THRESHOLD) {
                            treeifyBin(tab, i);
                        }
                        if (oldVal != null) {
                            return oldVal;
                        }
                        break;
                    }
                }
            }
            addCount(1L, binCount);
            return null;
        }
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        synchronized (lock) {
            tryPresize(m.size());
        }
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            putVal(e.getKey(), e.getValue(), false);
        }
    }

    public V remove(Object key) {
        return replaceNode(key, null, null);
    }

    /**
     * Replaces the value of key with value (or removes the entry when value is null) if cv is null or equals the
     * current value. Returns the old value, or null when nothing changed.
     */
    final V replaceNode(Object key, V value, Object cv) {
        int hash = spread(key.hashCode());
        synchronized (lock) {
            for (Node<K, V>[] tab = table;;) {
                Node<K, V> f;
                int n;
                int i;
                int fh;
                if (tab == null || (n = tab.length) == 0 || (f = tab[i = (n - 1) & hash]) == null) {
                    break;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                } else {
                    V oldVal = null;
                    boolean validated = false;
                    if (tab[i] == f) {
                        if (fh >= 0) {
                            validated = true;
                            for (Node<K, V> e = f, pred = null;;) {
                                K ek;
                                if (e.hash == hash && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                    V ev = e.val;
                                    if (cv == null || cv == ev || (ev != null && cv.equals(ev))) {
                                        oldVal = ev;
                                        if (value != null) {
                                            e.val = value;
                                        } else if (pred != null) {
                                            pred.next = e.next;
                                        } else {
                                            tab[i] = e.next;
                                        }
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null) {
                                    break;
                                }
                            }
                        } else if (f instanceof ReservationNode) {
                            throw new IllegalStateException("Recursive update");
                        }
                    }
                    if (validated) {
                        if (oldVal != null) {
                            if (value == null) {
                                addCount(-1L, -1);
                            }
                            return oldVal;
                        }
                        break;
                    }
                }
            }
            return null;
        }
    }

    public void clear() {
        synchronized (lock) {
            long delta = 0L;
            int i = 0;
            Node<K, V>[] tab = table;
            while (tab != null && i < tab.length) {
                int fh;
                Node<K, V> f = tab[i];
                if (f == null) {
                    ++i;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                    i = 0;
                } else {
                    Node<K, V> p = (fh >= 0 ? f : null);
                    while (p != null) {
                        --delta;
                        p = p.next;
                    }
                    tab[i++] = null;
                }
            }
            if (delta != 0L) {
                addCount(delta, -1);
            }
        }
    }

    /** Returns KeySetView like OpenJDK; javac adds the {@code Set keySet()} bridge that android.jar code calls. */
    public KeySetView<K, V> keySet() {
        KeySetView<K, V> ks;
        if ((ks = keySet) != null) {
            return ks;
        }
        return keySet = new KeySetView<K, V>(this, null);
    }

    public Collection<V> values() {
        ValuesView<K, V> vs;
        if ((vs = values) != null) {
            return vs;
        }
        return values = new ValuesView<K, V>(this);
    }

    public Set<Map.Entry<K, V>> entrySet() {
        EntrySetView<K, V> es;
        if ((es = entrySet) != null) {
            return es;
        }
        return entrySet = new EntrySetView<K, V>(this);
    }

    public int hashCode() {
        int h = 0;
        Node<K, V>[] t;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                h += p.key.hashCode() ^ p.val.hashCode();
            }
        }
        return h;
    }

    public String toString() {
        Node<K, V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        Traverser<K, V> it = new Traverser<K, V>(t, f, 0, f);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        Node<K, V> p;
        if ((p = it.advance()) != null) {
            for (;;) {
                K k = p.key;
                V v = p.val;
                sb.append(k == this ? "(this Map)" : k);
                sb.append('=');
                sb.append(v == this ? "(this Map)" : v);
                if ((p = it.advance()) == null) {
                    break;
                }
                sb.append(',').append(' ');
            }
        }
        return sb.append('}').toString();
    }

    public boolean equals(Object o) {
        if (o != this) {
            if (!(o instanceof Map)) {
                return false;
            }
            Map<?, ?> m = (Map<?, ?>) o;
            Node<K, V>[] t;
            int f = (t = table) == null ? 0 : t.length;
            Traverser<K, V> it = new Traverser<K, V>(t, f, 0, f);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                V val = p.val;
                Object v = m.get(p.key);
                if (v == null || (v != val && !v.equals(val))) {
                    return false;
                }
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object mk;
                Object mv;
                Object v;
                if ((mk = e.getKey()) == null || (mv = e.getValue()) == null || (v = get(mk)) == null
                        || (mv != v && !mv.equals(v))) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- ConcurrentMap ----

    public V putIfAbsent(K key, V value) {
        return putVal(key, value, true);
    }

    public boolean remove(Object key, Object value) {
        if (key == null) {
            throw new NullPointerException();
        }
        return value != null && replaceNode(key, null, value) != null;
    }

    public boolean replace(K key, V oldValue, V newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        return replaceNode(key, newValue, oldValue) != null;
    }

    public V replace(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        return replaceNode(key, value, null);
    }

    public V getOrDefault(Object key, V defaultValue) {
        V v;
        return (v = get(key)) == null ? defaultValue : v;
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Node<K, V>[] t;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                action.accept(p.key, p.val);
            }
        }
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        Node<K, V>[] t;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                V oldValue = p.val;
                for (K key = p.key;;) {
                    V newValue = function.apply(key, oldValue);
                    if (newValue == null) {
                        throw new NullPointerException();
                    }
                    if (replaceNode(key, newValue, oldValue) != null || (oldValue = get(key)) == null) {
                        break;
                    }
                }
            }
        }
    }

    /** removeIf for the entry set: removes entries that still have the value the predicate saw. */
    boolean removeEntryIf(Predicate<? super Map.Entry<K, V>> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        Node<K, V>[] t;
        boolean removed = false;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                K k = p.key;
                V v = p.val;
                Map.Entry<K, V> e = new AbstractMap.SimpleImmutableEntry<K, V>(k, v);
                if (function.test(e) && replaceNode(k, null, v) != null) {
                    removed = true;
                }
            }
        }
        return removed;
    }

    /** removeIf for the values view. */
    boolean removeValueIf(Predicate<? super V> function) {
        if (function == null) {
            throw new NullPointerException();
        }
        Node<K, V>[] t;
        boolean removed = false;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                K k = p.key;
                V v = p.val;
                if (function.test(v) && replaceNode(k, null, v) != null) {
                    removed = true;
                }
            }
        }
        return removed;
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key.hashCode());
        synchronized (lock) {
            V val = null;
            int binCount = 0;
            for (Node<K, V>[] tab = table;;) {
                Node<K, V> f;
                int n;
                int i;
                int fh;
                K fk;
                V fv;
                if (tab == null || (n = tab.length) == 0) {
                    tab = initTable();
                } else if ((f = tab[i = (n - 1) & h]) == null) {
                    Node<K, V> r = new ReservationNode<K, V>();
                    tab[i] = r;
                    binCount = 1;
                    Node<K, V> node = null;
                    try {
                        if ((val = mappingFunction.apply(key)) != null) {
                            node = new Node<K, V>(h, key, val);
                        }
                    } finally {
                        tab[i] = node;
                    }
                    break;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                } else if (fh == h && ((fk = f.key) == key || fk != null && key.equals(fk)) && (fv = f.val) != null) {
                    return fv;
                } else {
                    boolean added = false;
                    if (tab[i] == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K, V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                    val = e.val;
                                    break;
                                }
                                Node<K, V> pred = e;
                                if ((e = e.next) == null) {
                                    if ((val = mappingFunction.apply(key)) != null) {
                                        if (pred.next != null) {
                                            throw new IllegalStateException("Recursive update");
                                        }
                                        added = true;
                                        pred.next = new Node<K, V>(h, key, val);
                                    }
                                    break;
                                }
                            }
                        } else if (f instanceof ReservationNode) {
                            throw new IllegalStateException("Recursive update");
                        }
                    }
                    if (binCount != 0) {
                        if (binCount >= TREEIFY_THRESHOLD) {
                            treeifyBin(tab, i);
                        }
                        if (!added) {
                            return val;
                        }
                        break;
                    }
                }
            }
            if (val != null) {
                addCount(1L, binCount);
            }
            return val;
        }
    }

    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key.hashCode());
        synchronized (lock) {
            V val = null;
            int delta = 0;
            int binCount = 0;
            for (Node<K, V>[] tab = table;;) {
                Node<K, V> f;
                int n;
                int i;
                int fh;
                if (tab == null || (n = tab.length) == 0) {
                    tab = initTable();
                } else if ((f = tab[i = (n - 1) & h]) == null) {
                    break;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                } else {
                    if (tab[i] == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K, V> e = f, pred = null;; ++binCount) {
                                K ek;
                                if (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                    val = remappingFunction.apply(key, e.val);
                                    if (val != null) {
                                        e.val = val;
                                    } else {
                                        delta = -1;
                                        Node<K, V> en = e.next;
                                        if (pred != null) {
                                            pred.next = en;
                                        } else {
                                            tab[i] = en;
                                        }
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null) {
                                    break;
                                }
                            }
                        } else if (f instanceof ReservationNode) {
                            throw new IllegalStateException("Recursive update");
                        }
                    }
                    if (binCount != 0) {
                        break;
                    }
                }
            }
            if (delta != 0) {
                addCount((long) delta, binCount);
            }
            return val;
        }
    }

    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (key == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key.hashCode());
        synchronized (lock) {
            V val = null;
            int delta = 0;
            int binCount = 0;
            for (Node<K, V>[] tab = table;;) {
                Node<K, V> f;
                int n;
                int i;
                int fh;
                if (tab == null || (n = tab.length) == 0) {
                    tab = initTable();
                } else if ((f = tab[i = (n - 1) & h]) == null) {
                    Node<K, V> r = new ReservationNode<K, V>();
                    tab[i] = r;
                    binCount = 1;
                    Node<K, V> node = null;
                    try {
                        if ((val = remappingFunction.apply(key, null)) != null) {
                            delta = 1;
                            node = new Node<K, V>(h, key, val);
                        }
                    } finally {
                        tab[i] = node;
                    }
                    break;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                } else {
                    if (tab[i] == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K, V> e = f, pred = null;; ++binCount) {
                                K ek;
                                if (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                    val = remappingFunction.apply(key, e.val);
                                    if (val != null) {
                                        e.val = val;
                                    } else {
                                        delta = -1;
                                        Node<K, V> en = e.next;
                                        if (pred != null) {
                                            pred.next = en;
                                        } else {
                                            tab[i] = en;
                                        }
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null) {
                                    val = remappingFunction.apply(key, null);
                                    if (val != null) {
                                        if (pred.next != null) {
                                            throw new IllegalStateException("Recursive update");
                                        }
                                        delta = 1;
                                        pred.next = new Node<K, V>(h, key, val);
                                    }
                                    break;
                                }
                            }
                        } else if (f instanceof ReservationNode) {
                            throw new IllegalStateException("Recursive update");
                        }
                    }
                    if (binCount != 0) {
                        if (binCount >= TREEIFY_THRESHOLD) {
                            treeifyBin(tab, i);
                        }
                        break;
                    }
                }
            }
            if (delta != 0) {
                addCount((long) delta, binCount);
            }
            return val;
        }
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (key == null || value == null || remappingFunction == null) {
            throw new NullPointerException();
        }
        int h = spread(key.hashCode());
        synchronized (lock) {
            V val = null;
            int delta = 0;
            int binCount = 0;
            for (Node<K, V>[] tab = table;;) {
                Node<K, V> f;
                int n;
                int i;
                int fh;
                if (tab == null || (n = tab.length) == 0) {
                    tab = initTable();
                } else if ((f = tab[i = (n - 1) & h]) == null) {
                    tab[i] = new Node<K, V>(h, key, value);
                    delta = 1;
                    val = value;
                    break;
                } else if ((fh = f.hash) == MOVED) {
                    tab = ((ForwardingNode<K, V>) f).nextTable;
                } else {
                    if (tab[i] == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K, V> e = f, pred = null;; ++binCount) {
                                K ek;
                                if (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                    val = remappingFunction.apply(e.val, value);
                                    if (val != null) {
                                        e.val = val;
                                    } else {
                                        delta = -1;
                                        Node<K, V> en = e.next;
                                        if (pred != null) {
                                            pred.next = en;
                                        } else {
                                            tab[i] = en;
                                        }
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null) {
                                    delta = 1;
                                    val = value;
                                    pred.next = new Node<K, V>(h, key, val);
                                    break;
                                }
                            }
                        } else if (f instanceof ReservationNode) {
                            throw new IllegalStateException("Recursive update");
                        }
                    }
                    if (binCount != 0) {
                        if (binCount >= TREEIFY_THRESHOLD) {
                            treeifyBin(tab, i);
                        }
                        break;
                    }
                }
            }
            if (delta != 0) {
                addCount((long) delta, binCount);
            }
            return val;
        }
    }

    // ---- Hashtable-style legacy methods ----

    public boolean contains(Object value) {
        return containsValue(value);
    }

    public Enumeration<K> keys() {
        Node<K, V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        return new KeyIterator<K, V>(t, f, 0, f, this);
    }

    public Enumeration<V> elements() {
        Node<K, V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        return new ValueIterator<K, V>(t, f, 0, f, this);
    }

    // ---- ConcurrentHashMap-only methods ----

    public long mappingCount() {
        long n = sumCount();
        return (n < 0L) ? 0L : n;
    }

    public static <K> KeySetView<K, Boolean> newKeySet() {
        return new KeySetView<K, Boolean>(new ConcurrentHashMap<K, Boolean>(), Boolean.TRUE);
    }

    public static <K> KeySetView<K, Boolean> newKeySet(int initialCapacity) {
        return new KeySetView<K, Boolean>(new ConcurrentHashMap<K, Boolean>(initialCapacity), Boolean.TRUE);
    }

    public KeySetView<K, V> keySet(V mappedValue) {
        if (mappedValue == null) {
            throw new NullPointerException();
        }
        return new KeySetView<K, V>(this, mappedValue);
    }

    // ---- table management (callers hold lock) ----

    private Node<K, V>[] initTable() {
        Node<K, V>[] tab = table;
        if (tab == null || tab.length == 0) {
            int sc = sizeCtl;
            int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
            @SuppressWarnings("unchecked")
            Node<K, V>[] nt = (Node<K, V>[]) new Node<?, ?>[n];
            table = tab = nt;
            sizeCtl = n - (n >>> 2);
        }
        return tab;
    }

    final long sumCount() {
        return baseCount;
    }

    /** Adds x to the count; when check is non-negative, resizes while the count has reached sizeCtl. */
    private void addCount(long x, int check) {
        long s = baseCount = baseCount + x;
        if (check >= 0) {
            Node<K, V>[] tab;
            int sc;
            while (s >= (long) (sc = sizeCtl) && (tab = table) != null && tab.length < MAXIMUM_CAPACITY) {
                transfer(tab);
                s = sumCount();
            }
        }
    }

    /** Grows the table to hold size elements; also what treeifyBin does for tables under MIN_TREEIFY_CAPACITY. */
    private void tryPresize(int size) {
        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY : tableSizeFor(size + (size >>> 1) + 1);
        int sc;
        while ((sc = sizeCtl) >= 0) {
            Node<K, V>[] tab = table;
            int n;
            if (tab == null || (n = tab.length) == 0) {
                n = (sc > c) ? sc : c;
                @SuppressWarnings("unchecked")
                Node<K, V>[] nt = (Node<K, V>[]) new Node<?, ?>[n];
                table = nt;
                sizeCtl = n - (n >>> 2);
            } else if (c <= sc || n >= MAXIMUM_CAPACITY) {
                break;
            } else {
                transfer(tab);
            }
        }
    }

    /**
     * Moves every bin to a table twice the size, from the last bin down like OpenJDK. Each bin splits by the new hash
     * bit: the longest tail run whose nodes all go the same way is reused, and the nodes before it are copied, each
     * pushed onto the front of its list. The old bin then gets a ForwardingNode, so readers still on the old table
     * find the moved entries.
     */
    private void transfer(Node<K, V>[] tab) {
        int n = tab.length;
        @SuppressWarnings("unchecked")
        Node<K, V>[] nextTab = (Node<K, V>[]) new Node<?, ?>[n << 1];
        nextTable = nextTab;
        ForwardingNode<K, V> fwd = new ForwardingNode<K, V>(nextTab);
        for (int i = n - 1; i >= 0; i--) {
            Node<K, V> f = tab[i];
            if (f == null) {
                tab[i] = fwd;
                continue;
            }
            int fh = f.hash;
            if (fh >= 0) {
                Node<K, V> ln;
                Node<K, V> hn;
                int runBit = fh & n;
                Node<K, V> lastRun = f;
                for (Node<K, V> p = f.next; p != null; p = p.next) {
                    int b = p.hash & n;
                    if (b != runBit) {
                        runBit = b;
                        lastRun = p;
                    }
                }
                if (runBit == 0) {
                    ln = lastRun;
                    hn = null;
                } else {
                    hn = lastRun;
                    ln = null;
                }
                for (Node<K, V> p = f; p != lastRun; p = p.next) {
                    int ph = p.hash;
                    K pk = p.key;
                    V pv = p.val;
                    if ((ph & n) == 0) {
                        ln = new Node<K, V>(ph, pk, pv, ln);
                    } else {
                        hn = new Node<K, V>(ph, pk, pv, hn);
                    }
                }
                nextTab[i] = ln;
                nextTab[i + n] = hn;
                tab[i] = fwd;
            } else if (f instanceof ReservationNode) {
                throw new IllegalStateException("Recursive update");
            }
        }
        nextTable = null;
        table = nextTab;
        sizeCtl = (n << 1) - (n >>> 1);
    }

    /** OpenJDK converts long bins to trees in big tables; small tables are resized instead, which is kept here. */
    private void treeifyBin(Node<K, V>[] tab, int index) {
        if (tab != null && tab.length < MIN_TREEIFY_CAPACITY) {
            tryPresize(tab.length << 1);
        }
    }

    // ---- traversal ----

    /** Records a table and position while a Traverser visits the table a ForwardingNode points to. */
    static final class TableStack<K, V> {
        int length;
        int index;
        Node<K, V>[] tab;
        TableStack<K, V> next;
    }

    /**
     * Visits each bin of the table it started with (a range of it for split spliterators). A ForwardingNode means the
     * bin has moved: the traverser then visits the two bins that index maps to in the next table before going on.
     * This is OpenJDK's traversal, so concurrent updates and resizes never cause errors.
     */
    static class Traverser<K, V> {
        Node<K, V>[] tab;
        Node<K, V> next;
        TableStack<K, V> stack;
        TableStack<K, V> spare;
        int index;
        int baseIndex;
        int baseLimit;
        final int baseSize;

        Traverser(Node<K, V>[] tab, int size, int index, int limit) {
            this.tab = tab;
            this.baseSize = size;
            this.baseIndex = this.index = index;
            this.baseLimit = limit;
            this.next = null;
        }

        final Node<K, V> advance() {
            Node<K, V> e;
            if ((e = next) != null) {
                e = e.next;
            }
            for (;;) {
                Node<K, V>[] t;
                int i;
                int n;
                if (e != null) {
                    return next = e;
                }
                if (baseIndex >= baseLimit || (t = tab) == null || (n = t.length) <= (i = index) || i < 0) {
                    return next = null;
                }
                if ((e = t[i]) != null && e.hash < 0) {
                    if (e instanceof ForwardingNode) {
                        tab = ((ForwardingNode<K, V>) e).nextTable;
                        e = null;
                        pushState(t, i, n);
                        continue;
                    } else {
                        e = null;
                    }
                }
                if (stack != null) {
                    recoverState(n);
                } else if ((index = i + baseSize) >= n) {
                    index = ++baseIndex;
                }
            }
        }

        private void pushState(Node<K, V>[] t, int i, int n) {
            TableStack<K, V> s = spare;
            if (s != null) {
                spare = s.next;
            } else {
                s = new TableStack<K, V>();
            }
            s.tab = t;
            s.length = n;
            s.index = i;
            s.next = stack;
            stack = s;
        }

        private void recoverState(int n) {
            TableStack<K, V> s;
            int len;
            while ((s = stack) != null && (index += (len = s.length)) >= n) {
                n = len;
                index = s.index;
                tab = s.tab;
                s.tab = null;
                TableStack<K, V> next = s.next;
                s.next = spare;
                stack = next;
                spare = s;
            }
            if (s == null && (index += baseSize) >= n) {
                index = ++baseIndex;
            }
        }
    }

    static class BaseIterator<K, V> extends Traverser<K, V> {
        final ConcurrentHashMap<K, V> map;
        Node<K, V> lastReturned;

        BaseIterator(Node<K, V>[] tab, int size, int index, int limit, ConcurrentHashMap<K, V> map) {
            super(tab, size, index, limit);
            this.map = map;
            advance();
        }

        public final boolean hasNext() {
            return next != null;
        }

        public final boolean hasMoreElements() {
            return next != null;
        }

        public final void remove() {
            Node<K, V> p;
            if ((p = lastReturned) == null) {
                throw new IllegalStateException();
            }
            lastReturned = null;
            map.replaceNode(p.key, null, null);
        }
    }

    static final class KeyIterator<K, V> extends BaseIterator<K, V> implements Iterator<K>, Enumeration<K> {
        KeyIterator(Node<K, V>[] tab, int size, int index, int limit, ConcurrentHashMap<K, V> map) {
            super(tab, size, index, limit, map);
        }

        public final K next() {
            Node<K, V> p;
            if ((p = next) == null) {
                throw new NoSuchElementException();
            }
            K k = p.key;
            lastReturned = p;
            advance();
            return k;
        }

        public final K nextElement() {
            return next();
        }
    }

    static final class ValueIterator<K, V> extends BaseIterator<K, V> implements Iterator<V>, Enumeration<V> {
        ValueIterator(Node<K, V>[] tab, int size, int index, int limit, ConcurrentHashMap<K, V> map) {
            super(tab, size, index, limit, map);
        }

        public final V next() {
            Node<K, V> p;
            if ((p = next) == null) {
                throw new NoSuchElementException();
            }
            V v = p.val;
            lastReturned = p;
            advance();
            return v;
        }

        public final V nextElement() {
            return next();
        }
    }

    static final class EntryIterator<K, V> extends BaseIterator<K, V> implements Iterator<Map.Entry<K, V>> {
        EntryIterator(Node<K, V>[] tab, int size, int index, int limit, ConcurrentHashMap<K, V> map) {
            super(tab, size, index, limit, map);
        }

        public final Map.Entry<K, V> next() {
            Node<K, V> p;
            if ((p = next) == null) {
                throw new NoSuchElementException();
            }
            K k = p.key;
            V v = p.val;
            lastReturned = p;
            advance();
            return new MapEntry<K, V>(k, v, map);
        }
    }

    /** Entry handed out by entry iterators: setValue writes through to the map. */
    static final class MapEntry<K, V> implements Map.Entry<K, V> {
        final K key;
        V val;
        final ConcurrentHashMap<K, V> map;

        MapEntry(K key, V val, ConcurrentHashMap<K, V> map) {
            this.key = key;
            this.val = val;
            this.map = map;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return val;
        }

        public int hashCode() {
            return key.hashCode() ^ val.hashCode();
        }

        public String toString() {
            return key + "=" + val;
        }

        public boolean equals(Object o) {
            Object k;
            Object v;
            Map.Entry<?, ?> e;
            return ((o instanceof Map.Entry) && (k = (e = (Map.Entry<?, ?>) o).getKey()) != null
                    && (v = e.getValue()) != null && (k == key || k.equals(key)) && (v == val || v.equals(val)));
        }

        public V setValue(V value) {
            if (value == null) {
                throw new NullPointerException();
            }
            V v = val;
            val = value;
            map.put(key, value);
            return v;
        }
    }

    static final class KeySpliterator<K, V> extends Traverser<K, V> implements Spliterator<K> {
        long est;

        KeySpliterator(Node<K, V>[] tab, int size, int index, int limit, long est) {
            super(tab, size, index, limit);
            this.est = est;
        }

        public KeySpliterator<K, V> trySplit() {
            int i;
            int f;
            int h;
            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null
                    : new KeySpliterator<K, V>(tab, baseSize, baseLimit = h, f, est >>>= 1);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            for (Node<K, V> p; (p = advance()) != null;) {
                action.accept(p.key);
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V> p;
            if ((p = advance()) == null) {
                return false;
            }
            action.accept(p.key);
            return true;
        }

        public long estimateSize() {
            return est;
        }

        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.CONCURRENT | Spliterator.NONNULL;
        }
    }

    static final class ValueSpliterator<K, V> extends Traverser<K, V> implements Spliterator<V> {
        long est;

        ValueSpliterator(Node<K, V>[] tab, int size, int index, int limit, long est) {
            super(tab, size, index, limit);
            this.est = est;
        }

        public ValueSpliterator<K, V> trySplit() {
            int i;
            int f;
            int h;
            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null
                    : new ValueSpliterator<K, V>(tab, baseSize, baseLimit = h, f, est >>>= 1);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            for (Node<K, V> p; (p = advance()) != null;) {
                action.accept(p.val);
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V> p;
            if ((p = advance()) == null) {
                return false;
            }
            action.accept(p.val);
            return true;
        }

        public long estimateSize() {
            return est;
        }

        public int characteristics() {
            return Spliterator.CONCURRENT | Spliterator.NONNULL;
        }
    }

    static final class EntrySpliterator<K, V> extends Traverser<K, V> implements Spliterator<Map.Entry<K, V>> {
        final ConcurrentHashMap<K, V> map;
        long est;

        EntrySpliterator(Node<K, V>[] tab, int size, int index, int limit, long est, ConcurrentHashMap<K, V> map) {
            super(tab, size, index, limit);
            this.map = map;
            this.est = est;
        }

        public EntrySpliterator<K, V> trySplit() {
            int i;
            int f;
            int h;
            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null
                    : new EntrySpliterator<K, V>(tab, baseSize, baseLimit = h, f, est >>>= 1, map);
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            for (Node<K, V> p; (p = advance()) != null;) {
                action.accept(new MapEntry<K, V>(p.key, p.val, map));
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V> p;
            if ((p = advance()) == null) {
                return false;
            }
            action.accept(new MapEntry<K, V>(p.key, p.val, map));
            return true;
        }

        public long estimateSize() {
            return est;
        }

        public int characteristics() {
            return Spliterator.DISTINCT | Spliterator.CONCURRENT | Spliterator.NONNULL;
        }
    }

    // ---- bulk operations (sequential; parallelismThreshold only selects parallelism in OpenJDK) ----

    private Traverser<K, V> traverser() {
        Node<K, V>[] t;
        int f = (t = table) == null ? 0 : t.length;
        return new Traverser<K, V>(t, f, 0, f);
    }

    public void forEach(long parallelismThreshold, BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            action.accept(p.key, p.val);
        }
    }

    public <U> void forEach(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p.key, p.val)) != null) {
                action.accept(u);
            }
        }
    }

    public <U> U search(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = searchFunction.apply(p.key, p.val)) != null) {
                return u;
            }
        }
        return null;
    }

    public <U> U reduce(long parallelismThreshold, BiFunction<? super K, ? super V, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        U r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p.key, p.val)) != null) {
                r = (r == null) ? u : reducer.apply(r, u);
            }
        }
        return r;
    }

    public double reduceToDouble(long parallelismThreshold, ToDoubleBiFunction<? super K, ? super V> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        double r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key, p.val));
        }
        return r;
    }

    public long reduceToLong(long parallelismThreshold, ToLongBiFunction<? super K, ? super V> transformer,
            long basis, LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        long r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsLong(r, transformer.applyAsLong(p.key, p.val));
        }
        return r;
    }

    public int reduceToInt(long parallelismThreshold, ToIntBiFunction<? super K, ? super V> transformer, int basis,
            IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        int r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsInt(r, transformer.applyAsInt(p.key, p.val));
        }
        return r;
    }

    public void forEachKey(long parallelismThreshold, Consumer<? super K> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            action.accept(p.key);
        }
    }

    public <U> void forEachKey(long parallelismThreshold, Function<? super K, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p.key)) != null) {
                action.accept(u);
            }
        }
    }

    public <U> U searchKeys(long parallelismThreshold, Function<? super K, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = searchFunction.apply(p.key)) != null) {
                return u;
            }
        }
        return null;
    }

    public K reduceKeys(long parallelismThreshold, BiFunction<? super K, ? super K, ? extends K> reducer) {
        if (reducer == null) {
            throw new NullPointerException();
        }
        K r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            K u = p.key;
            r = (r == null) ? u : u == null ? r : reducer.apply(r, u);
        }
        return r;
    }

    public <U> U reduceKeys(long parallelismThreshold, Function<? super K, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        U r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p.key)) != null) {
                r = (r == null) ? u : reducer.apply(r, u);
            }
        }
        return r;
    }

    public double reduceKeysToDouble(long parallelismThreshold, ToDoubleFunction<? super K> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        double r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key));
        }
        return r;
    }

    public long reduceKeysToLong(long parallelismThreshold, ToLongFunction<? super K> transformer, long basis,
            LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        long r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsLong(r, transformer.applyAsLong(p.key));
        }
        return r;
    }

    public int reduceKeysToInt(long parallelismThreshold, ToIntFunction<? super K> transformer, int basis,
            IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        int r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsInt(r, transformer.applyAsInt(p.key));
        }
        return r;
    }

    public void forEachValue(long parallelismThreshold, Consumer<? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            action.accept(p.val);
        }
    }

    public <U> void forEachValue(long parallelismThreshold, Function<? super V, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p.val)) != null) {
                action.accept(u);
            }
        }
    }

    public <U> U searchValues(long parallelismThreshold, Function<? super V, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = searchFunction.apply(p.val)) != null) {
                return u;
            }
        }
        return null;
    }

    public V reduceValues(long parallelismThreshold, BiFunction<? super V, ? super V, ? extends V> reducer) {
        if (reducer == null) {
            throw new NullPointerException();
        }
        V r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            V v = p.val;
            r = (r == null) ? v : reducer.apply(r, v);
        }
        return r;
    }

    public <U> U reduceValues(long parallelismThreshold, Function<? super V, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        U r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p.val)) != null) {
                r = (r == null) ? u : reducer.apply(r, u);
            }
        }
        return r;
    }

    public double reduceValuesToDouble(long parallelismThreshold, ToDoubleFunction<? super V> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        double r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.val));
        }
        return r;
    }

    public long reduceValuesToLong(long parallelismThreshold, ToLongFunction<? super V> transformer, long basis,
            LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        long r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsLong(r, transformer.applyAsLong(p.val));
        }
        return r;
    }

    public int reduceValuesToInt(long parallelismThreshold, ToIntFunction<? super V> transformer, int basis,
            IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        int r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsInt(r, transformer.applyAsInt(p.val));
        }
        return r;
    }

    public void forEachEntry(long parallelismThreshold, Consumer<? super Map.Entry<K, V>> action) {
        if (action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            action.accept(p);
        }
    }

    public <U> void forEachEntry(long parallelismThreshold, Function<Map.Entry<K, V>, ? extends U> transformer,
            Consumer<? super U> action) {
        if (transformer == null || action == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p)) != null) {
                action.accept(u);
            }
        }
    }

    public <U> U searchEntries(long parallelismThreshold, Function<Map.Entry<K, V>, ? extends U> searchFunction) {
        if (searchFunction == null) {
            throw new NullPointerException();
        }
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = searchFunction.apply(p)) != null) {
                return u;
            }
        }
        return null;
    }

    public Map.Entry<K, V> reduceEntries(long parallelismThreshold,
            BiFunction<Map.Entry<K, V>, Map.Entry<K, V>, ? extends Map.Entry<K, V>> reducer) {
        if (reducer == null) {
            throw new NullPointerException();
        }
        Map.Entry<K, V> r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = (r == null) ? p : reducer.apply(r, p);
        }
        return r;
    }

    public <U> U reduceEntries(long parallelismThreshold, Function<Map.Entry<K, V>, ? extends U> transformer,
            BiFunction<? super U, ? super U, ? extends U> reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        U r = null;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            U u;
            if ((u = transformer.apply(p)) != null) {
                r = (r == null) ? u : reducer.apply(r, u);
            }
        }
        return r;
    }

    public double reduceEntriesToDouble(long parallelismThreshold, ToDoubleFunction<Map.Entry<K, V>> transformer,
            double basis, DoubleBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        double r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsDouble(r, transformer.applyAsDouble(p));
        }
        return r;
    }

    public long reduceEntriesToLong(long parallelismThreshold, ToLongFunction<Map.Entry<K, V>> transformer,
            long basis, LongBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        long r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsLong(r, transformer.applyAsLong(p));
        }
        return r;
    }

    public int reduceEntriesToInt(long parallelismThreshold, ToIntFunction<Map.Entry<K, V>> transformer, int basis,
            IntBinaryOperator reducer) {
        if (transformer == null || reducer == null) {
            throw new NullPointerException();
        }
        int r = basis;
        Traverser<K, V> it = traverser();
        for (Node<K, V> p; (p = it.advance()) != null;) {
            r = reducer.applyAsInt(r, transformer.applyAsInt(p));
        }
        return r;
    }

    // ---- views ----

    /** Base of the key, value and entry views; every method reads or writes the backing map. */
    abstract static class CollectionView<K, V, E> implements Collection<E>, Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        final ConcurrentHashMap<K, V> map;

        CollectionView(ConcurrentHashMap<K, V> map) {
            this.map = map;
        }

        public ConcurrentHashMap<K, V> getMap() {
            return map;
        }

        public final void clear() {
            map.clear();
        }

        public final int size() {
            return map.size();
        }

        public final boolean isEmpty() {
            return map.isEmpty();
        }

        public abstract Iterator<E> iterator();

        public abstract boolean contains(Object o);

        public abstract boolean remove(Object o);

        private static final String OOME_MSG = "Required array size too large";

        public final Object[] toArray() {
            long sz = map.mappingCount();
            if (sz > MAX_ARRAY_SIZE) {
                throw new OutOfMemoryError(OOME_MSG);
            }
            int n = (int) sz;
            Object[] r = new Object[n];
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE) {
                        throw new OutOfMemoryError(OOME_MSG);
                    }
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1) {
                        n = MAX_ARRAY_SIZE;
                    } else {
                        n += (n >>> 1) + 1;
                    }
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = e;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        @SuppressWarnings("unchecked")
        public final <T> T[] toArray(T[] a) {
            long sz = map.mappingCount();
            if (sz > MAX_ARRAY_SIZE) {
                throw new OutOfMemoryError(OOME_MSG);
            }
            int m = (int) sz;
            T[] r = (a.length >= m) ? a
                    : (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), m);
            int n = r.length;
            int i = 0;
            for (E e : this) {
                if (i == n) {
                    if (n >= MAX_ARRAY_SIZE) {
                        throw new OutOfMemoryError(OOME_MSG);
                    }
                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1) {
                        n = MAX_ARRAY_SIZE;
                    } else {
                        n += (n >>> 1) + 1;
                    }
                    r = Arrays.copyOf(r, n);
                }
                r[i++] = (T) e;
            }
            if (a == r && i < n) {
                r[i] = null;
                return r;
            }
            return (i == n) ? r : Arrays.copyOf(r, i);
        }

        public final String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            Iterator<E> it = iterator();
            if (it.hasNext()) {
                for (;;) {
                    Object e = it.next();
                    sb.append(e == this ? "(this Collection)" : e);
                    if (!it.hasNext()) {
                        break;
                    }
                    sb.append(',').append(' ');
                }
            }
            return sb.append(']').toString();
        }

        public final boolean containsAll(Collection<?> c) {
            if (c != this) {
                for (Object e : c) {
                    if (e == null || !contains(e)) {
                        return false;
                    }
                }
            }
            return true;
        }

        public boolean removeAll(Collection<?> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            boolean modified = false;
            Node<K, V>[] t;
            if ((t = map.table) == null) {
                return false;
            } else if (c instanceof Set<?> && c.size() > t.length) {
                for (Iterator<?> it = iterator(); it.hasNext();) {
                    if (c.contains(it.next())) {
                        it.remove();
                        modified = true;
                    }
                }
            } else {
                for (Object e : c) {
                    modified |= remove(e);
                }
            }
            return modified;
        }

        public final boolean retainAll(Collection<?> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            boolean modified = false;
            for (Iterator<E> it = iterator(); it.hasNext();) {
                if (!c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }
    }

    /**
     * The keys of a map as a Set. With a mapped value (keySet(V), newKeySet) add and addAll insert keys mapped to
     * that value; without one they throw UnsupportedOperationException.
     */
    public static class KeySetView<K, V> extends CollectionView<K, V, K> implements Set<K>, Serializable {
        private static final long serialVersionUID = 7249069246763182397L;
        private final V value;

        KeySetView(ConcurrentHashMap<K, V> map, V value) {
            super(map);
            this.value = value;
        }

        public V getMappedValue() {
            return value;
        }

        public boolean contains(Object o) {
            return map.containsKey(o);
        }

        public boolean remove(Object o) {
            return map.remove(o) != null;
        }

        public Iterator<K> iterator() {
            Node<K, V>[] t;
            ConcurrentHashMap<K, V> m = map;
            int f = (t = m.table) == null ? 0 : t.length;
            return new KeyIterator<K, V>(t, f, 0, f, m);
        }

        public boolean add(K e) {
            V v;
            if ((v = value) == null) {
                throw new UnsupportedOperationException();
            }
            return map.putVal(e, v, true) == null;
        }

        public boolean addAll(Collection<? extends K> c) {
            boolean added = false;
            V v;
            if ((v = value) == null) {
                throw new UnsupportedOperationException();
            }
            for (K e : c) {
                if (map.putVal(e, v, true) == null) {
                    added = true;
                }
            }
            return added;
        }

        public int hashCode() {
            int h = 0;
            for (K e : this) {
                h += e.hashCode();
            }
            return h;
        }

        public boolean equals(Object o) {
            Set<?> c;
            return ((o instanceof Set) && ((c = (Set<?>) o) == this || (containsAll(c) && c.containsAll(this))));
        }

        public Spliterator<K> spliterator() {
            Node<K, V>[] t;
            ConcurrentHashMap<K, V> m = map;
            long n = m.sumCount();
            int f = (t = m.table) == null ? 0 : t.length;
            return new KeySpliterator<K, V>(t, f, 0, f, n < 0L ? 0L : n);
        }

        public void forEach(Consumer<? super K> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V>[] t;
            if ((t = map.table) != null) {
                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
                for (Node<K, V> p; (p = it.advance()) != null;) {
                    action.accept(p.key);
                }
            }
        }
    }

    static final class ValuesView<K, V> extends CollectionView<K, V, V> implements Collection<V>, Serializable {
        private static final long serialVersionUID = 2249069246763182397L;

        ValuesView(ConcurrentHashMap<K, V> map) {
            super(map);
        }

        public final boolean contains(Object o) {
            return map.containsValue(o);
        }

        public final boolean remove(Object o) {
            if (o != null) {
                for (Iterator<V> it = iterator(); it.hasNext();) {
                    if (o.equals(it.next())) {
                        it.remove();
                        return true;
                    }
                }
            }
            return false;
        }

        public final Iterator<V> iterator() {
            ConcurrentHashMap<K, V> m = map;
            Node<K, V>[] t;
            int f = (t = m.table) == null ? 0 : t.length;
            return new ValueIterator<K, V>(t, f, 0, f, m);
        }

        public final boolean add(V e) {
            throw new UnsupportedOperationException();
        }

        public final boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> c) {
            if (c == null) {
                throw new NullPointerException();
            }
            boolean modified = false;
            for (Iterator<V> it = iterator(); it.hasNext();) {
                if (c.contains(it.next())) {
                    it.remove();
                    modified = true;
                }
            }
            return modified;
        }

        public boolean removeIf(Predicate<? super V> filter) {
            return map.removeValueIf(filter);
        }

        public Spliterator<V> spliterator() {
            Node<K, V>[] t;
            ConcurrentHashMap<K, V> m = map;
            long n = m.sumCount();
            int f = (t = m.table) == null ? 0 : t.length;
            return new ValueSpliterator<K, V>(t, f, 0, f, n < 0L ? 0L : n);
        }

        public void forEach(Consumer<? super V> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V>[] t;
            if ((t = map.table) != null) {
                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
                for (Node<K, V> p; (p = it.advance()) != null;) {
                    action.accept(p.val);
                }
            }
        }
    }

    static final class EntrySetView<K, V> extends CollectionView<K, V, Map.Entry<K, V>>
            implements Set<Map.Entry<K, V>>, Serializable {
        private static final long serialVersionUID = 2249069246763182397L;

        EntrySetView(ConcurrentHashMap<K, V> map) {
            super(map);
        }

        public boolean contains(Object o) {
            Object k;
            Object v;
            Object r;
            Map.Entry<?, ?> e;
            return ((o instanceof Map.Entry) && (k = (e = (Map.Entry<?, ?>) o).getKey()) != null
                    && (r = map.get(k)) != null && (v = e.getValue()) != null && (v == r || v.equals(r)));
        }

        public boolean remove(Object o) {
            Object k;
            Object v;
            Map.Entry<?, ?> e;
            return ((o instanceof Map.Entry) && (k = (e = (Map.Entry<?, ?>) o).getKey()) != null
                    && (v = e.getValue()) != null && map.remove(k, v));
        }

        public Iterator<Map.Entry<K, V>> iterator() {
            ConcurrentHashMap<K, V> m = map;
            Node<K, V>[] t;
            int f = (t = m.table) == null ? 0 : t.length;
            return new EntryIterator<K, V>(t, f, 0, f, m);
        }

        public boolean add(Map.Entry<K, V> e) {
            return map.putVal(e.getKey(), e.getValue(), false) == null;
        }

        public boolean addAll(Collection<? extends Map.Entry<K, V>> c) {
            boolean added = false;
            for (Map.Entry<K, V> e : c) {
                if (add(e)) {
                    added = true;
                }
            }
            return added;
        }

        public boolean removeIf(Predicate<? super Map.Entry<K, V>> filter) {
            return map.removeEntryIf(filter);
        }

        public final int hashCode() {
            int h = 0;
            Node<K, V>[] t;
            if ((t = map.table) != null) {
                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
                for (Node<K, V> p; (p = it.advance()) != null;) {
                    h += p.hashCode();
                }
            }
            return h;
        }

        public final boolean equals(Object o) {
            Set<?> c;
            return ((o instanceof Set) && ((c = (Set<?>) o) == this || (containsAll(c) && c.containsAll(this))));
        }

        public Spliterator<Map.Entry<K, V>> spliterator() {
            Node<K, V>[] t;
            ConcurrentHashMap<K, V> m = map;
            long n = m.sumCount();
            int f = (t = m.table) == null ? 0 : t.length;
            return new EntrySpliterator<K, V>(t, f, 0, f, n < 0L ? 0L : n, m);
        }

        public void forEach(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V>[] t;
            if ((t = map.table) != null) {
                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
                for (Node<K, V> p; (p = it.advance()) != null;) {
                    action.accept(new MapEntry<K, V>(p.key, p.val, map));
                }
            }
        }
    }

    // ---- serialization (OpenJDK's form) ----

    /** Kept only for the serial form, as in OpenJDK: writeObject emits 16 of these, readObject ignores them. */
    static class Segment<K, V> extends ReentrantLock implements Serializable {
        private static final long serialVersionUID = 2249069246763182397L;
        final float loadFactor;

        Segment(float lf) {
            this.loadFactor = lf;
        }
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        int sshift = 0;
        int ssize = 1;
        while (ssize < DEFAULT_CONCURRENCY_LEVEL) {
            ++sshift;
            ssize <<= 1;
        }
        int segmentShift = 32 - sshift;
        int segmentMask = ssize - 1;
        @SuppressWarnings("unchecked")
        Segment<K, V>[] segments = (Segment<K, V>[]) new Segment<?, ?>[DEFAULT_CONCURRENCY_LEVEL];
        for (int i = 0; i < segments.length; ++i) {
            segments[i] = new Segment<K, V>(LOAD_FACTOR);
        }
        java.io.ObjectOutputStream.PutField streamFields = s.putFields();
        streamFields.put("segments", segments);
        streamFields.put("segmentShift", segmentShift);
        streamFields.put("segmentMask", segmentMask);
        s.writeFields();

        Node<K, V>[] t;
        if ((t = table) != null) {
            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
            for (Node<K, V> p; (p = it.advance()) != null;) {
                s.writeObject(p.key);
                s.writeObject(p.val);
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /** Rebuilds the table the way OpenJDK does, so iteration order after a round trip matches too. */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        lock = new Object();
        sizeCtl = -1;
        s.defaultReadObject();
        long size = 0L;
        Node<K, V> p = null;
        for (;;) {
            K k = (K) s.readObject();
            V v = (V) s.readObject();
            if (k != null && v != null) {
                p = new Node<K, V>(spread(k.hashCode()), k, v, p);
                ++size;
            } else {
                break;
            }
        }
        if (size == 0L) {
            sizeCtl = 0;
        } else {
            long ts = (long) (1.0 + size / LOAD_FACTOR);
            int n = (ts >= (long) MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : tableSizeFor((int) ts);
            Node<K, V>[] tab = (Node<K, V>[]) new Node<?, ?>[n];
            int mask = n - 1;
            long added = 0L;
            while (p != null) {
                boolean insertAtFront;
                Node<K, V> next = p.next;
                Node<K, V> first;
                int h = p.hash;
                int j = h & mask;
                if ((first = tab[j]) == null) {
                    insertAtFront = true;
                } else {
                    K k = p.key;
                    insertAtFront = true;
                    K qk;
                    for (Node<K, V> q = first; q != null; q = q.next) {
                        if (q.hash == h && ((qk = q.key) == k || (qk != null && k.equals(qk)))) {
                            insertAtFront = false;
                            break;
                        }
                    }
                }
                if (insertAtFront) {
                    ++added;
                    p.next = first;
                    tab[j] = p;
                }
                p = next;
            }
            table = tab;
            sizeCtl = n - (n >>> 2);
            baseCount = added;
        }
    }
}
