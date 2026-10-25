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

    /* bytes per element when this buffer's order is not the platform's (a byte-backed view), else 0 */
    final int nativeSwap() {
        return order() == ByteOrder.nativeOrder() ? 0 : 4;
    }

    public abstract IntBuffer slice();

    public abstract IntBuffer duplicate();

    public abstract IntBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    /* the index checks are written out: a call costs about as much as the access itself */
    public int get() {
        int p = position;
        if (p >= limit) {
            throw new BufferUnderflowException();
        }
        position = p + 1;
        return getAbs(p);
    }

    public IntBuffer put(int v) {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        int p = position;
        if (p >= limit) {
            throw new BufferOverflowException();
        }
        position = p + 1;
        putAbs(p, v);
        return this;
    }

    public int get(int index) {
        if (index < 0 || index >= limit) {
            checkIndex(index);
        }
        return getAbs(index);
    }

    public IntBuffer put(int index, int v) {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        if (index < 0 || index >= limit) {
            checkIndex(index);
        }
        putAbs(index, v);
        return this;
    }

    public IntBuffer get(int[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        copyMemory(backing, byteOffset + (position << 2), dst, offset << 2, length << 2, nativeSwap());
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
        checkWritable();
        copyMemory(src.backing, src.byteOffset + (src.position << 2), backing, byteOffset + (position << 2), n << 2,
                src.order() == order() ? 0 : 4);
        src.position += n;
        position += n;
        return this;
    }

    public IntBuffer put(int[] src, int offset, int length) {
        checkWritable();
        checkBounds(offset, length, src.length);
        if (length > remaining()) {
            throw new BufferOverflowException();
        }
        copyMemory(src, offset << 2, backing, byteOffset + (position << 2), length << 2, nativeSwap());
        position += length;
        return this;
    }

    public final IntBuffer put(int[] src) {
        return put(src, 0, src.length);
    }

    public IntBuffer compact() {
        checkWritable();
        int rem = remaining();
        copyMemory(backing, byteOffset + (position << 2), backing, byteOffset, rem << 2, 0);
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
