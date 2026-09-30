package java.nio;

final class ByteBackedCharBuffer extends CharBuffer {
    private final ByteBuffer bb;

    ByteBackedCharBuffer(ByteBuffer bb) {
        super(-1, 0, bb.remaining() >> 1, bb.remaining() >> 1, bb.backing, bb.byteOffset + bb.position());
        this.bb = bb.duplicate();
        this.bb.position(0);
        this.bb.limit(this.bb.capacity());
        this.bb.order(bb.order());
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    private ByteBackedCharBuffer(ByteBuffer bb, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, bb.backing, byteOffset);
        this.bb = bb;
        this.readOnly = bb.isReadOnly();
        this.direct = bb.isDirect();
    }

    char getAbs(int i) {
        long v = bb.rawGet((byteOffset - bb.byteOffset) + (i << 1), 2);
        return (char) v;
    }

    void putAbs(int i, char v) {
        bb.rawPut((byteOffset - bb.byteOffset) + (i << 1), 2, v);
    }

    public CharBuffer slice() {
        return new ByteBackedCharBuffer(bb, -1, 0, remaining(), remaining(), byteOffset + (position() << 1));
    }

    public CharBuffer duplicate() {
        return new ByteBackedCharBuffer(bb, markValue(), position(), limit(), capacity(), byteOffset);
    }

    public CharBuffer asReadOnlyBuffer() {
        CharBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return bb.order();
    }
}
