package java.util;

public class Vector<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    protected Object[] elementData;
    protected int elementCount;
    protected int capacityIncrement;

    public Vector(int initialCapacity, int capacityIncrement) {
        this.elementData = new Object[initialCapacity];
        this.capacityIncrement = capacityIncrement;
    }

    public Vector(int initialCapacity) {
        this(initialCapacity, 0);
    }

    public Vector() {
        this(10);
    }

    public Vector(Collection<? extends E> c) {
        elementData = c.toArray();
        elementCount = elementData.length;
        if (elementData.getClass() != Object[].class) {
            elementData = Arrays.copyOf(elementData, elementCount, Object[].class);
        }
    }

    public synchronized void copyInto(Object[] anArray) {
        System.arraycopy(elementData, 0, anArray, 0, elementCount);
    }

    public synchronized void ensureCapacity(int minCapacity) {
        if (minCapacity > elementData.length) {
            int newCapacity = elementData.length + ((capacityIncrement > 0) ? capacityIncrement : elementData.length);
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            elementData = Arrays.copyOf(elementData, newCapacity);
        }
    }

    public synchronized void setSize(int newSize) {
        modCount++;
        ensureCapacity(newSize);
        for (int i = newSize; i < elementCount; i++) {
            elementData[i] = null;
        }
        elementCount = newSize;
    }

    public synchronized int capacity() {
        return elementData.length;
    }

    public synchronized int size() {
        return elementCount;
    }

    public synchronized boolean isEmpty() {
        return elementCount == 0;
    }

    public Enumeration<E> elements() {
        return new Enumeration<E>() {
            int count = 0;

            public boolean hasMoreElements() {
                return count < elementCount;
            }

            @SuppressWarnings("unchecked")
            public E nextElement() {
                synchronized (Vector.this) {
                    if (count < elementCount) {
                        return (E) elementData[count++];
                    }
                }
                throw new NoSuchElementException("Vector Enumeration");
            }
        };
    }

    public boolean contains(Object o) {
        return indexOf(o, 0) >= 0;
    }

    public int indexOf(Object o) {
        return indexOf(o, 0);
    }

    public synchronized int indexOf(Object o, int index) {
        for (int i = index; i < elementCount; i++) {
            if (Objects.equals(o, elementData[i])) {
                return i;
            }
        }
        return -1;
    }

    public synchronized int lastIndexOf(Object o) {
        return lastIndexOf(o, elementCount - 1);
    }

    public synchronized int lastIndexOf(Object o, int index) {
        for (int i = index; i >= 0; i--) {
            if (Objects.equals(o, elementData[i])) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public synchronized E elementAt(int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + elementCount);
        }
        return (E) elementData[index];
    }

    public synchronized E firstElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementAt(0);
    }

    public synchronized E lastElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementAt(elementCount - 1);
    }

    public synchronized void setElementAt(E obj, int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + elementCount);
        }
        elementData[index] = obj;
    }

    public synchronized void removeElementAt(int index) {
        remove(index);
    }

    public synchronized void insertElementAt(E obj, int index) {
        add(index, obj);
    }

    public synchronized void addElement(E obj) {
        add(obj);
    }

    public synchronized boolean removeElement(Object obj) {
        return remove(obj);
    }

    public synchronized void removeAllElements() {
        clear();
    }

    public synchronized Object clone() {
        return new Vector<E>(this);
    }

    public synchronized Object[] toArray() {
        return Arrays.copyOf(elementData, elementCount);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T[] toArray(T[] a) {
        if (a.length < elementCount) {
            return (T[]) Arrays.copyOf(elementData, elementCount, a.getClass());
        }
        System.arraycopy(elementData, 0, a, 0, elementCount);
        if (a.length > elementCount) {
            a[elementCount] = null;
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    public synchronized E get(int index) {
        if (index >= elementCount || index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return (E) elementData[index];
    }

    @SuppressWarnings("unchecked")
    public synchronized E set(int index, E element) {
        if (index >= elementCount || index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        E oldValue = (E) elementData[index];
        elementData[index] = element;
        return oldValue;
    }

    public synchronized boolean add(E e) {
        modCount++;
        ensureCapacity(elementCount + 1);
        elementData[elementCount++] = e;
        return true;
    }

    public synchronized void add(int index, E element) {
        if (index > elementCount || index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        modCount++;
        ensureCapacity(elementCount + 1);
        System.arraycopy(elementData, index, elementData, index + 1, elementCount - index);
        elementData[index] = element;
        elementCount++;
    }

    public synchronized boolean remove(Object o) {
        int i = indexOf(o);
        if (i < 0) {
            return false;
        }
        remove(i);
        return true;
    }

    @SuppressWarnings("unchecked")
    public synchronized E remove(int index) {
        modCount++;
        if (index >= elementCount || index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        E oldValue = (E) elementData[index];
        int numMoved = elementCount - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elementData, index + 1, elementData, index, numMoved);
        }
        elementData[--elementCount] = null;
        return oldValue;
    }

    public synchronized void clear() {
        modCount++;
        for (int i = 0; i < elementCount; i++) {
            elementData[i] = null;
        }
        elementCount = 0;
    }

    public synchronized boolean addAll(Collection<? extends E> c) {
        for (E e : c) {
            add(e);
        }
        return !c.isEmpty();
    }

    public synchronized void trimToSize() {
        if (elementCount < elementData.length) {
            elementData = Arrays.copyOf(elementData, elementCount);
        }
    }
}
