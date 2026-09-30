package java.nio;

public abstract class IntBuffer extends Buffer implements Comparable<IntBuffer> {
    IntBuffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset) {
        super(mark, pos, lim, cap, backing, byteOffset, 2);
    }

    public static IntBuffer allocate(int capacity) {
        return new HeapIntBuffer(new int[capacity], -1, 0, capacity, capacity, 0);
    }

    public static IntBuffer wrap(int[] array, int offset, int length) {
        IntBuffer b = new HeapIntBuffer(array, -1, 0, array.length, array.length, 0);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static IntBuffer wrap(int[] array) {
        return wrap(array, 0, array.length);
    }

    abstract int getAbs(int i);

    abstract void putAbs(int i, int v);

    public abstract IntBuffer slice();

    public abstract IntBuffer duplicate();

    public abstract IntBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    public int get() {
        return getAbs(nextGetIndex());
    }

    public IntBuffer put(int v) {
        checkWritable();
        putAbs(nextPutIndex(), v);
        return this;
    }

    public int get(int index) {
        return getAbs(checkIndex(index));
    }

    public IntBuffer put(int index, int v) {
        checkWritable();
        putAbs(checkIndex(index), v);
        return this;
    }

    public IntBuffer get(int[] dst, int offset, int length) {
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        for (int i = 0; i < length; i++) {
            dst[offset + i] = getAbs(position + i);
        }
        position += length;
        return this;
    }

    public IntBuffer get(int[] dst) {
        return get(dst, 0, dst.length);
    }

    public IntBuffer put(IntBuffer src) {
        if (src == this) {
            throw new IllegalArgumentException();
        }
        int n = src.remaining();
        if (n > remaining()) {
            throw new BufferOverflowException();
        }
        for (int i = 0; i < n; i++) {
            put(src.get());
        }
        return this;
    }

    public IntBuffer put(int[] src, int offset, int length) {
        checkWritable();
        if (length > remaining()) {
            throw new BufferOverflowException();
        }
        for (int i = 0; i < length; i++) {
            putAbs(position + i, src[offset + i]);
        }
        position += length;
        return this;
    }

    public final IntBuffer put(int[] src) {
        return put(src, 0, src.length);
    }

    public IntBuffer compact() {
        checkWritable();
        int rem = remaining();
        for (int i = 0; i < rem; i++) {
            putAbs(i, getAbs(position + i));
        }
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }

    boolean arrayMatchesType() {
        return backing instanceof int[];
    }

    public final int[] array() {
        return (int[]) super.array();
    }

    public IntBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public IntBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public IntBuffer mark() {
        super.mark();
        return this;
    }

    public IntBuffer reset() {
        super.reset();
        return this;
    }

    public IntBuffer clear() {
        super.clear();
        return this;
    }

    public IntBuffer flip() {
        super.flip();
        return this;
    }

    public IntBuffer rewind() {
        super.rewind();
        return this;
    }

    public int hashCode() {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--) {
            h = 31 * h + (int) get(i);
        }
        return h;
    }

    public boolean equals(Object ob) {
        if (this == ob) {
            return true;
        }
        if (!(ob instanceof IntBuffer)) {
            return false;
        }
        IntBuffer that = (IntBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (Integer.compare(this.get(i), that.get(j)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(IntBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Integer.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }
}
