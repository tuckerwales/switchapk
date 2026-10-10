package java.nio;

/** framework-internal: the MappedByteBuffer FileChannel.map returns (public so java.nio.channels can make one). */
public final class HeapMappedByteBuffer extends MappedByteBuffer {
    private HeapMappedByteBuffer(int mark, int pos, int lim, int cap, byte[] hb, int offset, int fd, long filePos,
            int mapBase, int mapLength, boolean writeBack) {
        super(mark, pos, lim, cap, hb, offset, fd, filePos, mapBase, mapLength, writeBack);
    }

    /** A map of bytes read from the file at filePos; writeBack makes force() write them back. */
    public static MappedByteBuffer create(byte[] contents, int fd, long filePos, boolean readOnly, boolean writeBack) {
        HeapMappedByteBuffer b = new HeapMappedByteBuffer(-1, 0, contents.length, contents.length, contents, 0, fd,
                filePos, 0, contents.length, writeBack);
        b.readOnly = readOnly;
        b.direct = true;
        return b;
    }

    private HeapMappedByteBuffer copy(int mark, int pos, int lim, int cap, int offset) {
        HeapMappedByteBuffer b = new HeapMappedByteBuffer(mark, pos, lim, cap, hb(), offset, fd, filePos, mapBase,
                mapLength, writeBack);
        b.readOnly = readOnly;
        b.direct = direct;
        b.bigEndian = bigEndian;
        return b;
    }

    public MappedByteBuffer slice() { return copy(-1, 0, remaining(), remaining(), byteOffset + position()); }

    public MappedByteBuffer slice(int index, int length) {
        if (index < 0 || length < 0 || index > limit() - length) throw new IndexOutOfBoundsException();
        return copy(-1, 0, length, length, byteOffset + index);
    }

    public MappedByteBuffer duplicate() { return copy(markValue(), position(), limit(), capacity(), byteOffset); }

    public ByteBuffer asReadOnlyBuffer() {
        HeapMappedByteBuffer b = copy(markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = true;
        return b;
    }

    public MappedByteBuffer compact() {
        checkWritable();
        int rem = remaining();
        System.arraycopy(hb(), byteOffset + position(), hb(), byteOffset, rem);
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }
}
