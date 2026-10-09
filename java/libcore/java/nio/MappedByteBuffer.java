package java.nio;

/* Mappings are heap copies of the file region (direct, like allocateDirect). READ_WRITE changes reach
 * the file on force() and when the channel that made them is closed. */
public abstract class MappedByteBuffer extends ByteBuffer {
    MappedByteBuffer(int mark, int pos, int lim, int cap, byte[] hb, int offset) {
        super(mark, pos, lim, cap, hb, offset);
        direct = true;
    }

    public final boolean isLoaded() {
        return true;
    }

    public final MappedByteBuffer load() {
        return this;
    }

    public final MappedByteBuffer force() {
        return force(0, capacity());
    }

    public final MappedByteBuffer force(int index, int length) {
        if (index < 0 || length < 0 || index > capacity() - length) {
            throw new IndexOutOfBoundsException("index " + index + " length " + length + " capacity " + capacity());
        }
        writeBack(index, length);
        return this;
    }

    abstract void writeBack(int index, int length);

    public final MappedByteBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public final MappedByteBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public final MappedByteBuffer mark() {
        super.mark();
        return this;
    }

    public final MappedByteBuffer reset() {
        super.reset();
        return this;
    }

    public final MappedByteBuffer clear() {
        super.clear();
        return this;
    }

    public final MappedByteBuffer flip() {
        super.flip();
        return this;
    }

    public final MappedByteBuffer rewind() {
        super.rewind();
        return this;
    }

    public abstract MappedByteBuffer slice();

    public abstract MappedByteBuffer slice(int index, int length);

    public abstract MappedByteBuffer duplicate();

    public abstract MappedByteBuffer compact();
}
