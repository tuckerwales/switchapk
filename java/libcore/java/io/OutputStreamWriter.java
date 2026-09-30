package java.io;

import java.nio.charset.Charset;

public class OutputStreamWriter extends Writer {
    private final OutputStream out;
    private final Charset cs;
    private char pendingHigh;

    public OutputStreamWriter(OutputStream out, String charsetName) throws UnsupportedEncodingException {
        super(out);
        this.out = out;
        try {
            cs = Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(charsetName);
        }
    }

    public OutputStreamWriter(OutputStream out) {
        this(out, Charset.defaultCharset());
    }

    public OutputStreamWriter(OutputStream out, Charset cs) {
        super(out);
        this.out = out;
        this.cs = cs;
    }

    public String getEncoding() {
        return cs.name();
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        synchronized (lock) {
            if (len == 0) {
                return;
            }
            char[] chars = cbuf;
            int start = off;
            int count = len;
            if (pendingHigh != 0) {
                chars = new char[len + 1];
                chars[0] = pendingHigh;
                System.arraycopy(cbuf, off, chars, 1, len);
                start = 0;
                count = len + 1;
                pendingHigh = 0;
            }
            if (Character.isHighSurrogate(chars[start + count - 1])) {
                pendingHigh = chars[start + count - 1];
                count--;
            }
            byte[] b = cs.encodeChars(chars, start, count);
            out.write(b, 0, b.length);
        }
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        flush();
        out.close();
    }
}
