package java.nio;

public abstract class CharBuffer extends Buffer implements Comparable<CharBuffer>, CharSequence, Appendable, Readable {
    CharBuffer(int mark, int pos, int lim, int cap, Object backing, int byteOffset) {
        super(mark, pos, lim, cap, backing, byteOffset, 1);
    }

    public static CharBuffer allocate(int capacity) {
        return new HeapCharBuffer(new char[capacity], -1, 0, capacity, capacity, 0);
    }

    public static CharBuffer wrap(char[] array, int offset, int length) {
        CharBuffer b = new HeapCharBuffer(array, -1, 0, array.length, array.length, 0);
        b.position(offset);
        b.limit(offset + length);
        return b;
    }

    public static CharBuffer wrap(char[] array) {
        return wrap(array, 0, array.length);
    }

    abstract char getAbs(int i);

    abstract void putAbs(int i, char v);

    /* bytes per element when this buffer's order is not the platform's (a byte-backed view), else 0 */
    final int nativeSwap() {
        return order() == ByteOrder.nativeOrder() ? 0 : 2;
    }

    public abstract CharBuffer slice();

    public abstract CharBuffer duplicate();

    public abstract CharBuffer asReadOnlyBuffer();

    public abstract ByteOrder order();

    /* the index checks are written out: a call costs about as much as the access itself */
    public char get() {
        int p = position;
        if (p >= limit) {
            throw new BufferUnderflowException();
        }
        position = p + 1;
        return getAbs(p);
    }

    public CharBuffer put(char v) {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        int p = position;
        if (p >= limit) {
            throw new BufferOverflowException();
        }
        position = p + 1;
        putAbs(p, v);
        return this;
    }

    public char get(int index) {
        if (index < 0 || index >= limit) {
            checkIndex(index);
        }
        return getAbs(index);
    }

    public CharBuffer put(int index, char v) {
        if (readOnly) {
            throw new ReadOnlyBufferException();
        }
        if (index < 0 || index >= limit) {
            checkIndex(index);
        }
        putAbs(index, v);
        return this;
    }

    public CharBuffer get(char[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if (length > remaining()) {
            throw new BufferUnderflowException();
        }
        copyMemory(backing, byteOffset + (position << 1), dst, offset << 1, length << 1, nativeSwap());
        position += length;
        return this;
    }

    public CharBuffer get(char[] dst) {
        return get(dst, 0, dst.length);
    }

    public CharBuffer put(CharBuffer src) {
        if (src == this) {
            throw new IllegalArgumentException();
        }
        int n = src.remaining();
        if (n > remaining()) {
            throw new BufferOverflowException();
        }
        checkWritable();
        copyMemory(src.backing, src.byteOffset + (src.position << 1), backing, byteOffset + (position << 1), n << 1,
                src.order() == order() ? 0 : 2);
        src.position += n;
        position += n;
        return this;
    }

    public CharBuffer put(char[] src, int offset, int length) {
        checkWritable();
        checkBounds(offset, length, src.length);
        if (length > remaining()) {
            throw new BufferOverflowException();
        }
        copyMemory(src, offset << 1, backing, byteOffset + (position << 1), length << 1, nativeSwap());
        position += length;
        return this;
    }

    public final CharBuffer put(char[] src) {
        return put(src, 0, src.length);
    }

    public CharBuffer compact() {
        checkWritable();
        int rem = remaining();
        copyMemory(backing, byteOffset + (position << 1), backing, byteOffset, rem << 1, 0);
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }

    boolean arrayMatchesType() {
        return backing instanceof char[];
    }

    public final char[] array() {
        return (char[]) super.array();
    }

    public CharBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    public CharBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    public CharBuffer mark() {
        super.mark();
        return this;
    }

    public CharBuffer reset() {
        super.reset();
        return this;
    }

    public CharBuffer clear() {
        super.clear();
        return this;
    }

    public CharBuffer flip() {
        super.flip();
        return this;
    }

    public CharBuffer rewind() {
        super.rewind();
        return this;
    }

    public int hashCode() {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--) {
            h = 31 * h + (int) get(i);
        }
        return h;
    }

    public boolean equals(Object ob) {
        if (this == ob) {
            return true;
        }
        if (!(ob instanceof CharBuffer)) {
            return false;
        }
        CharBuffer that = (CharBuffer) ob;
        if (this.remaining() != that.remaining()) {
            return false;
        }
        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            if (Character.compare(this.get(i), that.get(j)) != 0) {
                return false;
            }
        }
        return true;
    }

    public int compareTo(CharBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            int cmp = Character.compare(this.get(i), that.get(j));
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.remaining() - that.remaining();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(remaining());
        for (int i = position(); i < limit(); i++) {
            sb.append(get(i));
        }
        return sb.toString();
    }

    public CharBuffer put(String src) {
        for (int i = 0; i < src.length(); i++) {
            put(src.charAt(i));
        }
        return this;
    }

    public static CharBuffer wrap(CharSequence csq) {
        return wrap(csq.toString().toCharArray());
    }

    public int length() {
        return remaining();
    }

    public char charAt(int index) {
        return get(position() + checkIndex(index));
    }

    public CharSequence subSequence(int start, int end) {
        return toString().substring(start, end);
    }

    public CharBuffer append(CharSequence csq) {
        return put(String.valueOf(csq));
    }

    public CharBuffer append(CharSequence csq, int start, int end) {
        return put(String.valueOf(csq).substring(start, end));
    }

    public CharBuffer append(char c) {
        return put(c);
    }

    public int read(CharBuffer target) {
        int n = Math.min(remaining(), target.remaining());
        for (int i = 0; i < n; i++) {
            target.put(get());
        }
        return n == 0 && !hasRemaining() ? -1 : n;
    }
}
