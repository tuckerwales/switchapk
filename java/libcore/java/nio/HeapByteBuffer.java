package java.nio;

// Extends MappedByteBuffer so FileChannel.map can hand out heap copies of file regions.
final class HeapByteBuffer extends MappedByteBuffer {
    HeapByteBuffer(byte[] hb, int offset, int cap) {
        super(-1, 0, cap, cap, hb, offset);
    }

    HeapByteBuffer(byte[] hb, int mark, int pos, int lim, int cap, int offset) {
        super(mark, pos, lim, cap, hb, offset);
    }

    public ByteBuffer slice() {
        HeapByteBuffer b = new HeapByteBuffer(hb(), -1, 0, remaining(), remaining(), byteOffset + position());
        b.readOnly = readOnly;
        b.direct = direct;
        return b;
    }

    public ByteBuffer duplicate() {
        HeapByteBuffer b = new HeapByteBuffer(hb(), markValue(), position(), limit(), capacity(), byteOffset);
        b.readOnly = readOnly;
        b.direct = direct;
        b.bigEndian = bigEndian;
        return b;
    }

    public ByteBuffer asReadOnlyBuffer() {
        ByteBuffer b = duplicate();
        b.readOnly = true;
        return b;
    }
}
