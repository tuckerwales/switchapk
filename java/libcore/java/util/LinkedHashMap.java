package java.util;

public class LinkedHashMap<K, V> extends HashMap<K, V> implements Map<K, V> {
    static class Entry<K, V> extends HashMap.Node<K, V> {
        Entry<K, V> before, after;

        Entry(int hash, K key, V value, Node<K, V> next) {
            super(hash, key, value, next);
        }
    }

    transient Entry<K, V> head;
    transient Entry<K, V> tail;
    final boolean accessOrder;

    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    public LinkedHashMap() {
        super();
        accessOrder = false;
    }

    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super();
        accessOrder = false;
        putAll(m);
    }

    public LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }

    Node<K, V> newNode(int hash, K key, V value, Node<K, V> e) {
        Entry<K, V> p = new Entry<K, V>(hash, key, value, e);
        Entry<K, V> last = tail;
        tail = p;
        if (last == null) {
            head = p;
        } else {
            p.before = last;
            last.after = p;
        }
        return p;
    }

    void afterNodeRemoval(Node<K, V> e) {
        Entry<K, V> p = (Entry<K, V>) e, b = p.before, a = p.after;
        p.before = p.after = null;
        if (b == null) {
            head = a;
        } else {
            b.after = a;
        }
        if (a == null) {
            tail = b;
        } else {
            a.before = b;
        }
    }

    void afterNodeInsertion(boolean evict) {
        Entry<K, V> first;
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false);
        }
    }

    void afterNodeAccess(Node<K, V> e) {
        Entry<K, V> last;
        if (accessOrder && (last = tail) != e) {
            Entry<K, V> p = (Entry<K, V>) e, b = p.before, a = p.after;
            p.after = null;
            if (b == null) {
                head = a;
            } else {
                b.after = a;
            }
            if (a != null) {
                a.before = b;
            } else {
                last = b;
            }
            if (last == null) {
                head = p;
            } else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        }
    }

    void clearHook() {
        head = tail = null;
    }

    public boolean containsValue(Object value) {
        for (Entry<K, V> e = head; e != null; e = e.after) {
            if (Objects.equals(value, e.value)) {
                return true;
            }
        }
        return false;
    }

    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return false;
    }

    Iterator<Node<K, V>> nodeIterator() {
        return new Iterator<Node<K, V>>() {
            Entry<K, V> next = head;
            Entry<K, V> current;
            int expectedModCount = modCount;

            public boolean hasNext() {
                return next != null;
            }

            public Node<K, V> next() {
                Entry<K, V> e = next;
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException();
                }
                if (e == null) {
                    throw new NoSuchElementException();
                }
                current = e;
                next = e.after;
                return e;
            }

            public void remove() {
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
        };
    }

    public Map.Entry<K, V> eldest() {
        return head;
    }
}
