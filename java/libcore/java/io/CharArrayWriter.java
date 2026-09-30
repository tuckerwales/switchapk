package java.io;

import java.util.Arrays;

public class CharArrayWriter extends Writer {
    protected char[] buf;
    protected int count;

    public CharArrayWriter() {
        this(32);
    }

    public CharArrayWriter(int initialSize) {
        buf = new char[initialSize];
    }

    public void write(int c) {
        synchronized (lock) {
            int newcount = count + 1;
            if (newcount > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
            }
            buf[count] = (char) c;
            count = newcount;
        }
    }

    public void write(char[] c, int off, int len) {
        synchronized (lock) {
            int newcount = count + len;
            if (newcount > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
            }
            System.arraycopy(c, off, buf, count, len);
            count = newcount;
        }
    }

    public void write(String str, int off, int len) {
        synchronized (lock) {
            int newcount = count + len;
            if (newcount > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length << 1, newcount));
            }
            str.getChars(off, off + len, buf, count);
            count = newcount;
        }
    }

    public void writeTo(Writer out) throws IOException {
        synchronized (lock) {
            out.write(buf, 0, count);
        }
    }

    public CharArrayWriter append(CharSequence csq) {
        String s = String.valueOf(csq);
        write(s, 0, s.length());
        return this;
    }

    public CharArrayWriter append(char c) {
        write(c);
        return this;
    }

    public void reset() {
        count = 0;
    }

    public char[] toCharArray() {
        synchronized (lock) {
            return Arrays.copyOf(buf, count);
        }
    }

    public int size() {
        return count;
    }

    public String toString() {
        synchronized (lock) {
            return new String(buf, 0, count);
        }
    }

    public void flush() {
    }

    public void close() {
    }
}
