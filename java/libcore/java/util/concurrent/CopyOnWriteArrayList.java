package java.util.concurrent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

public class CopyOnWriteArrayList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private volatile Object[] array;

    public CopyOnWriteArrayList() {
        array = new Object[0];
    }

    public CopyOnWriteArrayList(Collection<? extends E> c) {
        array = c.toArray();
    }

    public CopyOnWriteArrayList(E[] toCopyIn) {
        array = Arrays.copyOf(toCopyIn, toCopyIn.length, Object[].class);
    }

    private ArrayList<E> snapshot() {
        ArrayList<E> l = new ArrayList<E>(array.length);
        for (Object o : array) {
            @SuppressWarnings("unchecked")
            E e = (E) o;
            l.add(e);
        }
        return l;
    }

    public int size() {
        return array.length;
    }

    public boolean isEmpty() {
        return array.length == 0;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public int indexOf(Object o) {
        Object[] a = array;
        for (int i = 0; i < a.length; i++) {
            if (o == null ? a[i] == null : o.equals(a[i])) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        Object[] a = array;
        for (int i = a.length - 1; i >= 0; i--) {
            if (o == null ? a[i] == null : o.equals(a[i])) {
                return i;
            }
        }
        return -1;
    }

    public Object[] toArray() {
        return array.clone();
    }

    public <T> T[] toArray(T[] a) {
        return snapshot().toArray(a);
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        return (E) array[index];
    }

    public synchronized E set(int index, E element) {
        Object[] a = array.clone();
        @SuppressWarnings("unchecked")
        E old = (E) a[index];
        a[index] = element;
        array = a;
        return old;
    }

    public synchronized boolean add(E e) {
        Object[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = e;
        array = a;
        return true;
    }

    public synchronized void add(int index, E element) {
        ArrayList<E> l = snapshot();
        l.add(index, element);
        array = l.toArray();
    }

    public synchronized E remove(int index) {
        ArrayList<E> l = snapshot();
        E e = l.remove(index);
        array = l.toArray();
        return e;
    }

    public synchronized boolean remove(Object o) {
        ArrayList<E> l = snapshot();
        boolean r = l.remove(o);
        if (r) {
            array = l.toArray();
        }
        return r;
    }

    public synchronized boolean addIfAbsent(E e) {
        if (contains(e)) {
            return false;
        }
        return add(e);
    }

    public boolean containsAll(Collection<?> c) {
        return snapshot().containsAll(c);
    }

    public synchronized boolean addAll(Collection<? extends E> c) {
        ArrayList<E> l = snapshot();
        boolean r = l.addAll(c);
        array = l.toArray();
        return r;
    }

    public synchronized int addAllAbsent(Collection<? extends E> c) {
        int n = 0;
        for (E e : c) {
            if (addIfAbsent(e)) {
                n++;
            }
        }
        return n;
    }

    public synchronized boolean addAll(int index, Collection<? extends E> c) {
        ArrayList<E> l = snapshot();
        boolean r = l.addAll(index, c);
        array = l.toArray();
        return r;
    }

    public synchronized boolean removeAll(Collection<?> c) {
        ArrayList<E> l = snapshot();
        boolean r = l.removeAll(c);
        array = l.toArray();
        return r;
    }

    public synchronized boolean retainAll(Collection<?> c) {
        ArrayList<E> l = snapshot();
        boolean r = l.retainAll(c);
        array = l.toArray();
        return r;
    }

    public synchronized boolean removeIf(java.util.function.Predicate<? super E> filter) {
        ArrayList<E> l = snapshot();
        boolean r = l.removeIf(filter);
        array = l.toArray();
        return r;
    }

    public synchronized void clear() {
        array = new Object[0];
    }

    public Iterator<E> iterator() {
        return snapshot().iterator();
    }

    public ListIterator<E> listIterator() {
        return snapshot().listIterator();
    }

    public ListIterator<E> listIterator(int index) {
        return snapshot().listIterator(index);
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return snapshot().subList(fromIndex, toIndex);
    }

    public synchronized void sort(java.util.Comparator<? super E> c) {
        ArrayList<E> l = snapshot();
        l.sort(c);
        array = l.toArray();
    }

    public boolean equals(Object o) {
        return snapshot().equals(o);
    }

    public int hashCode() {
        return snapshot().hashCode();
    }

    public String toString() {
        return Arrays.toString(array);
    }

    public Object clone() {
        return new CopyOnWriteArrayList<E>(snapshot());
    }
}
