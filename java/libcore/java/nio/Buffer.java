package java.nio;

/**
 * Base buffer. Storage is always a Java primitive array ({@code backing}); the
 * VM never moves objects, so native code can address a buffer as
 * array data + {@code byteOffset} + (index << {@code elementSizeShift}).
 */
public abstract class Buffer {
    private int mark = -1;
    int position = 0;
    int limit;
    int capacity;
    /** Primitive array holding the data. */
    final Object backing;
    /** Offset in bytes of element 0 inside backing. */
    final int byteOffset;
    final int elementSizeShift;
    boolean readOnly;
    boolean direct;

    Buffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset, int elementSizeShift) {
        if (cap < 0) {
            throw new IllegalArgumentException("Negative capacity: " + cap);
        }
        this.capacity = cap;
        this.backing = backing;
        this.byteOffset = byteOffset;
        this.elementSizeShift = elementSizeShift;
        limit(lim);
        position(pos);
        if (mark >= 0) {
            if (mark > pos) {
                throw new IllegalArgumentException();
            }
            this.mark = mark;
        }
    }

    public final int capacity() {
        return capacity;
    }

    public final int position() {
        return position;
    }

    public Buffer position(int newPosition) {
        if ((newPosition > limit) || (newPosition < 0)) {
            throw new IllegalArgumentException("Bad position " + newPosition + "/" + limit);
        }
        position = newPosition;
        if (mark > position) {
            mark = -1;
        }
        return this;
    }

    public final int limit() {
        return limit;
    }

    public Buffer limit(int newLimit) {
        if ((newLimit > capacity) || (newLimit < 0)) {
            throw new IllegalArgumentException();
        }
        limit = newLimit;
        if (position > limit) {
            position = limit;
        }
        if (mark > limit) {
            mark = -1;
        }
        return this;
    }

    public Buffer mark() {
        mark = position;
        return this;
    }

    public Buffer reset() {
        int m = mark;
        if (m < 0) {
            throw new InvalidMarkException();
        }
        position = m;
        return this;
    }

    public Buffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }

    public Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }

    public Buffer rewind() {
        position = 0;
        mark = -1;
        return this;
    }

    public final int remaining() {
        int rem = limit - position;
        return rem > 0 ? rem : 0;
    }

    public final boolean hasRemaining() {
        return position < limit;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean hasArray() {
        return !readOnly && !direct && arrayMatchesType();
    }

    abstract boolean arrayMatchesType();

    public Object array() {
        if (!hasArray()) {
            throw new UnsupportedOperationException();
        }
        return backing;
    }

    public int arrayOffset() {
        if (!hasArray()) {
            throw new UnsupportedOperationException();
        }
        return byteOffset >> elementSizeShift;
    }

    public boolean isDirect() {
        return direct;
    }

    public abstract Buffer slice();

    public abstract Buffer duplicate();

    final int nextGetIndex() {
        if (position >= limit) {
            throw new BufferUnderflowException();
        }
        return position++;
    }

    final int nextGetIndex(int nb) {
        if (limit - position < nb) {
            throw new BufferUnderflowException();
        }
        int p = position;
        position += nb;
        return p;
    }

    final int nextPutIndex() {
        if (position >= limit) {
            throw new BufferOverflowException();
        }
        return position++;
    }

    final int nextPutIndex(int nb) {
        if (limit - position < nb) {
            throw new BufferOverflowException();
        }
        int p = position;
        position += nb;
        return p;
    }

    final int checkIndex(int i) {
        if ((i < 0) || (i >= limit)) {
            throw new IndexOutOfBoundsException("index=" + i + " out of bounds (limit=" + limit + ")");
        }
        return i;
    }

    final int checkIndex(int i, int nb) {
        if ((i < 0) || (nb > limit - i)) {
            throw new IndexOutOfBoundsException("index=" + i + " out of bounds (limit=" + limit + ", nb=" + nb + ")");
        }
        return i;
    }

    final void checkWritable() {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
    }

    final int markValue() {
        return mark;
    }

    final void discardMark() {
        mark = -1;
    }

    public String toString() {
        return getClass().getName() + "[pos=" + position + " lim=" + limit + " cap=" + capacity + "]";
    }
}
