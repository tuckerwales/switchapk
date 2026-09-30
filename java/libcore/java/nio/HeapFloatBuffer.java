package java.nio;

final class HeapFloatBuffer extends FloatBuffer {
    HeapFloatBuffer(float[] a, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, a, byteOffset);
    }

    private float[] a() {
        return (float[]) backing;
    }

    float getAbs(int i) {
        return a()[(byteOffset >> 2) + i];
    }

    void putAbs(int i, float v) {
        a()[(byteOffset >> 2) + i] = v;
    }

    public FloatBuffer slice() {
        HeapFloatBuffer b = new HeapFloatBuffer(a(), -1, 0, remaining(), remaining(), byteOffset + (position() << 2));
        b.readOnly = readOnly;
        return b;
    }

    public FloatBuffer duplicate() {
        HeapFloatBuffer b = new HeapFloatBuffer(a(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        return b;
    }

    public FloatBuffer asReadOnlyBuffer() {
        FloatBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }
}
