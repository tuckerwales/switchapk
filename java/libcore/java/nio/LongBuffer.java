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

    public abstract LongBuffer slice();

    public abstract LongBuffer duplicate();

    public abstract LongBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    public long get() {
        return getAbs(nextGetIndex());
    }

    public LongBuffer put(long v) {
        checkWritable();
        putAbs(nextPutIndex(), v);
        return this;
    }

    public long get(int index) {
        return getAbs(checkIndex(index));
    }

    public LongBuffer put(int index, long v) {
        checkWritable();
        putAbs(checkIndex(index), v);
        return this;
    }

    public LongBuffer get(long[] dst, int offset, int length) {
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        for (int i = 0; i < length; i++) {
            dst[offset + i] = getAbs(position + i);
        }
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
        for (int i = 0; i < n; i++) {
            put(src.get());
        }
        return this;
    }

    public LongBuffer put(long[] src, int offset, int length) {
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

    public final LongBuffer put(long[] src) {
        return put(src, 0, src.length);
    }

    public LongBuffer compact() {
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
