package java.nio;

/**
 * A mapped region, held as a heap copy (WS16): READ_ONLY maps are read-only snapshots, PRIVATE maps private copies,
 * and READ_WRITE maps write their bytes back to the file on force(). Unlike a real mapping, writes made to the file
 * through other channels after map() are not seen.
 */
public abstract class MappedByteBuffer extends ByteBuffer {
    // The file region: the backing array's [mapBase, mapBase + mapLength) is the file's [filePos, filePos + mapLength).
    final int fd;
    final long filePos;
    final int mapBase;
    final int mapLength;
    final boolean writeBack;

    MappedByteBuffer(int mark, int pos, int lim, int cap, byte[] hb, int offset, int fd, long filePos, int mapBase,
            int mapLength, boolean writeBack) {
        super(mark, pos, lim, cap, hb, offset);
        this.fd = fd;
        this.filePos = filePos;
        this.mapBase = mapBase;
        this.mapLength = mapLength;
        this.writeBack = writeBack;
    }

    public final boolean isLoaded() { return true; }
    public final MappedByteBuffer load() { return this; }
    public final MappedByteBuffer force() { return force(0, capacity()); }

    public final MappedByteBuffer force(int index, int length) {
        if (index < 0 || length < 0 || index > capacity() - length) throw new IndexOutOfBoundsException();
        if (!writeBack || length == 0) return this;
        int from = byteOffset + index;
        try {
            long saved = libcore.io.Os.seek(fd, 0, libcore.io.Os.SEEK_CUR);
            libcore.io.Os.seek(fd, filePos + (from - mapBase), libcore.io.Os.SEEK_SET);
            libcore.io.Os.write(fd, hb(), from, length);
            libcore.io.Os.seek(fd, saved, libcore.io.Os.SEEK_SET);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return this;
    }

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
