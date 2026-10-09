package java.util.zip;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

public class GZIPInputStream extends InflaterInputStream {
    protected CRC32 crc = new CRC32();
    protected boolean eos;

    public static final int GZIP_MAGIC = 0x8b1f;

    private static final int FHCRC = 2;
    private static final int FEXTRA = 4;
    private static final int FNAME = 8;
    private static final int FCOMMENT = 16;

    private byte[] tmpbuf = new byte[128];

    public GZIPInputStream(InputStream in, int size) throws IOException {
        super(in, in != null ? new Inflater(true) : null, size);
        usesDefaultInflater = true;
        try {
            readHeader(in);
        } catch (IOException e) {
            inf.end();
            throw e;
        }
    }

    public GZIPInputStream(InputStream in) throws IOException {
        this(in, 512);
    }

    public int read(byte[] buf, int off, int len) throws IOException {
        ensureOpen();
        if (eos) {
            return -1;
        }
        int n = super.read(buf, off, len);
        if (n == -1) {
            if (readTrailer()) {
                eos = true;
            } else {
                return this.read(buf, off, len);
            }
        } else {
            crc.update(buf, off, n);
        }
        return n;
    }

    public void close() throws IOException {
        if (!closed) {
            super.close();
            eos = true;
            closed = true;
        }
    }

    private int readHeader(InputStream thisIn) throws IOException {
        CheckedInputStream in = new CheckedInputStream(thisIn, crc);
        crc.reset();
        if (readUShort(in) != GZIP_MAGIC) {
            throw new ZipException("Not in GZIP format");
        }
        if (readUByte(in) != 8) {
            throw new ZipException("Unsupported compression method");
        }
        int flg = readUByte(in);
        skipBytes(in, 6);
        int n = 2 + 2 + 6;
        if ((flg & FEXTRA) == FEXTRA) {
            int m = readUShort(in);
            skipBytes(in, m);
            n += m + 2;
        }
        if ((flg & FNAME) == FNAME) {
            do {
                n++;
            } while (readUByte(in) != 0);
        }
        if ((flg & FCOMMENT) == FCOMMENT) {
            do {
                n++;
            } while (readUByte(in) != 0);
        }
        if ((flg & FHCRC) == FHCRC) {
            int v = (int) crc.getValue() & 0xffff;
            if (readUShort(in) != v) {
                throw new ZipException("Corrupt GZIP header");
            }
            n += 2;
        }
        crc.reset();
        return n;
    }

    /* Reads the trailer; returns true at the end of the input, false when another gzip member follows. */
    private boolean readTrailer() throws IOException {
        InputStream in = this.in;
        int n = inf.getRemaining();
        if (n > 0) {
            in = new SequenceInputStream(new ByteArrayInputStream(buf, len - n, n), new FilterInputStream(in) {
                public void close() throws IOException {
                }
            });
        }
        if ((readUInt(in) != crc.getValue()) || (readUInt(in) != (inf.getBytesWritten() & 0xffffffffL))) {
            throw new ZipException("Corrupt GZIP trailer");
        }
        if (this.in.available() > 0 || n > 26) {
            int m = 8;
            try {
                m += readHeader(in);
            } catch (IOException ze) {
                return true;
            }
            inf.reset();
            if (n > m) {
                inf.setInput(buf, len - n + m, n - m);
            }
            return false;
        }
        return true;
    }

    private long readUInt(InputStream in) throws IOException {
        long s = readUShort(in);
        return ((long) readUShort(in) << 16) | s;
    }

    private int readUShort(InputStream in) throws IOException {
        int b = readUByte(in);
        return (readUByte(in) << 8) | b;
    }

    private int readUByte(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new EOFException();
        }
        if (b < -1 || b > 255) {
            throw new IOException(this.in.getClass().getName() + ".read() returned value out of range -1..255: " + b);
        }
        return b;
    }

    private void skipBytes(InputStream in, int n) throws IOException {
        while (n > 0) {
            int len = in.read(tmpbuf, 0, n < tmpbuf.length ? n : tmpbuf.length);
            if (len == -1) {
                throw new EOFException();
            }
            n -= len;
        }
    }
}
