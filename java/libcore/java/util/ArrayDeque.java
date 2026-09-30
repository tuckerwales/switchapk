package java.util;

public class ArrayDeque<E> extends AbstractCollection<E> implements Deque<E>, Cloneable, java.io.Serializable {
    transient Object[] elements;
    transient int head;
    transient int tail;

    public ArrayDeque() {
        elements = new Object[16];
    }

    public ArrayDeque(int numElements) {
        int n = 8;
        while (n < numElements + 1) {
            n <<= 1;
        }
        elements = new Object[n];
    }

    public ArrayDeque(Collection<? extends E> c) {
        this(c.size());
        addAll(c);
    }

    private void doubleCapacity() {
        int p = head;
        int n = elements.length;
        int r = n - p;
        int newCapacity = n << 1;
        Object[] a = new Object[newCapacity];
        System.arraycopy(elements, p, a, 0, r);
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        head = 0;
        tail = n;
    }

    public void addFirst(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        elements[head = (head - 1) & (elements.length - 1)] = e;
        if (head == tail) {
            doubleCapacity();
        }
    }

    public void addLast(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        elements[tail] = e;
        if ((tail = (tail + 1) & (elements.length - 1)) == head) {
            doubleCapacity();
        }
    }

    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    public E removeFirst() {
        E x = pollFirst();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    public E removeLast() {
        E x = pollLast();
        if (x == null) {
            throw new NoSuchElementException();
        }
        return x;
    }

    @SuppressWarnings("unchecked")
    public E pollFirst() {
        int h = head;
        E result = (E) elements[h];
        if (result == null) {
            return null;
        }
        elements[h] = null;
        head = (h + 1) & (elements.length - 1);
        return result;
    }

    @SuppressWarnings("unchecked")
    public E pollLast() {
        int t = (tail - 1) & (elements.length - 1);
        E result = (E) elements[t];
        if (result == null) {
            return null;
        }
        elements[t] = null;
        tail = t;
        return result;
    }

    @SuppressWarnings("unchecked")
    public E getFirst() {
        E result = (E) elements[head];
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public E getLast() {
        E result = (E) elements[(tail - 1) & (elements.length - 1)];
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public E peekFirst() {
        return (E) elements[head];
    }

    @SuppressWarnings("unchecked")
    public E peekLast() {
        return (E) elements[(tail - 1) & (elements.length - 1)];
    }

    public boolean removeFirstOccurrence(Object o) {
        if (o != null) {
            int mask = elements.length - 1;
            int i = head;
            Object x;
            while ((x = elements[i]) != null) {
                if (o.equals(x)) {
                    delete(i);
                    return true;
                }
                i = (i + 1) & mask;
            }
        }
        return false;
    }

    public boolean removeLastOccurrence(Object o) {
        if (o != null) {
            int mask = elements.length - 1;
            int i = (tail - 1) & mask;
            Object x;
            while ((x = elements[i]) != null) {
                if (o.equals(x)) {
                    delete(i);
                    return true;
                }
                i = (i - 1) & mask;
            }
        }
        return false;
    }

    public boolean add(E e) {
        addLast(e);
        return true;
    }

    public boolean offer(E e) {
        return offerLast(e);
    }

    public E remove() {
        return removeFirst();
    }

    public E poll() {
        return pollFirst();
    }

    public E element() {
        return getFirst();
    }

    public E peek() {
        return peekFirst();
    }

    public void push(E e) {
        addFirst(e);
    }

    public E pop() {
        return removeFirst();
    }

    private boolean delete(int i) {
        // simple approach: rebuild
        Object[] old = toArray();
        int idx = (i - head) & (elements.length - 1);
        clear();
        for (int k = 0; k < old.length; k++) {
            if (k != idx) {
                @SuppressWarnings("unchecked")
                E e = (E) old[k];
                addLast(e);
            }
        }
        return true;
    }

    public int size() {
        return (tail - head) & (elements.length - 1);
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int cursor = head;
            private int fence = tail;
            private int lastRet = -1;

            public boolean hasNext() {
                return cursor != fence;
            }

            @SuppressWarnings("unchecked")
            public E next() {
                if (cursor == fence) {
                    throw new NoSuchElementException();
                }
                E result = (E) elements[cursor];
                if (tail != fence || result == null) {
                    throw new ConcurrentModificationException();
                }
                lastRet = cursor;
                cursor = (cursor + 1) & (elements.length - 1);
                return result;
            }

            public void remove() {
                if (lastRet < 0) {
                    throw new IllegalStateException();
                }
                int n = (lastRet - head) & (elements.length - 1);
                delete(lastRet);
                cursor = (head + n) & (elements.length - 1);
                fence = tail;
                lastRet = -1;
            }
        };
    }

    public Iterator<E> descendingIterator() {
        final Object[] snapshot = toArray();
        return new Iterator<E>() {
            int i = snapshot.length - 1;

            public boolean hasNext() {
                return i >= 0;
            }

            @SuppressWarnings("unchecked")
            public E next() {
                if (i < 0) {
                    throw new NoSuchElementException();
                }
                return (E) snapshot[i--];
            }
        };
    }

    public boolean contains(Object o) {
        if (o != null) {
            int mask = elements.length - 1;
            int i = head;
            Object x;
            while ((x = elements[i]) != null) {
                if (o.equals(x)) {
                    return true;
                }
                i = (i + 1) & mask;
            }
        }
        return false;
    }

    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    public void clear() {
        int h = head;
        int t = tail;
        if (h != t) {
            head = tail = 0;
            int i = h;
            int mask = elements.length - 1;
            do {
                elements[i] = null;
                i = (i + 1) & mask;
            } while (i != t);
        }
    }

    public Object[] toArray() {
        int n = size();
        Object[] a = new Object[n];
        for (int k = 0, i = head; k < n; k++, i = (i + 1) & (elements.length - 1)) {
            a[k] = elements[i];
        }
        return a;
    }

    public ArrayDeque<E> clone() {
        return new ArrayDeque<E>(this);
    }
}
