package android.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ArraySet<E> implements Collection<E>, Set<E> {
    private final LinkedHashSet<E> mSet;
    private ArrayList<E> mCache;

    public ArraySet() { mSet = new LinkedHashSet<>(); }
    public ArraySet(int capacity) { mSet = new LinkedHashSet<>(Math.max(capacity, 1)); }
    public ArraySet(ArraySet<E> set) { mSet = new LinkedHashSet<>(set.mSet); }
    public ArraySet(Collection<? extends E> set) { mSet = new LinkedHashSet<>(set); }

    private ArrayList<E> list() {
        if (mCache == null) mCache = new ArrayList<>(mSet);
        return mCache;
    }

    public void clear() { mSet.clear(); mCache = null; }
    public boolean contains(Object key) { return mSet.contains(key); }
    public int indexOf(Object key) { return list().indexOf(key); }
    public E valueAt(int index) { return list().get(index); }
    public boolean isEmpty() { return mSet.isEmpty(); }
    public boolean add(E value) { mCache = null; return mSet.add(value); }
    public void append(E value) { add(value); }
    public void addAll(ArraySet<? extends E> array) { mCache = null; mSet.addAll(array.mSet); }
    public boolean remove(Object object) { mCache = null; return mSet.remove(object); }
    public E removeAt(int index) { E v = valueAt(index); remove(v); return v; }
    public boolean removeAll(ArraySet<? extends E> array) { mCache = null; return mSet.removeAll(array.mSet); }
    public int size() { return mSet.size(); }
    public Object[] toArray() { return mSet.toArray(); }
    public <T> T[] toArray(T[] array) { return mSet.toArray(array); }
    public boolean equals(Object object) { return object instanceof Set && mSet.equals(object); }
    public int hashCode() { return mSet.hashCode(); }
    public String toString() { return mSet.toString(); }
    public Iterator<E> iterator() { mCache = null; return mSet.iterator(); }
    public boolean containsAll(Collection<?> collection) { return mSet.containsAll(collection); }
    public boolean addAll(Collection<? extends E> collection) { mCache = null; return mSet.addAll(collection); }
    public boolean removeAll(Collection<?> collection) { mCache = null; return mSet.removeAll(collection); }
    public boolean retainAll(Collection<?> collection) { mCache = null; return mSet.retainAll(collection); }
}
