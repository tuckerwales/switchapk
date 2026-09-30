package java.nio;

final class ByteBackedFloatBuffer extends FloatBuffer {
    private final ByteBuffer bb;

    ByteBackedFloatBuffer(ByteBuffer bb) {
        super(-1, 0, bb.remaining() >> 2, bb.remaining() >> 2, bb.backing, bb.byteOffset + bb.position());
        this.bb = bb.duplicate();
        this.bb.position(0);
        this.bb.limit(this.bb.capacity());
        this.bb.order(bb.order());
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    private ByteBackedFloatBuffer(ByteBuffer bb, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, bb.backing, byteOffset);
        this.bb = bb;
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    float getAbs(int i) {
        long v = bb.rawGet((byteOffset - bb.byteOffset) + (i << 2), 4);
        return Float.intBitsToFloat((int) v);
    }

    void putAbs(int i, float v) {
        bb.rawPut((byteOffset - bb.byteOffset) + (i << 2), 4, Float.floatToRawIntBits(v));
    }

    public FloatBuffer slice() {
        return new ByteBackedFloatBuffer(bb, -1, 0, remaining(), remaining(), byteOffset + (position() << 2));
    }

    public FloatBuffer duplicate() {
        return new ByteBackedFloatBuffer(bb, markValue(), position(), limit(), capacity(), byteOffset);
    }

    public FloatBuffer asReadOnlyBuffer() {
        FloatBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return bb.order();
    }
}
