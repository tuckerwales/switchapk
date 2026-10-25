package java.nio;

public abstract class LongBuffer extends Buffer implements Comparable<LongBuffer> {
    LongBuffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset) {
        super(mark, pos, lim, cap, backing, byteOffset, 3);
    }

    public static LongBuffer allocate(int capacity) {
        return new HeapLongBuffer(new long[capacity], -1, 0, capacity, capacity, 0);
    }

    public static LongBuffer wrap(long[] array, int offset, int length) {
        LongBuffer b = new HeapLongBuffer(array, -1, 0, array.length, array.length, 0);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static LongBuffer wrap(long[] array) {
        return wrap(array, 0, array.length);
    }

    abstract long getAbs(int i);

    abstract void putAbs(int i, long v);

    /* bytes per element when this buffer's order is not the platform's (a byte-backed view), else 0 */
    final int nativeSwap() {
        return order() == ByteOrder.nativeOrder() ? 0 : 8;
    }

    public abstract LongBuffer slice();

    public abstract LongBuffer duplicate();

    public abstract LongBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    /* the index checks are written out: a call costs about as much as the access itself */
    public long get() {
        int p = position;
        if (p >= limit) {
            throw new BufferUnderflowException();
        }
        position = p + 1;
        return getAbs(p);
    }

    public LongBuffer put(long v) {
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

    public long get(int index) {
        if (index < 0 || index >= limit) {
            checkIndex(index);
        }
        return getAbs(index);
    }

    public LongBuffer put(int index, long v) {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        if (index < 0 || index >= limit) {
            checkIndex(index);
        }
        putAbs(index, v);
        return this;
    }

    public LongBuffer get(long[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        copyMemory(backing, byteOffset + (position << 3), dst, offset << 3, length << 3, nativeSwap());
        position += length;
        return this;
    }

    public LongBuffer get(long[] dst) {
        return get(dst, 0, dst.length);
    }

    public LongBuffer put(LongBuffer src) {
        if (src == this) {
            throw new IllegalArgumentException();
        }
        int n = src.remaining();
        if (n > remaining()) {
            throw new BufferOverflowException();
        }
        checkWritable();
        copyMemory(src.backing, src.byteOffset + (src.position << 3), backing, byteOffset + (position << 3), n << 3,
                src.order() == order() ? 0 : 8);
        src.position += n;
        position += n;
        return this;
    }

    public LongBuffer put(long[] src, int offset, int length) {
        checkWritable();
        checkBounds(offset, length, src.length);
        if (length > remaining()) {
            throw new BufferOverflowException();
        }
        copyMemory(src, offset << 3, backing, byteOffset + (position << 3), length << 3, nativeSwap());
        position += length;
        return this;
    }

    public final LongBuffer put(long[] src) {
        return put(src, 0, src.length);
    }

    public LongBuffer compact() {
        checkWritable();
        int rem = remaining();
        copyMemory(backing, byteOffset + (position << 3), backing, byteOffset, rem << 3, 0);
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }

    boolean arrayMatchesType() {
        return backing instanceof long[];
    }

    public final long[] array() {
        return (long[]) super.array();
    }

    public LongBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public LongBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public LongBuffer mark() {
        super.mark();
        return this;
    }

    public LongBuffer reset() {
        super.reset();
        return this;
    }

    public LongBuffer clear() {
        super.clear();
        return this;
    }

    public LongBuffer flip() {
        super.flip();
        return this;
    }

    public LongBuffer rewind() {
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
        if (!(ob instanceof LongBuffer)) {
            return false;
        }
        LongBuffer that = (LongBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (Long.compare(this.get(i), that.get(j)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(LongBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Long.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }
}
