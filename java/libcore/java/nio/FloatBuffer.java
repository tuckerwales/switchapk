package java.nio;

public abstract class FloatBuffer extends Buffer implements Comparable<FloatBuffer> {
    FloatBuffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset) {
        super(mark, pos, lim, cap, backing, byteOffset, 2);
    }

    public static FloatBuffer allocate(int capacity) {
        return new HeapFloatBuffer(new float[capacity], -1, 0, capacity, capacity, 0);
    }

    public static FloatBuffer wrap(float[] array, int offset, int length) {
        FloatBuffer b = new HeapFloatBuffer(array, -1, 0, array.length, array.length, 0);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static FloatBuffer wrap(float[] array) {
        return wrap(array, 0, array.length);
    }

    abstract float getAbs(int i);

    abstract void putAbs(int i, float v);

    public abstract FloatBuffer slice();

    public abstract FloatBuffer duplicate();

    public abstract FloatBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    public float get() {
        return getAbs(nextGetIndex());
    }

    public FloatBuffer put(float v) {
        checkWritable();
        putAbs(nextPutIndex(), v);
        return this;
    }

    public float get(int index) {
        return getAbs(checkIndex(index));
    }

    public FloatBuffer put(int index, float v) {
        checkWritable();
        putAbs(checkIndex(index), v);
        return this;
    }

    public FloatBuffer get(float[] dst, int offset, int length) {
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        for (int i = 0; i < length; i++) {
            dst[offset + i] = getAbs(position + i);
        }
        position += length;
        return this;
    }

    public FloatBuffer get(float[] dst) {
        return get(dst, 0, dst.length);
    }

    public FloatBuffer put(FloatBuffer src) {
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

    public FloatBuffer put(float[] src, int offset, int length) {
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

    public final FloatBuffer put(float[] src) {
        return put(src, 0, src.length);
    }

    public FloatBuffer compact() {
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
        return backing instanceof float[];
    }

    public final float[] array() {
        return (float[]) super.array();
    }

    public FloatBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public FloatBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public FloatBuffer mark() {
        super.mark();
        return this;
    }

    public FloatBuffer reset() {
        super.reset();
        return this;
    }

    public FloatBuffer clear() {
        super.clear();
        return this;
    }

    public FloatBuffer flip() {
        super.flip();
        return this;
    }

    public FloatBuffer rewind() {
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
        if (!(ob instanceof FloatBuffer)) {
            return false;
        }
        FloatBuffer that = (FloatBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (Float.compare(this.get(i), that.get(j)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(FloatBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Float.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }
}
