package java.nio;

final class HeapDoubleBuffer extends DoubleBuffer {
    HeapDoubleBuffer(double[] a, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, a, byteOffset);
    }

    private double[] a() {
        return (double[]) backing;
    }

    double getAbs(int i) {
        return a()[(byteOffset >> 3) + i];
    }

    void putAbs(int i, double v) {
        a()[(byteOffset >> 3) + i] = v;
    }

    public DoubleBuffer slice() {
        HeapDoubleBuffer b = new HeapDoubleBuffer(a(), -1, 0, remaining(), remaining(), byteOffset + (position() << 3));
        b.readOnly = readOnly;
        return b;
    }

    public DoubleBuffer duplicate() {
        HeapDoubleBuffer b = new HeapDoubleBuffer(a(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        return b;
    }

    public DoubleBuffer asReadOnlyBuffer() {
        DoubleBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }
}
