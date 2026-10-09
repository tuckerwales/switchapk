package javax.crypto;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CipherOutputStream extends FilterOutputStream {
    private final Cipher cipher;
    private final byte[] ibuffer = new byte[1];
    private boolean closed;

    public CipherOutputStream(OutputStream os, Cipher c) {
        super(os);
        cipher = c;
    }

    protected CipherOutputStream(OutputStream os) {
        super(os);
        cipher = new NullCipher();
    }

    public void write(int b) throws IOException {
        ibuffer[0] = (byte) b;
        write(ibuffer, 0, 1);
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        byte[] out = cipher.update(b, off, len);
        if (out != null) this.out.write(out);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        if (closed) return;
        closed = true;
        byte[] last;
        try {
            last = cipher.doFinal();
        } catch (IllegalBlockSizeException e) {
            throw new IOException(e);
        } catch (BadPaddingException e) {
            throw new IOException(e);
        }
        if (last != null) out.write(last);
        try {
            flush();
        } finally {
            out.close();
        }
    }
}
