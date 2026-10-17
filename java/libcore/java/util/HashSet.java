package java.util;

public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = -5024744406713321676L;
    static final Object PRESENT = new Object();
    transient HashMap<E, Object> map;

    /** Java's serial form: the backing map's capacity and load factor, the size, then the elements. */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeInt(map.capacity());
        s.writeFloat(map.loadFactor());
        s.writeInt(map.size());
        for (E e : map.keySet()) {
            s.writeObject(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        int capacity = s.readInt();
        if (capacity < 0) {
            throw new java.io.InvalidObjectException("Illegal capacity: " + capacity);
        }
        float loadFactor = s.readFloat();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new java.io.InvalidObjectException("Illegal load factor: " + loadFactor);
        }
        int size = s.readInt();
        if (size < 0) {
            throw new java.io.InvalidObjectException("Illegal size: " + size);
        }
        capacity = (int) Math.min(size * Math.min(1 / loadFactor, 4.0f), HashMap.MAXIMUM_CAPACITY);
        map = (this instanceof LinkedHashSet) ? new LinkedHashMap<E, Object>(capacity, loadFactor)
                : new HashMap<E, Object>(capacity, loadFactor);
        for (int i = 0; i < size; i++) {
            map.put((E) s.readObject(), PRESENT);
        }
    }

    public HashSet() {
        map = new HashMap<E, Object>();
    }

    public HashSet(Collection<? extends E> c) {
        map = new HashMap<E, Object>(Math.max((int) (c.size() / .75f) + 1, 16));
        addAll(c);
    }

    public HashSet(int initialCapacity, float loadFactor) {
        map = new HashMap<E, Object>(initialCapacity, loadFactor);
    }

    public HashSet(int initialCapacity) {
        map = new HashMap<E, Object>(initialCapacity);
    }

    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<E, Object>(initialCapacity, loadFactor);
    }

    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    public boolean add(E e) {
        return map.put(e, PRESENT) == null;
    }

    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    public void clear() {
        map.clear();
    }

    @SuppressWarnings("unchecked")
    public Object clone() {
        try {
            HashSet<E> newSet = (HashSet<E>) super.clone();
            newSet.map = (HashMap<E, Object>) map.clone();
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
