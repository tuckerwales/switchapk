package java.nio;

public abstract class DoubleBuffer extends Buffer implements Comparable<DoubleBuffer> {
    DoubleBuffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset) {
        super(mark, pos, lim, cap, backing, byteOffset, 3);
    }

    public static DoubleBuffer allocate(int capacity) {
        return new HeapDoubleBuffer(new double[capacity], -1, 0, capacity, capacity, 0);
    }

    public static DoubleBuffer wrap(double[] array, int offset, int length) {
        DoubleBuffer b = new HeapDoubleBuffer(array, -1, 0, array.length, array.length, 0);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static DoubleBuffer wrap(double[] array) {
        return wrap(array, 0, array.length);
    }

    abstract double getAbs(int i);

    abstract void putAbs(int i, double v);

    public abstract DoubleBuffer slice();

    public abstract DoubleBuffer duplicate();

    public abstract DoubleBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    public double get() {
        return getAbs(nextGetIndex());
    }

    public DoubleBuffer put(double v) {
        checkWritable();
        putAbs(nextPutIndex(), v);
        return this;
    }

    public double get(int index) {
        return getAbs(checkIndex(index));
    }

    public DoubleBuffer put(int index, double v) {
        checkWritable();
        putAbs(checkIndex(index), v);
        return this;
    }

    public DoubleBuffer get(double[] dst, int offset, int length) {
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        for (int i = 0; i < length; i++) {
            dst[offset + i] = getAbs(position + i);
        }
        position += length;
        return this;
    }

    public DoubleBuffer get(double[] dst) {
        return get(dst, 0, dst.length);
    }

    public DoubleBuffer put(DoubleBuffer src) {
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

    public DoubleBuffer put(double[] src, int offset, int length) {
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

    public final DoubleBuffer put(double[] src) {
        return put(src, 0, src.length);
    }

    public DoubleBuffer compact() {
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
        return backing instanceof double[];
    }

    public final double[] array() {
        return (double[]) super.array();
    }

    public DoubleBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public DoubleBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public DoubleBuffer mark() {
        super.mark();
        return this;
    }

    public DoubleBuffer reset() {
        super.reset();
        return this;
    }

    public DoubleBuffer clear() {
        super.clear();
        return this;
    }

    public DoubleBuffer flip() {
        super.flip();
        return this;
    }

    public DoubleBuffer rewind() {
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
        if (!(ob instanceof DoubleBuffer)) {
            return false;
        }
        DoubleBuffer that = (DoubleBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (Double.compare(this.get(i), that.get(j)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(DoubleBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Double.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }
}
