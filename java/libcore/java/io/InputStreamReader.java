package java.io;

import java.nio.charset.Charset;

public class InputStreamReader extends Reader {
    private final InputStream in;
    private final Charset cs;
    private final byte[] bytes = new byte[8192];
    private int bytePos, byteLen;
    private boolean eof;
    private int pendingLow = -1;

    public InputStreamReader(InputStream in) {
        this(in, Charset.defaultCharset());
    }

    public InputStreamReader(InputStream in, String charsetName) throws UnsupportedEncodingException {
        super(in);
        if (charsetName == null) {
            throw new NullPointerException("charsetName");
        }
        this.in = in;
        try {
            this.cs = Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(charsetName);
        }
    }

    public InputStreamReader(InputStream in, Charset cs) {
        super(in);
        this.in = in;
        this.cs = cs;
    }

    public InputStreamReader(InputStream in, java.nio.charset.CharsetDecoder dec) {
        this(in, dec.charset());
    }

    public String getEncoding() {
        return cs.name();
    }

    private boolean ensure(int n) throws IOException {
        if (byteLen - bytePos >= n) {
            return true;
        }
        if (eof) {
            return false;
        }
        if (bytePos > 0) {
            System.arraycopy(bytes, bytePos, bytes, 0, byteLen - bytePos);
            byteLen -= bytePos;
            bytePos = 0;
        }
        while (byteLen - bytePos < n && !eof) {
            int r = in.read(bytes, byteLen, bytes.length - byteLen);
            if (r < 0) {
                eof = true;
            } else {
                byteLen += r;
            }
            if (r == 0) {
                break;
            }
        }
        return byteLen - bytePos >= n;
    }

    /** Decodes one code point, returns -1 at EOF. */
    private int decodeOne() throws IOException {
        if (!ensure(1)) {
            return -1;
        }
        String name = cs.name();
        int b0 = bytes[bytePos] & 0xff;
        if (name.equals("UTF-8")) {
            int need = b0 < 0x80 ? 1 : b0 >= 0xf0 ? 4 : b0 >= 0xe0 ? 3 : b0 >= 0xc0 ? 2 : 1;
            if (!ensure(need)) {
                bytePos = byteLen;
                return 0xfffd;
            }
            int c;
            if (need == 1) {
                c = b0 < 0x80 ? b0 : 0xfffd;
            } else if (need == 2) {
                c = ((b0 & 0x1f) << 6) | (bytes[bytePos + 1] & 0x3f);
            } else if (need == 3) {
                c = ((b0 & 0x0f) << 12) | ((bytes[bytePos + 1] & 0x3f) << 6) | (bytes[bytePos + 2] & 0x3f);
            } else {
                c = ((b0 & 0x07) << 18) | ((bytes[bytePos + 1] & 0x3f) << 12) | ((bytes[bytePos + 2] & 0x3f) << 6)
                        | (bytes[bytePos + 3] & 0x3f);
            }
            bytePos += need;
            return c;
        }
        if (name.startsWith("UTF-16")) {
            if (!ensure(2)) {
                bytePos = byteLen;
                return 0xfffd;
            }
            int b1 = bytes[bytePos + 1] & 0xff;
            bytePos += 2;
            return name.equals("UTF-16LE") ? (b1 << 8) | b0 : (b0 << 8) | b1;
        }
        bytePos++;
        if (name.equals("US-ASCII")) {
            return b0 < 0x80 ? b0 : 0xfffd;
        }
        return b0;
    }

    public int read(char[] cbuf, int off, int len) throws IOException {
        synchronized (lock) {
            if (off < 0 || len < 0 || len > cbuf.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }
            int n = 0;
            if (pendingLow >= 0) {
                cbuf[off + n++] = (char) pendingLow;
                pendingLow = -1;
            }
            while (n < len) {
                if (n > 0 && byteLen - bytePos == 0 && (eof || in.available() <= 0)) {
                    break;
                }
                int c = decodeOne();
                if (c < 0) {
                    break;
                }
                if (c >= 0x10000) {
                    cbuf[off + n++] = Character.highSurrogate(c);
                    if (n < len) {
                        cbuf[off + n++] = Character.lowSurrogate(c);
                    } else {
                        pendingLow = Character.lowSurrogate(c);
                    }
                } else {
                    cbuf[off + n++] = (char) c;
                }
            }
            return n == 0 ? -1 : n;
        }
    }

    public boolean ready() throws IOException {
        return byteLen - bytePos > 0 || in.available() > 0;
    }

    public void close() throws IOException {
        in.close();
    }
}
