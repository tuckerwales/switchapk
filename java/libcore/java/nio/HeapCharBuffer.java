package java.nio;

final class HeapCharBuffer extends CharBuffer {
    HeapCharBuffer(char[] a, int mark, int pos, int lim, int cap, int byteOffset) {
        super(mark, pos, lim, cap, a, byteOffset);
    }

    private char[] a() {
        return (char[]) backing;
    }

    char getAbs(int i) {
        return a()[(byteOffset >> 1) + i];
    }

    void putAbs(int i, char v) {
        a()[(byteOffset >> 1) + i] = v;
    }

    public CharBuffer slice() {
        HeapCharBuffer b = new HeapCharBuffer(a(), -1, 0, remaining(), remaining(), byteOffset + (position() << 1));
        b.readOnly = readOnly;
        return b;
    }

    public CharBuffer duplicate() {
        HeapCharBuffer b = new HeapCharBuffer(a(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        return b;
    }

    public CharBuffer asReadOnlyBuffer() {
        CharBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }

    public ByteOrder order() {
        return ByteOrder.nativeOrder();
    }
}
