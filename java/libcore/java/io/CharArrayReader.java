package java.io;

public class CharArrayReader extends Reader {
    protected char[] buf;
    protected int pos;
    protected int markedPos = 0;
    protected int count;

    public CharArrayReader(char[] buf) {
        this.buf = buf;
        this.pos = 0;
        this.count = buf.length;
    }

    public CharArrayReader(char[] buf, int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.count = Math.min(offset + length, buf.length);
        this.markedPos = offset;
    }

    public int read() throws IOException {
        synchronized (lock) {
            if (pos >= count) {
                return -1;
            }
            return buf[pos++];
        }
    }

    public int read(char[] b, int off, int len) throws IOException {
        synchronized (lock) {
            if (pos >= count) {
                return -1;
            }
            int avail = count - pos;
            if (len > avail) {
                len = avail;
            }
            if (len <= 0) {
                return 0;
            }
            System.arraycopy(buf, pos, b, off, len);
            pos += len;
            return len;
        }
    }

    public boolean ready() {
        return (count - pos) > 0;
    }

    public boolean markSupported() {
        return true;
    }

    public void mark(int readAheadLimit) {
        markedPos = pos;
    }

    public void reset() {
        pos = markedPos;
    }

    public void close() {
        buf = null;
    }
}
