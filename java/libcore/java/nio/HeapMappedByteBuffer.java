package java.nio;

final class HeapMappedByteBuffer extends MappedByteBuffer {
    /* Shared by a mapping and its slices and duplicates. */
    static final class Region {
        final byte[] data;
        final long filePosition;
        final NIOAccess.WriteBack sink;

        Region(byte[] data, long filePosition, NIOAccess.WriteBack sink) {
            this.data = data;
            this.filePosition = filePosition;
            this.sink = sink;
        }
    }

    private final Region region;

    HeapMappedByteBuffer(Region region, int mark, int pos, int lim, int cap, int offset, boolean readOnly) {
        super(mark, pos, lim, cap, region.data, offset);
        this.region = region;
        this.readOnly = readOnly;
    }

    void writeBack(int index, int length) {
        if (region.sink != null && length > 0) {
            region.sink.write(region.data, byteOffset + index, length, region.filePosition + byteOffset + index);
        }
    }

    public MappedByteBuffer slice() {
        return new HeapMappedByteBuffer(region, -1, 0, remaining(), remaining(), byteOffset + position(), readOnly);
    }

    public MappedByteBuffer slice(int index, int length) {
        checkIndex(index, length);
        return new HeapMappedByteBuffer(region, -1, 0, length, length, byteOffset + index, readOnly);
    }

    public MappedByteBuffer duplicate() {
        HeapMappedByteBuffer b = new HeapMappedByteBuffer(region, markValue(), position(), limit(), capacity(),
                byteOffset, readOnly);
        b.bigEndian = bigEndian;
        return b;
    }

    public ByteBuffer asReadOnlyBuffer() {
        HeapMappedByteBuffer b = new HeapMappedByteBuffer(region, markValue(), position(), limit(), capacity(),
                byteOffset, true);
        b.bigEndian = bigEndian;
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
