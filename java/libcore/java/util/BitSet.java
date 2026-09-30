package java.util;

public class BitSet implements Cloneable, java.io.Serializable {
    private long[] words;

    public BitSet() {
        words = new long[1];
    }

    public BitSet(int nbits) {
        if (nbits < 0) {
            throw new NegativeArraySizeException("nbits < 0: " + nbits);
        }
        words = new long[Math.max(1, (nbits + 63) >>> 6)];
    }

    private void ensure(int wordIndex) {
        if (wordIndex >= words.length) {
            words = Arrays.copyOf(words, Math.max(2 * words.length, wordIndex + 1));
        }
    }

    public void set(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
        }
        ensure(bitIndex >>> 6);
        words[bitIndex >>> 6] |= (1L << bitIndex);
    }

    public void set(int bitIndex, boolean value) {
        if (value) {
            set(bitIndex);
        } else {
            clear(bitIndex);
        }
    }

    public void set(int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            set(i);
        }
    }

    public void set(int fromIndex, int toIndex, boolean value) {
        for (int i = fromIndex; i < toIndex; i++) {
            set(i, value);
        }
    }

    public void clear(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
        }
        int w = bitIndex >>> 6;
        if (w < words.length) {
            words[w] &= ~(1L << bitIndex);
        }
    }

    public void clear(int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            clear(i);
        }
    }

    public void clear() {
        Arrays.fill(words, 0);
    }

    public void flip(int bitIndex) {
        ensure(bitIndex >>> 6);
        words[bitIndex >>> 6] ^= (1L << bitIndex);
    }

    public void flip(int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            flip(i);
        }
    }

    public boolean get(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
        }
        int w = bitIndex >>> 6;
        return w < words.length && ((words[w] & (1L << bitIndex)) != 0);
    }

    public BitSet get(int fromIndex, int toIndex) {
        BitSet r = new BitSet(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            if (get(i)) {
                r.set(i - fromIndex);
            }
        }
        return r;
    }

    public int nextSetBit(int fromIndex) {
        int n = words.length * 64;
        for (int i = Math.max(fromIndex, 0); i < n; i++) {
            if (get(i)) {
                return i;
            }
        }
        return -1;
    }

    public int nextClearBit(int fromIndex) {
        int i = fromIndex;
        while (get(i)) {
            i++;
        }
        return i;
    }

    public int previousSetBit(int fromIndex) {
        for (int i = fromIndex; i >= 0; i--) {
            if (get(i)) {
                return i;
            }
        }
        return -1;
    }

    public int length() {
        for (int w = words.length - 1; w >= 0; w--) {
            if (words[w] != 0) {
                return w * 64 + (64 - Long.numberOfLeadingZeros(words[w]));
            }
        }
        return 0;
    }

    public int size() {
        return words.length * 64;
    }

    public boolean isEmpty() {
        for (long w : words) {
            if (w != 0) {
                return false;
            }
        }
        return true;
    }

    public int cardinality() {
        int sum = 0;
        for (long w : words) {
            sum += Long.bitCount(w);
        }
        return sum;
    }

    public void and(BitSet set) {
        for (int i = 0; i < words.length; i++) {
            words[i] &= i < set.words.length ? set.words[i] : 0;
        }
    }

    public void or(BitSet set) {
        ensure(set.words.length - 1);
        for (int i = 0; i < set.words.length; i++) {
            words[i] |= set.words[i];
        }
    }

    public void xor(BitSet set) {
        ensure(set.words.length - 1);
        for (int i = 0; i < set.words.length; i++) {
            words[i] ^= set.words[i];
        }
    }

    public void andNot(BitSet set) {
        for (int i = 0; i < Math.min(words.length, set.words.length); i++) {
            words[i] &= ~set.words[i];
        }
    }

    public boolean intersects(BitSet set) {
        for (int i = Math.min(words.length, set.words.length) - 1; i >= 0; i--) {
            if ((words[i] & set.words[i]) != 0) {
                return true;
            }
        }
        return false;
    }

    public long[] toLongArray() {
        return Arrays.copyOf(words, (length() + 63) >>> 6);
    }

    public byte[] toByteArray() {
        int n = (length() + 7) >>> 3;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (words[i >>> 3] >>> ((i & 7) * 8));
        }
        return b;
    }

    public static BitSet valueOf(long[] longs) {
        BitSet b = new BitSet();
        b.words = longs.length == 0 ? new long[1] : longs.clone();
        return b;
    }

    public static BitSet valueOf(byte[] bytes) {
        BitSet b = new BitSet(bytes.length * 8);
        for (int i = 0; i < bytes.length; i++) {
            b.words[i >>> 3] |= (bytes[i] & 0xffL) << ((i & 7) * 8);
        }
        return b;
    }

    public int hashCode() {
        long h = 1234;
        for (int i = words.length; --i >= 0;) {
            h ^= words[i] * (i + 1);
        }
        return (int) ((h >> 32) ^ h);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof BitSet)) {
            return false;
        }
        BitSet set = (BitSet) obj;
        int n = Math.max(words.length, set.words.length);
        for (int i = 0; i < n; i++) {
            long a = i < words.length ? words[i] : 0;
            long b = i < set.words.length ? set.words[i] : 0;
            if (a != b) {
                return false;
            }
        }
        return true;
    }

    public Object clone() {
        BitSet b = new BitSet();
        b.words = words.clone();
        return b;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        int i = nextSetBit(0);
        if (i != -1) {
            b.append(i);
            while ((i = nextSetBit(i + 1)) >= 0) {
                b.append(", ").append(i);
            }
        }
        return b.append('}').toString();
    }
}
