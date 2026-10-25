package java.nio;

final class ByteBackedLongBuffer extends LongBuffer {
    private final ByteBuffer bb;

    ByteBackedLongBuffer(ByteBuffer bb) {
        super(-1, 0, bb.remaining() >> 3, bb.remaining() >> 3, bb.backing, bb.byteOffset + bb.position());
        this.bb = bb.duplicate();
        this.bb.position(0);
        this.bb.limit(this.bb.capacity());
        this.bb.order(bb.order());
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    private ByteBackedLongBuffer(ByteBuffer bb, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, bb.backing, byteOffset);
        this.bb = bb;
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    long getAbs(int i) {
        return peekLong(backing, byteOffset + (i << 3), bb.bigEndian);
    }

    void putAbs(int i, long v) {
        pokeLong(backing, byteOffset + (i << 3), v, bb.bigEndian);
    }

    public LongBuffer slice() {
        return new ByteBackedLongBuffer(bb, -1, 0, remaining(), remaining(), byteOffset + (position() << 3));
    }

    public LongBuffer duplicate() {
        return new ByteBackedLongBuffer(bb, markValue(), position(), limit(), capacity(), byteOffset);
    }

    public LongBuffer asReadOnlyBuffer() {
        LongBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return bb.order();
    }
}
