package java.util;

public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
    protected transient int modCount = 0;

    protected AbstractList() {
    }

    public boolean add(E e) {
        add(size(), e);
        return true;
    }

    public abstract E get(int index);

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
        ListIterator<E> it = listIterator();
        while (it.hasNext()) {
            if (Objects.equals(o, it.next())) {
                return it.previousIndex();
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        ListIterator<E> it = listIterator(size());
        while (it.hasPrevious()) {
            if (Objects.equals(o, it.previous())) {
                return it.nextIndex();
            }
        }
        return -1;
    }

    public void clear() {
        removeRange(0, size());
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            add(index++, e);
            modified = true;
        }
        return modified;
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    public ListIterator<E> listIterator(final int index) {
        if (index < 0 || index > size()) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        return new ListItr(index);
    }

    private class Itr implements Iterator<E> {
        int cursor = 0;
        int lastRet = -1;
        int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor != size();
        }

        public E next() {
            checkForComodification();
            try {
                int i = cursor;
                E next = get(i);
                lastRet = i;
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                checkForComodification();
                throw new NoSuchElementException();
            }
        }

        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            checkForComodification();
            AbstractList.this.remove(lastRet);
            if (lastRet < cursor) {
                cursor--;
            }
            lastRet = -1;
            expectedModCount = modCount;
        }

        final void checkForComodification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            cursor = index;
        }

        public boolean hasPrevious() {
            return cursor != 0;
        }

        public E previous() {
            checkForComodification();
            int i = cursor - 1;
            if (i < 0) {
                throw new NoSuchElementException();
            }
            E previous = get(i);
            lastRet = cursor = i;
            return previous;
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        public void set(E e) {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            checkForComodification();
            AbstractList.this.set(lastRet, e);
            expectedModCount = modCount;
        }

        public void add(E e) {
            checkForComodification();
            int i = cursor;
            AbstractList.this.add(i, e);
            lastRet = -1;
            cursor = i + 1;
            expectedModCount = modCount;
        }
    }

    public List<E> subList(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + ", toIndex: " + toIndex);
        }
        return new SubList<E>(this, fromIndex, toIndex);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof List)) {
            return false;
        }
        ListIterator<E> e1 = listIterator();
        ListIterator<?> e2 = ((List<?>) o).listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            if (!Objects.equals(e1.next(), e2.next())) {
                return false;
            }
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    public int hashCode() {
        int hashCode = 1;
        for (E e : this) {
            hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
        }
        return hashCode;
    }

    protected void removeRange(int fromIndex, int toIndex) {
        ListIterator<E> it = listIterator(fromIndex);
        for (int i = 0, n = toIndex - fromIndex; i < n; i++) {
            it.next();
            it.remove();
        }
    }

    static class SubList<E> extends AbstractList<E> implements RandomAccess {
        private final AbstractList<E> l;
        private final int offset;
        private int size;

        SubList(AbstractList<E> list, int fromIndex, int toIndex) {
            l = list;
            offset = fromIndex;
            size = toIndex - fromIndex;
            this.modCount = l.modCount;
        }

        private void rangeCheck(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }
        }

        public E set(int index, E element) {
            rangeCheck(index);
            return l.set(index + offset, element);
        }

        public E get(int index) {
            rangeCheck(index);
            return l.get(index + offset);
        }

        public int size() {
            return size;
        }

        public void add(int index, E element) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException();
            }
            l.add(index + offset, element);
            this.modCount = l.modCount;
            size++;
        }

        public E remove(int index) {
            rangeCheck(index);
            E result = l.remove(index + offset);
            this.modCount = l.modCount;
            size--;
            return result;
        }

        protected void removeRange(int fromIndex, int toIndex) {
            for (int i = toIndex - 1; i >= fromIndex; i--) {
                l.remove(i + offset);
            }
            this.modCount = l.modCount;
            size -= (toIndex - fromIndex);
        }
    }
}
