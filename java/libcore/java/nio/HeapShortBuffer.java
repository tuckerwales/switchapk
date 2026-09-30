package java.nio;

final class HeapShortBuffer extends ShortBuffer {
    HeapShortBuffer(short[] a, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, a, byteOffset);
    }

    private short[] a() {
        return (short[]) backing;
    }

    short getAbs(int i) {
        return a()[(byteOffset >> 1) + i];
    }

    void putAbs(int i, short v) {
        a()[(byteOffset >> 1) + i] = v;
    }

    public ShortBuffer slice() {
        HeapShortBuffer b = new HeapShortBuffer(a(), -1, 0, remaining(), remaining(), byteOffset + (position() << 1));
        b.readOnly = readOnly;
        return b;
    }

    public ShortBuffer duplicate() {
        HeapShortBuffer b = new HeapShortBuffer(a(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        return b;
    }

    public ShortBuffer asReadOnlyBuffer() {
        ShortBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }
}
