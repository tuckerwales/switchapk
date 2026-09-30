package android.util;

import java.util.Arrays;

public class SparseArray<E> implements Cloneable {
    private int[] mKeys;
    private Object[] mValues;
    private int mSize;

    public SparseArray() { this(10); }

    public SparseArray(int initialCapacity) {
        if (initialCapacity < 1) initialCapacity = 1;
        mKeys = new int[initialCapacity];
        mValues = new Object[initialCapacity];
    }

    @Override
    @SuppressWarnings("unchecked")
    public SparseArray<E> clone() {
        try {
            SparseArray<E> c = (SparseArray<E>) super.clone();
            c.mKeys = mKeys.clone();
            c.mValues = mValues.clone();
            return c;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public boolean contains(int key) { return indexOfKey(key) >= 0; }

    public E get(int key) { return get(key, null); }

    @SuppressWarnings("unchecked")
    public E get(int key, E valueIfKeyNotFound) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        return i < 0 ? valueIfKeyNotFound : (E) mValues[i];
    }

    public void delete(int key) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        if (i >= 0) removeAt(i);
    }

    public E removeReturnOld(int key) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        if (i < 0) return null;
        E old = valueAt(i);
        removeAt(i);
        return old;
    }

    public void remove(int key) { delete(key); }

    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - index - 1);
        System.arraycopy(mValues, index + 1, mValues, index, mSize - index - 1);
        mSize--;
        mValues[mSize] = null;
    }

    public void removeAtRange(int index, int size) {
        int end = Math.min(mSize, index + size);
        for (int i = end - 1; i >= index; i--) removeAt(i);
    }

    public void set(int key, E value) { put(key, value); }

    public void put(int key, E value) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        if (i >= 0) {
            mValues[i] = value;
            return;
        }
        i = ~i;
        if (mSize == mKeys.length) {
            int n = mSize * 2 + 2;
            mKeys = Arrays.copyOf(mKeys, n);
            mValues = Arrays.copyOf(mValues, n);
        }
        System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i);
        System.arraycopy(mValues, i, mValues, i + 1, mSize - i);
        mKeys[i] = key;
        mValues[i] = value;
        mSize++;
    }

    public void putAll(SparseArray<? extends E> other) {
        for (int i = 0; i < other.size(); i++) put(other.keyAt(i), other.valueAt(i));
    }

    public E putIfAbsent(int key, E value) {
        E cur = get(key);
        if (cur == null) put(key, value);
        return cur;
    }

    public int size() { return mSize; }

    public int keyAt(int index) {
        if (index >= mSize) throw new ArrayIndexOutOfBoundsException(index);
        return mKeys[index];
    }

    @SuppressWarnings("unchecked")
    public E valueAt(int index) {
        if (index >= mSize) throw new ArrayIndexOutOfBoundsException(index);
        return (E) mValues[index];
    }

    public void setValueAt(int index, E value) { mValues[index] = value; }

    public int indexOfKey(int key) { return Arrays.binarySearch(mKeys, 0, mSize, key) < 0 ? -1 : Arrays.binarySearch(mKeys, 0, mSize, key); }

    public int indexOfValue(E value) {
        for (int i = 0; i < mSize; i++) if (mValues[i] == value) return i;
        return -1;
    }

    public int indexOfValueByValue(E value) {
        for (int i = 0; i < mSize; i++) if (value == null ? mValues[i] == null : value.equals(mValues[i])) return i;
        return -1;
    }

    public void clear() {
        Arrays.fill(mValues, 0, mSize, null);
        mSize = 0;
    }

    public void append(int key, E value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }
        if (mSize == mKeys.length) {
            int n = mSize * 2 + 2;
            mKeys = Arrays.copyOf(mKeys, n);
            mValues = Arrays.copyOf(mValues, n);
        }
        mKeys[mSize] = key;
        mValues[mSize] = value;
        mSize++;
    }

    public boolean contentEquals(SparseArray<?> other) {
        if (other == null || size() != other.size()) return false;
        for (int i = 0; i < mSize; i++) {
            if (keyAt(i) != other.keyAt(i)) return false;
            Object a = valueAt(i), b = other.valueAt(i);
            if (a == null ? b != null : !a.equals(b)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (mSize == 0) return "{}";
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < mSize; i++) {
            if (i > 0) b.append(", ");
            b.append(mKeys[i]).append('=').append(mValues[i] == this ? "(this Map)" : mValues[i]);
        }
        return b.append('}').toString();
    }
}
