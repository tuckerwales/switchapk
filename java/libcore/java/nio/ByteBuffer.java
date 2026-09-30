package java.nio;

public abstract class ByteBuffer extends Buffer implements Comparable<ByteBuffer> {
    boolean bigEndian = true;

    ByteBuffer(int mark, int pos, int lim, int cap, byte[] hb, int offset) {
        super(mark, pos, lim, cap, hb, offset, 0);
    }

    final byte[] hb() {
        return (byte[]) backing;
    }

    public static ByteBuffer allocateDirect(int capacity) {
        ByteBuffer b = new HeapByteBuffer(new byte[capacity], 0, capacity);
        b.direct = true;
        return b;
    }

    public static ByteBuffer allocate(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity < 0: " + capacity);
        }
        return new HeapByteBuffer(new byte[capacity], 0, capacity);
    }

    public static ByteBuffer wrap(byte[] array, int offset, int length) {
        HeapByteBuffer b = new HeapByteBuffer(array, 0, array.length);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static ByteBuffer wrap(byte[] array) {
        return wrap(array, 0, array.length);
    }

    boolean arrayMatchesType() {
        return true;
    }

    public final byte[] array() {
        return (byte[]) super.array();
    }

    public abstract ByteBuffer slice();

    public ByteBuffer slice(int index, int length) {
        ByteBuffer d = duplicate();
        d.position(index);
        d.limit(index + length);
        return d.slice();
    }

    public abstract ByteBuffer duplicate();

    public abstract ByteBuffer asReadOnlyBuffer();

    public byte get() {
        return hb()[byteOffset + nextGetIndex()];
    }

    public ByteBuffer put(byte b) {
        checkWritable();
        hb()[byteOffset + nextPutIndex()] = b;
        return this;
    }

    public byte get(int index) {
        return hb()[byteOffset + checkIndex(index)];
    }

    public ByteBuffer put(int index, byte b) {
        checkWritable();
        hb()[byteOffset + checkIndex(index)] = b;
        return this;
    }

    public ByteBuffer get(byte[] dst, int offset, int length) {
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        System.arraycopy(hb(), byteOffset + position, dst, offset, length);
        position += length;
        return this;
    }

    public ByteBuffer get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    public ByteBuffer get(int index, byte[] dst, int offset, int length) {
        checkIndex(index, length);
        System.arraycopy(hb(), byteOffset + index, dst, offset, length);
        return this;
    }

    public ByteBuffer put(ByteBuffer src) {
        if (src == this) {
            throw new IllegalArgumentException();
        }
        int n = src.remaining();
        if (n > remaining()) {
            throw new BufferOverflowException();
        }
        checkWritable();
        System.arraycopy(src.hb(), src.byteOffset + src.position, hb(), byteOffset + position, n);
        src.position += n;
        position += n;
        return this;
    }

    public ByteBuffer put(byte[] src, int offset, int length) {
        checkWritable();
        if (length > remaining()) {
            throw new BufferOverflowException();
        }
        System.arraycopy(src, offset, hb(), byteOffset + position, length);
        position += length;
        return this;
    }

    public final ByteBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    public ByteBuffer put(int index, byte[] src, int offset, int length) {
        checkWritable();
        checkIndex(index, length);
        System.arraycopy(src, offset, hb(), byteOffset + index, length);
        return this;
    }

    public ByteBuffer compact() {
        checkWritable();
        int rem = remaining();
        System.arraycopy(hb(), byteOffset + position, hb(), byteOffset, rem);
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }

    public final ByteOrder order() {
        return bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
    }

    public final ByteBuffer order(ByteOrder bo) {
        bigEndian = (bo == ByteOrder.BIG_ENDIAN);
        return this;
    }

    public ByteBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public ByteBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public ByteBuffer mark() {
        super.mark();
        return this;
    }

    public ByteBuffer reset() {
        super.reset();
        return this;
    }

    public ByteBuffer clear() {
        super.clear();
        return this;
    }

    public ByteBuffer flip() {
        super.flip();
        return this;
    }

    public ByteBuffer rewind() {
        super.rewind();
        return this;
    }

    public int hashCode() {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--) {
            h = 31 * h + get(i);
        }
        return h;
    }

    public boolean equals(Object ob) {
        if (this == ob) {
            return true;
        }
        if (!(ob instanceof ByteBuffer)) {
            return false;
        }
        ByteBuffer that = (ByteBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (this.get(i) != that.get(j)) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(ByteBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Byte.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }

    /* ---- multi-byte access ---- */

    final long rawGet(int abs, int n) {
        byte[] a = hb();
        int base = byteOffset + abs;
        long v = 0;
        if (bigEndian) {
            for (int i = 0; i < n; i++) {
                v = (v << 8) | (a[base + i] & 0xff);
            }
        } else {
            for (int i = n - 1; i >= 0; i--) {
                v = (v << 8) | (a[base + i] & 0xff);
            }
        }
        return v;
    }

    final void rawPut(int abs, int n, long v) {
        checkWritable();
        byte[] a = hb();
        int base = byteOffset + abs;
        if (bigEndian) {
            for (int i = n - 1; i >= 0; i--) {
                a[base + i] = (byte) v;
                v >>= 8;
            }
        } else {
            for (int i = 0; i < n; i++) {
                a[base + i] = (byte) v;
                v >>= 8;
            }
        }
    }

    public char getChar() {
        return (char) rawGet(nextGetIndex(2), 2);
    }

    public ByteBuffer putChar(char value) {
        rawPut(nextPutIndex(2), 2, value);
        return this;
    }

    public char getChar(int index) {
        return (char) rawGet(checkIndex(index, 2), 2);
    }

    public ByteBuffer putChar(int index, char value) {
        rawPut(checkIndex(index, 2), 2, value);
        return this;
    }

    public short getShort() {
        return (short) rawGet(nextGetIndex(2), 2);
    }

    public ByteBuffer putShort(short value) {
        rawPut(nextPutIndex(2), 2, value);
        return this;
    }

    public short getShort(int index) {
        return (short) rawGet(checkIndex(index, 2), 2);
    }

    public ByteBuffer putShort(int index, short value) {
        rawPut(checkIndex(index, 2), 2, value);
        return this;
    }

    public int getInt() {
        return (int) rawGet(nextGetIndex(4), 4);
    }

    public ByteBuffer putInt(int value) {
        rawPut(nextPutIndex(4), 4, value);
        return this;
    }

    public int getInt(int index) {
        return (int) rawGet(checkIndex(index, 4), 4);
    }

    public ByteBuffer putInt(int index, int value) {
        rawPut(checkIndex(index, 4), 4, value);
        return this;
    }

    public long getLong() {
        return rawGet(nextGetIndex(8), 8);
    }

    public ByteBuffer putLong(long value) {
        rawPut(nextPutIndex(8), 8, value);
        return this;
    }

    public long getLong(int index) {
        return rawGet(checkIndex(index, 8), 8);
    }

    public ByteBuffer putLong(int index, long value) {
        rawPut(checkIndex(index, 8), 8, value);
        return this;
    }

    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    public ByteBuffer putFloat(float value) {
        return putInt(Float.floatToRawIntBits(value));
    }

    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    public ByteBuffer putFloat(int index, float value) {
        return putInt(index, Float.floatToRawIntBits(value));
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public ByteBuffer putDouble(double value) {
        return putLong(Double.doubleToRawLongBits(value));
    }

    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    public ByteBuffer putDouble(int index, double value) {
        return putLong(index, Double.doubleToRawLongBits(value));
    }

    public CharBuffer asCharBuffer() {
        return new ByteBackedCharBuffer(this);
    }

    public ShortBuffer asShortBuffer() {
        return new ByteBackedShortBuffer(this);
    }

    public IntBuffer asIntBuffer() {
        return new ByteBackedIntBuffer(this);
    }

    public LongBuffer asLongBuffer() {
        return new ByteBackedLongBuffer(this);
    }

    public FloatBuffer asFloatBuffer() {
        return new ByteBackedFloatBuffer(this);
    }

    public DoubleBuffer asDoubleBuffer() {
        return new ByteBackedDoubleBuffer(this);
    }
}
