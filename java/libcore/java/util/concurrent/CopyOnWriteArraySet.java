package java.util.concurrent;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;

public class CopyOnWriteArraySet<E> extends AbstractSet<E> implements java.io.Serializable {
    private static final long serialVersionUID = 5457747651344034263L;

    private final CopyOnWriteArrayList<E> al;

    public CopyOnWriteArraySet() {
        al = new CopyOnWriteArrayList<E>();
    }

    public CopyOnWriteArraySet(Collection<? extends E> c) {
        al = new CopyOnWriteArrayList<E>();
        al.addAllAbsent(c);
    }

    public int size() {
        return al.size();
    }

    public boolean contains(Object o) {
        return al.contains(o);
    }

    public boolean add(E e) {
        return al.addIfAbsent(e);
    }

    public boolean remove(Object o) {
        return al.remove(o);
    }

    public void clear() {
        al.clear();
    }

    public Iterator<E> iterator() {
        return al.iterator();
    }
}
