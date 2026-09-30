package java.io;

import java.util.Arrays;

public class ByteArrayOutputStream extends OutputStream {
    protected byte[] buf;
    protected int count;

    public ByteArrayOutputStream() {
        this(32);
    }

    public ByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        buf = new byte[size];
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - buf.length > 0) {
            int newCapacity = Math.max(buf.length << 1, minCapacity);
            buf = Arrays.copyOf(buf, newCapacity);
        }
    }

    public synchronized void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    public synchronized void write(byte[] b, int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void writeBytes(byte[] b) {
        write(b, 0, b.length);
    }

    public synchronized void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    public synchronized void reset() {
        count = 0;
    }

    public synchronized byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    public synchronized int size() {
        return count;
    }

    public synchronized String toString() {
        return new String(buf, 0, count);
    }

    public synchronized String toString(String charsetName) throws UnsupportedEncodingException {
        return new String(buf, 0, count, charsetName);
    }

    public synchronized String toString(java.nio.charset.Charset charset) {
        return new String(buf, 0, count, charset);
    }

    public void close() throws IOException {
    }
}
