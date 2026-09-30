package java.util;

public class PriorityQueue<E> extends AbstractQueue<E> implements java.io.Serializable {
    private Object[] queue;
    private int size = 0;
    private final Comparator<? super E> comparator;
    private int modCount = 0;

    public PriorityQueue() {
        this(11, null);
    }

    public PriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    public PriorityQueue(Comparator<? super E> comparator) {
        this(11, comparator);
    }

    public PriorityQueue(int initialCapacity, Comparator<? super E> comparator) {
        if (initialCapacity < 1) {
            initialCapacity = 1;
        }
        this.queue = new Object[initialCapacity];
        this.comparator = comparator;
    }

    public PriorityQueue(Collection<? extends E> c) {
        this(Math.max(1, c.size()), PriorityQueue.<E>comparatorOf(c));
        addAll(c);
    }

    @SuppressWarnings("unchecked")
    private static <E> Comparator<? super E> comparatorOf(Collection<? extends E> c) {
        if (c instanceof PriorityQueue) {
            return (Comparator<? super E>) ((PriorityQueue<?>) c).comparator();
        }
        return null;
    }

    public boolean add(E e) {
        return offer(e);
    }

    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        modCount++;
        int i = size;
        if (i >= queue.length) {
            queue = Arrays.copyOf(queue, queue.length * 2 + 2);
        }
        size = i + 1;
        siftUp(i, e);
        return true;
    }

    @SuppressWarnings("unchecked")
    public E peek() {
        return (size == 0) ? null : (E) queue[0];
    }

    private int indexOf(Object o) {
        if (o != null) {
            for (int i = 0; i < size; i++) {
                if (o.equals(queue[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i == -1) {
            return false;
        }
        removeAt(i);
        return true;
    }

    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    public Object[] toArray() {
        return Arrays.copyOf(queue, size);
    }

    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int cursor = 0;
            private int lastRet = -1;
            private int expectedModCount = modCount;

            public boolean hasNext() {
                return cursor < size;
            }

            @SuppressWarnings("unchecked")
            public E next() {
                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
                if (cursor < size) {
                    return (E) queue[lastRet = cursor++];
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                if (lastRet < 0) {
                    throw new IllegalStateException();
                }
                removeAt(lastRet);
                cursor = lastRet;
                lastRet = -1;
                expectedModCount = modCount;
            }
        };
    }

    public int size() {
        return size;
    }

    public void clear() {
        modCount++;
        for (int i = 0; i < size; i++) {
            queue[i] = null;
        }
        size = 0;
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0) {
            return null;
        }
        int s = --size;
        modCount++;
        E result = (E) queue[0];
        E x = (E) queue[s];
        queue[s] = null;
        if (s != 0) {
            siftDown(0, x);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    E removeAt(int i) {
        modCount++;
        int s = --size;
        if (s == i) {
            queue[i] = null;
        } else {
            E moved = (E) queue[s];
            queue[s] = null;
            siftDown(i, moved);
            if (queue[i] == moved) {
                siftUp(i, moved);
                if (queue[i] != moved) {
                    return moved;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private int cmp(Object a, Object b) {
        return comparator != null ? comparator.compare((E) a, (E) b) : ((Comparable<Object>) a).compareTo(b);
    }

    private void siftUp(int k, E x) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            if (cmp(x, e) >= 0) {
                break;
            }
            queue[k] = e;
            k = parent;
        }
        queue[k] = x;
    }

    private void siftDown(int k, E x) {
        int half = size >>> 1;
        while (k < half) {
            int child = (k << 1) + 1;
            Object c = queue[child];
            int right = child + 1;
            if (right < size && cmp(c, queue[right]) > 0) {
                c = queue[child = right];
            }
            if (cmp(x, c) <= 0) {
                break;
            }
            queue[k] = c;
            k = child;
        }
        queue[k] = x;
    }

    public Comparator<? super E> comparator() {
        return comparator;
    }
}
