package java.util.zip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/** Writes zip archives. Zip64 output (entries over 4 GB or more than 65535 entries) is not supported. */
public class ZipOutputStream extends DeflaterOutputStream implements ZipConstants {
    public static final int STORED = ZipEntry.STORED;
    public static final int DEFLATED = ZipEntry.DEFLATED;

    private static final class XEntry {
        final ZipEntry entry;
        final long offset;

        XEntry(ZipEntry entry, long offset) {
            this.entry = entry;
            this.offset = offset;
        }
    }

    private XEntry current;
    private final ArrayList<XEntry> xentries = new ArrayList<>();
    private final HashSet<String> names = new HashSet<>();
    private final CRC32 crc = new CRC32();
    private long written;
    private long locoff;
    private byte[] comment;
    private int method = DEFLATED;
    private boolean finished;
    private boolean closed;
    private final Charset charset;

    public ZipOutputStream(OutputStream out) {
        this(out, StandardCharsets.UTF_8);
    }

    public ZipOutputStream(OutputStream out, Charset charset) {
        super(out, out != null ? new Deflater(Deflater.DEFAULT_COMPRESSION, true) : null);
        if (charset == null) {
            throw new NullPointerException("charset is null");
        }
        this.charset = charset;
        usesDefaultDeflater = true;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    public void setComment(String comment) {
        if (comment != null) {
            this.comment = comment.getBytes(charset);
            if (this.comment.length > 0xffff) {
                throw new IllegalArgumentException("ZIP file comment too long.");
            }
        } else {
            this.comment = null;
        }
    }

    public void setMethod(int method) {
        if (method != DEFLATED && method != STORED) {
            throw new IllegalArgumentException("invalid compression method");
        }
        this.method = method;
    }

    public void setLevel(int level) {
        def.setLevel(level);
    }

    public void putNextEntry(ZipEntry e) throws IOException {
        ensureOpen();
        if (current != null) {
            closeEntry();
        }
        if (e.xdostime == -1) {
            e.setTime(System.currentTimeMillis());
        }
        if (e.method == -1) {
            e.method = method;
        }
        e.flag = 0;
        switch (e.method) {
            case DEFLATED:
                if (e.size == -1 || e.csize == -1 || e.crc == -1) {
                    e.flag = 8;
                }
                break;
            case STORED:
                if (e.size == -1) {
                    e.size = e.csize;
                } else if (e.csize == -1) {
                    e.csize = e.size;
                } else if (e.size != e.csize) {
                    throw new ZipException("STORED entry where compressed != uncompressed size");
                }
                if (e.size == -1 || e.crc == -1) {
                    throw new ZipException("STORED entry missing size, compressed size, or crc-32");
                }
                break;
            default:
                throw new ZipException("unsupported compression method");
        }
        if (!names.add(e.name)) {
            throw new ZipException("duplicate entry: " + e.name);
        }
        if (charset == StandardCharsets.UTF_8 || "UTF-8".equals(charset.name())) {
            e.flag |= ZipUtils.EFS;
        }
        current = new XEntry(e, written);
        xentries.add(current);
        writeLOC(current);
    }

    public void closeEntry() throws IOException {
        ensureOpen();
        if (current != null) {
            ZipEntry e = current.entry;
            switch (e.method) {
                case DEFLATED:
                    def.finish();
                    while (!def.finished()) {
                        deflate();
                    }
                    if ((e.flag & 8) == 0) {
                        if (e.size != def.getBytesRead()) {
                            throw new ZipException("invalid entry size (expected " + e.size + " but got "
                                    + def.getBytesRead() + " bytes)");
                        }
                        if (e.csize != def.getBytesWritten()) {
                            throw new ZipException("invalid entry compressed size (expected " + e.csize
                                    + " but got " + def.getBytesWritten() + " bytes)");
                        }
                        if (e.crc != crc.getValue()) {
                            throw new ZipException("invalid entry CRC-32 (expected 0x" + Long.toHexString(e.crc)
                                    + " but got 0x" + Long.toHexString(crc.getValue()) + ")");
                        }
                    } else {
                        e.size = def.getBytesRead();
                        e.csize = def.getBytesWritten();
                        e.crc = crc.getValue();
                        writeEXT(e);
                    }
                    def.reset();
                    written += e.csize;
                    break;
                case STORED:
                    if (e.size != written - locoff) {
                        throw new ZipException("invalid entry size (expected " + e.size + " but got "
                                + (written - locoff) + " bytes)");
                    }
                    if (e.crc != crc.getValue()) {
                        throw new ZipException("invalid entry crc-32 (expected 0x" + Long.toHexString(e.crc)
                                + " but got 0x" + Long.toHexString(crc.getValue()) + ")");
                    }
                    break;
                default:
                    throw new ZipException("invalid compression method");
            }
            crc.reset();
            current = null;
        }
    }

    public synchronized void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        if (current == null) {
            throw new ZipException("no current ZIP entry");
        }
        ZipEntry entry = current.entry;
        switch (entry.method) {
            case DEFLATED:
                super.write(b, off, len);
                break;
            case STORED:
                written += len;
                if (written - locoff > entry.size) {
                    throw new ZipException("attempt to write past end of STORED entry");
                }
                out.write(b, off, len);
                break;
            default:
                throw new ZipException("invalid compression method");
        }
        crc.update(b, off, len);
    }

    public void finish() throws IOException {
        ensureOpen();
        if (finished) {
            return;
        }
        if (current != null) {
            closeEntry();
        }
        long off = written;
        for (XEntry xentry : xentries) {
            writeCEN(xentry);
        }
        writeEND(off, written - off);
        finished = true;
    }

    public void close() throws IOException {
        if (!closed) {
            super.close();
            closed = true;
        }
    }

    private static int version(ZipEntry e) throws ZipException {
        switch (e.method) {
            case DEFLATED:
                return 20;
            case STORED:
                return 10;
            default:
                throw new ZipException("unsupported compression method");
        }
    }

    private void writeLOC(XEntry xentry) throws IOException {
        ZipEntry e = xentry.entry;
        int flag = e.flag;
        byte[] extra = e.extra;
        writeInt(LOCSIG);
        writeShort(version(e));
        writeShort(flag);
        writeShort(e.method);
        writeInt(e.xdostime);
        if ((flag & 8) == 8) {
            writeInt(0);
            writeInt(0);
            writeInt(0);
        } else {
            writeInt(e.crc);
            writeInt(e.csize);
            writeInt(e.size);
        }
        byte[] nameBytes = e.name.getBytes(charset);
        writeShort(nameBytes.length);
        writeShort(extra != null ? extra.length : 0);
        writeBytes(nameBytes, 0, nameBytes.length);
        if (extra != null) {
            writeBytes(extra, 0, extra.length);
        }
        locoff = written;
    }

    private void writeEXT(ZipEntry e) throws IOException {
        writeInt(EXTSIG);
        writeInt(e.crc);
        writeInt(e.csize);
        writeInt(e.size);
    }

    private void writeCEN(XEntry xentry) throws IOException {
        ZipEntry e = xentry.entry;
        int version = version(e);
        byte[] extra = e.extra;
        writeInt(CENSIG);
        writeShort(version);
        writeShort(version);
        writeShort(e.flag);
        writeShort(e.method);
        writeInt(e.xdostime);
        writeInt(e.crc);
        writeInt(e.csize);
        writeInt(e.size);
        byte[] nameBytes = e.name.getBytes(charset);
        writeShort(nameBytes.length);
        writeShort(extra != null ? extra.length : 0);
        byte[] commentBytes = null;
        if (e.comment != null) {
            commentBytes = e.comment.getBytes(charset);
            writeShort(Math.min(commentBytes.length, 0xffff));
        } else {
            writeShort(0);
        }
        writeShort(0);
        writeShort(0);
        writeInt(0);
        writeInt(xentry.offset);
        writeBytes(nameBytes, 0, nameBytes.length);
        if (extra != null) {
            writeBytes(extra, 0, extra.length);
        }
        if (commentBytes != null) {
            writeBytes(commentBytes, 0, Math.min(commentBytes.length, 0xffff));
        }
    }

    private void writeEND(long off, long len) throws IOException {
        int count = xentries.size();
        if (count > 0xffff || off >= ZipUtils.ZIP64_MAGICVAL || len >= ZipUtils.ZIP64_MAGICVAL) {
            throw new ZipException("archive needs zip64, which is not supported");
        }
        writeInt(ENDSIG);
        writeShort(0);
        writeShort(0);
        writeShort(count);
        writeShort(count);
        writeInt(len);
        writeInt(off);
        if (comment != null) {
            writeShort(comment.length);
            writeBytes(comment, 0, comment.length);
        } else {
            writeShort(0);
        }
    }

    private void writeShort(int v) throws IOException {
        OutputStream out = this.out;
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
        written += 2;
    }

    private void writeInt(long v) throws IOException {
        OutputStream out = this.out;
        out.write((int) (v & 0xff));
        out.write((int) ((v >>> 8) & 0xff));
        out.write((int) ((v >>> 16) & 0xff));
        out.write((int) ((v >>> 24) & 0xff));
        written += 4;
    }

    private void writeBytes(byte[] b, int off, int len) throws IOException {
        this.out.write(b, off, len);
        written += len;
    }
}
