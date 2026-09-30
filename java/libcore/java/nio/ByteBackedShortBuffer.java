package java.nio;

final class ByteBackedShortBuffer extends ShortBuffer {
    private final ByteBuffer bb;

    ByteBackedShortBuffer(ByteBuffer bb) {
        super(-1, 0, bb.remaining() >> 1, bb.remaining() >> 1, bb.backing, bb.byteOffset + bb.position());
        this.bb = bb.duplicate();
        this.bb.position(0);
        this.bb.limit(this.bb.capacity());
        this.bb.order(bb.order());
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    private ByteBackedShortBuffer(ByteBuffer bb, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, bb.backing, byteOffset);
        this.bb = bb;
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    short getAbs(int i) {
        long v = bb.rawGet((byteOffset - bb.byteOffset) + (i << 1), 2);
        return (short) v;
    }

    void putAbs(int i, short v) {
        bb.rawPut((byteOffset - bb.byteOffset) + (i << 1), 2, v);
    }

    public ShortBuffer slice() {
        return new ByteBackedShortBuffer(bb, -1, 0, remaining(), remaining(), byteOffset + (position() << 1));
    }

    public ShortBuffer duplicate() {
        return new ByteBackedShortBuffer(bb, markValue(), position(), limit(), capacity(), byteOffset);
    }

    public ShortBuffer asReadOnlyBuffer() {
        ShortBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return bb.order();
    }
}
