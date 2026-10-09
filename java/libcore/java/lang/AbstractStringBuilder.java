package java.lang;

abstract class AbstractStringBuilder implements Appendable, CharSequence {
    char[] value;
    int count;

    AbstractStringBuilder() {
        value = new char[16];
    }

    AbstractStringBuilder(int capacity) {
        if (capacity < 0) {
            throw new NegativeArraySizeException(Integer.toString(capacity));
        }
        value = new char[capacity];
    }

    public int length() {
        return count;
    }

    public int capacity() {
        return value.length;
    }

    public void ensureCapacity(int min) {
        if (min > value.length) {
            int n = value.length * 2 + 2;
            if (n < min) {
                n = min;
            }
            char[] v = new char[n];
            System.arraycopy(value, 0, v, 0, count);
            value = v;
        }
    }

    public void trimToSize() {
        if (count < value.length) {
            char[] v = new char[count];
            System.arraycopy(value, 0, v, 0, count);
            value = v;
        }
    }

    public void setLength(int newLength) {
        if (newLength < 0) {
            throw new StringIndexOutOfBoundsException(newLength);
        }
        ensureCapacity(newLength);
        for (int i = count; i < newLength; i++) {
            value[i] = 0;
        }
        count = newLength;
    }

    public char charAt(int index) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException("index " + index + ",length " + count);
        }
        return value[index];
    }

    public int codePointAt(int index) {
        return Character.codePointAt(value, index, count);
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcBegin < 0 || srcEnd > count || srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        System.arraycopy(value, srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    public void setCharAt(int index, char ch) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(index);
        }
        value[index] = ch;
    }

    AbstractStringBuilder appendNull() {
        ensureCapacity(count + 4);
        value[count++] = 'n';
        value[count++] = 'u';
        value[count++] = 'l';
        value[count++] = 'l';
        return this;
    }

    public AbstractStringBuilder append(Object obj) {
        return append(String.valueOf(obj));
    }

    public AbstractStringBuilder append(String str) {
        if (str == null) {
            return appendNull();
        }
        int len = str.length();
        ensureCapacity(count + len);
        str.getChars(0, len, value, count);
        count += len;
        return this;
    }

    public AbstractStringBuilder append(StringBuffer sb) {
        return append(sb == null ? (String) null : sb.toString());
    }

    public AbstractStringBuilder append(CharSequence s) {
        if (s == null) {
            return appendNull();
        }
        if (s instanceof String) {
            return append((String) s);
        }
        return append(s, 0, s.length());
    }

    public AbstractStringBuilder append(CharSequence s, int start, int end) {
        if (s == null) {
            s = "null";
        }
        if (start < 0 || start > end || end > s.length()) {
            throw new IndexOutOfBoundsException("start " + start + ", end " + end + ", length " + s.length());
        }
        ensureCapacity(count + end - start);
        for (int i = start; i < end; i++) {
            value[count++] = s.charAt(i);
        }
        return this;
    }

    public AbstractStringBuilder append(char[] str) {
        ensureCapacity(count + str.length);
        System.arraycopy(str, 0, value, count, str.length);
        count += str.length;
        return this;
    }

    public AbstractStringBuilder append(char[] str, int offset, int len) {
        ensureCapacity(count + len);
        System.arraycopy(str, offset, value, count, len);
        count += len;
        return this;
    }

    public AbstractStringBuilder append(boolean b) {
        return append(b ? "true" : "false");
    }

    public AbstractStringBuilder append(char c) {
        if (count == value.length) {
            ensureCapacity(count + 1);
        }
        value[count++] = c;
        return this;
    }

    public AbstractStringBuilder append(int i) {
        return append(Integer.toString(i));
    }

    public AbstractStringBuilder appendCodePoint(int codePoint) {
        if (Character.isBmpCodePoint(codePoint)) {
            return append((char) codePoint);
        }
        return append(Character.toChars(codePoint));
    }

    public AbstractStringBuilder append(long l) {
        return append(Long.toString(l));
    }

    public AbstractStringBuilder append(float f) {
        return append(Float.toString(f));
    }

    public AbstractStringBuilder append(double d) {
        return append(Double.toString(d));
    }

    public AbstractStringBuilder delete(int start, int end) {
        if (end > count) {
            end = count;
        }
        if (start < 0 || start > end) {
            throw new StringIndexOutOfBoundsException("start " + start + ", end " + end + ", length " + count);
        }
        int len = end - start;
        if (len > 0) {
            System.arraycopy(value, start + len, value, start, count - end);
            count -= len;
        }
        return this;
    }

    public AbstractStringBuilder deleteCharAt(int index) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(index);
        }
        System.arraycopy(value, index + 1, value, index, count - index - 1);
        count--;
        return this;
    }

    public AbstractStringBuilder replace(int start, int end, String str) {
        if (end > count) {
            end = count;
        }
        if (start < 0 || start > end) {
            throw new StringIndexOutOfBoundsException(start);
        }
        int len = str.length();
        int newCount = count + len - (end - start);
        ensureCapacity(newCount);
        System.arraycopy(value, end, value, start + len, count - end);
        str.getChars(0, len, value, start);
        count = newCount;
        return this;
    }

    public String substring(int start) {
        return substring(start, count);
    }

    public CharSequence subSequence(int start, int end) {
        return substring(start, end);
    }

    public String substring(int start, int end) {
        if (start < 0 || end > count || start > end) {
            throw new StringIndexOutOfBoundsException("start " + start + ", end " + end + ", length " + count);
        }
        return new String(value, start, end - start);
    }

    public AbstractStringBuilder insert(int offset, String str) {
        if (offset < 0 || offset > count) {
            throw new StringIndexOutOfBoundsException(offset);
        }
        if (str == null) {
            str = "null";
        }
        int len = str.length();
        ensureCapacity(count + len);
        System.arraycopy(value, offset, value, offset + len, count - offset);
        str.getChars(0, len, value, offset);
        count += len;
        return this;
    }

    public AbstractStringBuilder insert(int offset, char[] str) {
        return insert(offset, new String(str));
    }

    public AbstractStringBuilder insert(int index, char[] str, int offset, int len) {
        return insert(index, new String(str, offset, len));
    }

    public AbstractStringBuilder insert(int offset, Object obj) {
        return insert(offset, String.valueOf(obj));
    }

    public AbstractStringBuilder insert(int offset, CharSequence s) {
        return insert(offset, String.valueOf(s));
    }

    public AbstractStringBuilder insert(int offset, boolean b) {
        return insert(offset, String.valueOf(b));
    }

    public AbstractStringBuilder insert(int offset, char c) {
        return insert(offset, String.valueOf(c));
    }

    public AbstractStringBuilder insert(int offset, int i) {
        return insert(offset, String.valueOf(i));
    }

    public AbstractStringBuilder insert(int offset, long l) {
        return insert(offset, String.valueOf(l));
    }

    public AbstractStringBuilder insert(int offset, float f) {
        return insert(offset, String.valueOf(f));
    }

    public AbstractStringBuilder insert(int offset, double d) {
        return insert(offset, String.valueOf(d));
    }

    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    public int indexOf(String str, int fromIndex) {
        return toString().indexOf(str, fromIndex);
    }

    public int lastIndexOf(String str) {
        return toString().lastIndexOf(str);
    }

    public int lastIndexOf(String str, int fromIndex) {
        return toString().lastIndexOf(str, fromIndex);
    }

    public AbstractStringBuilder reverse() {
        for (int i = 0, j = count - 1; i < j; i++, j--) {
            char c = value[i];
            value[i] = value[j];
            value[j] = c;
        }
        // restore surrogate pair order
        for (int i = 0; i < count - 1; i++) {
            if (Character.isLowSurrogate(value[i]) && Character.isHighSurrogate(value[i + 1])) {
                char c = value[i];
                value[i] = value[i + 1];
                value[i + 1] = c;
                i++;
            }
        }
        return this;
    }

    public abstract String toString();

    public java.util.stream.IntStream chars() {
        return toString().chars();
    }

    public int codePointBefore(int index) {
        int i = index - 1;
        if (i < 0 || i >= count) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return Character.codePointBefore(value, index, 0);
    }

    public int codePointCount(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > count || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }
        return Character.codePointCount(value, beginIndex, endIndex - beginIndex);
    }

    public int offsetByCodePoints(int index, int codePointOffset) {
        if (index < 0 || index > count) {
            throw new IndexOutOfBoundsException();
        }
        return Character.offsetByCodePoints(value, 0, count, index, codePointOffset);
    }

    public AbstractStringBuilder insert(int dstOffset, CharSequence s, int start, int end) {
        if (s == null) {
            s = "null";
        }
        if (dstOffset < 0 || dstOffset > count || start < 0 || end < 0 || start > end || end > s.length()) {
            throw new IndexOutOfBoundsException("dstOffset " + dstOffset + ", start " + start + ", end " + end
                    + ", s.length() " + s.length() + ", length() " + count);
        }
        return insert(dstOffset, s.subSequence(start, end).toString());
    }

    public java.util.stream.IntStream codePoints() {
        return toString().codePoints();
    }
}
