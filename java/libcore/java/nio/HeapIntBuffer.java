package java.nio;

final class HeapIntBuffer extends IntBuffer {
    HeapIntBuffer(int[] a, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, a, byteOffset);
    }

    private int[] a() {
        return (int[]) backing;
    }

    int getAbs(int i) {
        return a()[(byteOffset >> 2) + i];
    }

    void putAbs(int i, int v) {
        a()[(byteOffset >> 2) + i] = v;
    }

    public IntBuffer slice() {
        HeapIntBuffer b = new HeapIntBuffer(a(), -1, 0, remaining(), remaining(), byteOffset + (position() << 2));
        b.readOnly = readOnly;
        return b;
    }

    public IntBuffer duplicate() {
        HeapIntBuffer b = new HeapIntBuffer(a(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        return b;
    }

    public IntBuffer asReadOnlyBuffer() {
        IntBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }
}
