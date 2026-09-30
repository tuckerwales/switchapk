package java.nio;

final class HeapLongBuffer extends LongBuffer {
    HeapLongBuffer(long[] a, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, a, byteOffset);
    }

    private long[] a() {
        return (long[]) backing;
    }

    long getAbs(int i) {
        return a()[(byteOffset >> 3) + i];
    }

    void putAbs(int i, long v) {
        a()[(byteOffset >> 3) + i] = v;
    }

    public LongBuffer slice() {
        HeapLongBuffer b = new HeapLongBuffer(a(), -1, 0, remaining(), remaining(), byteOffset + (position() << 3));
        b.readOnly = readOnly;
        return b;
    }

    public LongBuffer duplicate() {
        HeapLongBuffer b = new HeapLongBuffer(a(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        return b;
    }

    public LongBuffer asReadOnlyBuffer() {
        LongBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }
}
