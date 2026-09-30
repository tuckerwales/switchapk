package android.util;

import java.util.Arrays;

public class LongSparseArray<E> implements Cloneable {
    private long[] mKeys;
    private Object[] mValues;
    private int mSize;

    public LongSparseArray() { this(10); }

    public LongSparseArray(int initialCapacity) {
        if (initialCapacity < 1) initialCapacity = 1;
        mKeys = new long[initialCapacity];
        mValues = new Object[initialCapacity];
    }

    @Override
    @SuppressWarnings("unchecked")
    public LongSparseArray<E> clone() {
        try {
            LongSparseArray<E> c = (LongSparseArray<E>) super.clone();
            c.mKeys = mKeys.clone();
            c.mValues = mValues.clone();
            return c;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public E get(long key) { return get(key, null); }

    @SuppressWarnings("unchecked")
    public E get(long key, E valueIfKeyNotFound) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        return i < 0 ? valueIfKeyNotFound : (E) mValues[i];
    }

    public void delete(long key) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        if (i >= 0) removeAt(i);
    }

    public void remove(long key) { delete(key); }

    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - index - 1);
        System.arraycopy(mValues, index + 1, mValues, index, mSize - index - 1);
        mSize--;
        mValues[mSize] = null;
    }

    public void put(long key, E value) {
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

    public int size() { return mSize; }
    public long keyAt(int index) { return mKeys[index]; }
    @SuppressWarnings("unchecked")
    public E valueAt(int index) { return (E) mValues[index]; }
    public void setValueAt(int index, E value) { mValues[index] = value; }

    public int indexOfKey(long key) {
        int i = Arrays.binarySearch(mKeys, 0, mSize, key);
        return i < 0 ? -1 : i;
    }

    public int indexOfValue(E value) {
        for (int i = 0; i < mSize; i++) if (mValues[i] == value) return i;
        return -1;
    }

    public void clear() {
        Arrays.fill(mValues, 0, mSize, null);
        mSize = 0;
    }

    public void append(long key, E value) { put(key, value); }

    @Override
    public String toString() {
        if (mSize == 0) return "{}";
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < mSize; i++) {
            if (i > 0) b.append(", ");
            b.append(mKeys[i]).append('=').append(mValues[i]);
        }
        return b.append('}').toString();
    }
}
