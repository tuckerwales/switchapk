package java.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final Object[] EMPTY = new Object[0];
    transient Object[] elementData;
    private int size;

    public ArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
        }
        elementData = initialCapacity == 0 ? EMPTY : new Object[initialCapacity];
    }

    public ArrayList() {
        elementData = EMPTY;
    }

    public ArrayList(Collection<? extends E> c) {
        elementData = c.toArray();
        size = elementData.length;
        if (elementData.getClass() != Object[].class) {
            elementData = Arrays.copyOf(elementData, size, Object[].class);
        }
    }

    public void trimToSize() {
        modCount++;
        if (size < elementData.length) {
            elementData = Arrays.copyOf(elementData, size);
        }
    }

    public void ensureCapacity(int minCapacity) {
        if (minCapacity > elementData.length) {
            int newCapacity = elementData.length + (elementData.length >> 1);
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            if (newCapacity < 10) {
                newCapacity = 10;
            }
            elementData = Arrays.copyOf(elementData, newCapacity);
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public int indexOf(Object o) {
        Object[] es = elementData;
        if (o == null) {
            for (int i = 0; i < size; i++) {
                if (es[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (o.equals(es[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        Object[] es = elementData;
        for (int i = size - 1; i >= 0; i--) {
            if (Objects.equals(o, es[i])) {
                return i;
            }
        }
        return -1;
    }

    public Object clone() {
        try {
            @SuppressWarnings("unchecked")
            ArrayList<E> v = (ArrayList<E>) super.clone();
            v.elementData = Arrays.copyOf(elementData, size);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public Object[] toArray() {
        return Arrays.copyOf(elementData, size);
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size) {
            return (T[]) Arrays.copyOf(elementData, size, a.getClass());
        }
        System.arraycopy(elementData, 0, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return (E) elementData[index];
    }

    @SuppressWarnings("unchecked")
    public E set(int index, E element) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        E oldValue = (E) elementData[index];
        elementData[index] = element;
        return oldValue;
    }

    public boolean add(E e) {
        modCount++;
        if (size == elementData.length) {
            ensureCapacity(size + 1);
        }
        elementData[size++] = e;
        return true;
    }

    public void add(int index, E element) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        modCount++;
        if (size == elementData.length) {
            ensureCapacity(size + 1);
        }
        System.arraycopy(elementData, index, elementData, index + 1, size - index);
        elementData[index] = element;
        size++;
    }

    @SuppressWarnings("unchecked")
    public E remove(int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        modCount++;
        E oldValue = (E) elementData[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elementData, index + 1, elementData, index, numMoved);
        }
        elementData[--size] = null;
        return oldValue;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    public void clear() {
        modCount++;
        for (int i = 0; i < size; i++) {
            elementData[i] = null;
        }
        size = 0;
    }

    public boolean addAll(Collection<? extends E> c) {
        Object[] a = c.toArray();
        int numNew = a.length;
        modCount++;
        ensureCapacity(size + numNew);
        System.arraycopy(a, 0, elementData, size, numNew);
        size += numNew;
        return numNew != 0;
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        Object[] a = c.toArray();
        int numNew = a.length;
        modCount++;
        ensureCapacity(size + numNew);
        int numMoved = size - index;
        if (numMoved > 0) {
            System.arraycopy(elementData, index, elementData, index + numNew, numMoved);
        }
        System.arraycopy(a, 0, elementData, index, numNew);
        size += numNew;
        return numNew != 0;
    }

    protected void removeRange(int fromIndex, int toIndex) {
        modCount++;
        int numMoved = size - toIndex;
        System.arraycopy(elementData, toIndex, elementData, fromIndex, numMoved);
        int newSize = size - (toIndex - fromIndex);
        for (int i = newSize; i < size; i++) {
            elementData[i] = null;
        }
        size = newSize;
    }

    public boolean removeAll(Collection<?> c) {
        return batchRemove(c, false);
    }

    public boolean retainAll(Collection<?> c) {
        return batchRemove(c, true);
    }

    private boolean batchRemove(Collection<?> c, boolean complement) {
        Objects.requireNonNull(c);
        int w = 0;
        for (int r = 0; r < size; r++) {
            if (c.contains(elementData[r]) == complement) {
                elementData[w++] = elementData[r];
            }
        }
        if (w == size) {
            return false;
        }
        for (int i = w; i < size; i++) {
            elementData[i] = null;
        }
        modCount++;
        size = w;
        return true;
    }

    @SuppressWarnings("unchecked")
    public boolean removeIf(Predicate<? super E> filter) {
        int w = 0;
        for (int r = 0; r < size; r++) {
            if (!filter.test((E) elementData[r])) {
                elementData[w++] = elementData[r];
            }
        }
        if (w == size) {
            return false;
        }
        for (int i = w; i < size; i++) {
            elementData[i] = null;
        }
        modCount++;
        size = w;
        return true;
    }

    @SuppressWarnings("unchecked")
    public void forEach(Consumer<? super E> action) {
        int expected = modCount;
        for (int i = 0; modCount == expected && i < size; i++) {
            action.accept((E) elementData[i]);
        }
        if (modCount != expected) {
            throw new ConcurrentModificationException();
        }
    }

    @SuppressWarnings("unchecked")
    public void replaceAll(UnaryOperator<E> operator) {
        for (int i = 0; i < size; i++) {
            elementData[i] = operator.apply((E) elementData[i]);
        }
        modCount++;
    }

    @SuppressWarnings("unchecked")
    public void sort(Comparator<? super E> c) {
        Arrays.sort((E[]) elementData, 0, size, c);
        modCount++;
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        int cursor;
        int lastRet = -1;
        int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor < size;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            int i = cursor;
            if (i >= size) {
                throw new NoSuchElementException();
            }
            cursor = i + 1;
            return (E) elementData[lastRet = i];
        }

        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            ArrayList.this.remove(lastRet);
            cursor = lastRet;
            lastRet = -1;
            expectedModCount = modCount;
        }
    }
}
