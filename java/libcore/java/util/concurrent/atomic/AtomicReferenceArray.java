package java.util.concurrent.atomic;

public class AtomicReferenceArray<E> implements java.io.Serializable {
    private final Object[] array;

    public AtomicReferenceArray(int length) {
        array = new Object[length];
    }

    public AtomicReferenceArray(E[] array) {
        this.array = java.util.Arrays.copyOf(array, array.length, Object[].class);
    }

    public final int length() {
        return array.length;
    }

    @SuppressWarnings("unchecked")
    public final synchronized E get(int i) {
        return (E) array[i];
    }

    public final synchronized void set(int i, E newValue) {
        array[i] = newValue;
    }

    public final void lazySet(int i, E newValue) {
        set(i, newValue);
    }

    @SuppressWarnings("unchecked")
    public final synchronized E getAndSet(int i, E newValue) {
        E old = (E) array[i];
        array[i] = newValue;
        return old;
    }

    public final synchronized boolean compareAndSet(int i, E expect, E update) {
        if (array[i] == expect) {
            array[i] = update;
            return true;
        }
        return false;
    }

    public synchronized String toString() {
        return java.util.Arrays.toString(array);
    }
}
