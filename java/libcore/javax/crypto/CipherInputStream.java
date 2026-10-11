package javax.crypto;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decrypts or encrypts what it reads. As on Android, a failed doFinal at the end
 * of the stream (a bad AEAD tag, bad padding) is thrown as an IOException
 * instead of being swallowed.
 */
public class CipherInputStream extends FilterInputStream {
    private final Cipher cipher;
    private final byte[] ibuffer = new byte[512];
    private boolean done;
    private byte[] obuffer;
    private int ostart;
    private int ofinish;
    private boolean closed;

    public CipherInputStream(InputStream is, Cipher c) {
        super(is);
        cipher = c;
    }

    protected CipherInputStream(InputStream is) {
        super(is);
        cipher = new NullCipher();
    }

    private int getMoreData() throws IOException {
        if (done) return -1;
        int readin = in.read(ibuffer);
        if (readin == -1) {
            done = true;
            try {
                obuffer = cipher.doFinal();
            } catch (IllegalBlockSizeException e) {
                throw new IOException(e);
            } catch (BadPaddingException e) {
                throw new IOException(e);
            }
            ostart = 0;
            ofinish = obuffer == null ? 0 : obuffer.length;
            return ofinish == 0 ? -1 : ofinish;
        }
        obuffer = cipher.update(ibuffer, 0, readin);
        ostart = 0;
        ofinish = obuffer == null ? 0 : obuffer.length;
        return ofinish;
    }

    public int read() throws IOException {
        if (ostart >= ofinish) {
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        return obuffer[ostart++] & 0xff;
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (ostart >= ofinish) {
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        if (len <= 0) return 0;
        int available = ofinish - ostart;
        if (len < available) available = len;
        if (b != null) System.arraycopy(obuffer, ostart, b, off, available);
        ostart += available;
        return available;
    }

    public long skip(long n) throws IOException {
        int available = ofinish - ostart;
        if (n > available) n = available;
        if (n < 0) return 0;
        ostart += (int) n;
        return n;
    }

    public int available() throws IOException {
        return ofinish - ostart;
    }

    public void close() throws IOException {
        if (closed) return;
        closed = true;
        in.close();
        if (!done) {
            try {
                cipher.doFinal();
            } catch (Exception ignored) {
            }
        }
        ostart = 0;
        ofinish = 0;
    }

    public boolean markSupported() {
        return false;
    }
}
