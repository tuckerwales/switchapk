package java.nio;

public abstract class ShortBuffer extends Buffer implements Comparable<ShortBuffer> {
    ShortBuffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset) {
        super(mark, pos, lim, cap, backing, byteOffset, 1);
    }

    public static ShortBuffer allocate(int capacity) {
        return new HeapShortBuffer(new short[capacity], -1, 0, capacity, capacity, 0);
    }

    public static ShortBuffer wrap(short[] array, int offset, int length) {
        ShortBuffer b = new HeapShortBuffer(array, -1, 0, array.length, array.length, 0);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static ShortBuffer wrap(short[] array) {
        return wrap(array, 0, array.length);
    }

    abstract short getAbs(int i);

    abstract void putAbs(int i, short v);

    public abstract ShortBuffer slice();

    public abstract ShortBuffer duplicate();

    public abstract ShortBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    public short get() {
        return getAbs(nextGetIndex());
    }

    public ShortBuffer put(short v) {
        checkWritable();
        putAbs(nextPutIndex(), v);
        return this;
    }

    public short get(int index) {
        return getAbs(checkIndex(index));
    }

    public ShortBuffer put(int index, short v) {
        checkWritable();
        putAbs(checkIndex(index), v);
        return this;
    }

    public ShortBuffer get(short[] dst, int offset, int length) {
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        for (int i = 0; i < length; i++) {
            dst[offset + i] = getAbs(position + i);
        }
        position += length;
        return this;
    }

    public ShortBuffer get(short[] dst) {
        return get(dst, 0, dst.length);
    }

    public ShortBuffer put(ShortBuffer src) {
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

    public ShortBuffer put(short[] src, int offset, int length) {
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

    public final ShortBuffer put(short[] src) {
        return put(src, 0, src.length);
    }

    public ShortBuffer compact() {
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
        return backing instanceof short[];
    }

    public final short[] array() {
        return (short[]) super.array();
    }

    public ShortBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public ShortBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public ShortBuffer mark() {
        super.mark();
        return this;
    }

    public ShortBuffer reset() {
        super.reset();
        return this;
    }

    public ShortBuffer clear() {
        super.clear();
        return this;
    }

    public ShortBuffer flip() {
        super.flip();
        return this;
    }

    public ShortBuffer rewind() {
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
        if (!(ob instanceof ShortBuffer)) {
            return false;
        }
        ShortBuffer that = (ShortBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (Short.compare(this.get(i), that.get(j)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(ShortBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Short.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }
}
