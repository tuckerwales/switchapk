package java.nio;

final class ByteBackedDoubleBuffer extends DoubleBuffer {
    private final ByteBuffer bb;

    ByteBackedDoubleBuffer(ByteBuffer bb) {
        super(-1, 0, bb.remaining() >> 3, bb.remaining() >> 3, bb.backing, bb.byteOffset + bb.position());
        this.bb = bb.duplicate();
        this.bb.position(0);
        this.bb.limit(this.bb.capacity());
        this.bb.order(bb.order());
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    private ByteBackedDoubleBuffer(ByteBuffer bb, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, bb.backing, byteOffset);
        this.bb = bb;
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    double getAbs(int i) {
        long v = bb.rawGet((byteOffset - bb.byteOffset) + (i << 3), 8);
        return Double.longBitsToDouble(v);
    }

    void putAbs(int i, double v) {
        bb.rawPut((byteOffset - bb.byteOffset) + (i << 3), 8, Double.doubleToRawLongBits(v));
    }

    public DoubleBuffer slice() {
        return new ByteBackedDoubleBuffer(bb, -1, 0, remaining(), remaining(), byteOffset + (position() << 3));
    }

    public DoubleBuffer duplicate() {
        return new ByteBackedDoubleBuffer(bb, markValue(), position(), limit(), capacity(), byteOffset);
    }

    public DoubleBuffer asReadOnlyBuffer() {
        DoubleBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return bb.order();
    }
}
