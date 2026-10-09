package java.util.zip;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.zip.ZipUtils.get16;
import static java.util.zip.ZipUtils.get32;
import static java.util.zip.ZipUtils.get64;

/**
 * Random access to a zip archive, in Java over RandomAccessFile. The central directory is read once when the
 * file is opened; entry data is read on demand (deflated entries through an Inflater each stream owns).
 */
public class ZipFile implements ZipConstants, Closeable {
    public static final int OPEN_READ = 0x1;
    public static final int OPEN_DELETE = 0x4;

    private static final long ZIP64_LOCSIG = 0x07064b50L;
    private static final long ZIP64_ENDSIG = 0x06064b50L;

    private static final class Source {
        final ZipEntry entry;
        final long locOffset;

        Source(ZipEntry entry, long locOffset) {
            this.entry = entry;
            this.locOffset = locOffset;
        }
    }

    private final String name;
    private final RandomAccessFile raf;
    private final ArrayList<Source> sources = new ArrayList<>();
    private final HashMap<String, Source> byName = new HashMap<>();
    private final Set<InputStream> streams = Collections.synchronizedSet(new HashSet<InputStream>());
    private String comment;
    private volatile boolean closeRequested;

    public ZipFile(String name) throws IOException {
        this(new File(name), OPEN_READ);
    }

    public ZipFile(File file, int mode) throws IOException {
        this(file, mode, StandardCharsets.UTF_8);
    }

    public ZipFile(File file) throws ZipException, IOException {
        this(file, OPEN_READ);
    }

    public ZipFile(File file, int mode, Charset charset) throws IOException {
        if (((mode & OPEN_READ) == 0) || ((mode & ~(OPEN_READ | OPEN_DELETE)) != 0)) {
            throw new IllegalArgumentException("Illegal mode: 0x" + Integer.toHexString(mode));
        }
        if (charset == null) {
            throw new NullPointerException("charset is null");
        }
        this.name = file.getPath();
        this.raf = new RandomAccessFile(file, "r");
        boolean ok = false;
        try {
            readCentralDirectory(charset);
            ok = true;
        } finally {
            if (!ok) {
                raf.close();
            }
        }
        if ((mode & OPEN_DELETE) != 0) {
            file.delete();
        }
    }

    public ZipFile(String name, Charset charset) throws IOException {
        this(new File(name), OPEN_READ, charset);
    }

    public ZipFile(File file, Charset charset) throws IOException {
        this(file, OPEN_READ, charset);
    }

    public String getComment() {
        ensureOpen();
        return comment;
    }

    public ZipEntry getEntry(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        ensureOpen();
        Source s = find(name);
        return s != null ? new ZipEntry(s.entry) : null;
    }

    private Source find(String name) {
        Source s = byName.get(name);
        if (s == null && !name.endsWith("/")) {
            s = byName.get(name + "/");
        }
        return s;
    }

    public InputStream getInputStream(ZipEntry entry) throws IOException {
        if (entry == null) {
            throw new NullPointerException("entry");
        }
        ensureOpen();
        Source s = byName.get(entry.name);
        if (s == null) {
            return null;
        }
        ZipEntry e = s.entry;
        long dataOffset;
        synchronized (raf) {
            byte[] loc = new byte[LOCHDR];
            raf.seek(s.locOffset);
            raf.readFully(loc, 0, LOCHDR);
            if (get32(loc, 0) != LOCSIG) {
                throw new ZipException("invalid LOC header (bad signature)");
            }
            dataOffset = s.locOffset + LOCHDR + get16(loc, LOCNAM) + get16(loc, LOCEXT);
        }
        InputStream in;
        switch (e.method) {
            case ZipEntry.STORED:
                in = new EntryInputStream(dataOffset, e.size);
                break;
            case ZipEntry.DEFLATED:
                long size = e.csize + 2;
                int bufSize = (int) Math.max(Math.min(size, 65536), 4096);
                in = new EntryInflaterInputStream(new EntryInputStream(dataOffset, e.csize), bufSize, e.size);
                break;
            default:
                throw new ZipException("invalid compression method");
        }
        streams.add(in);
        return in;
    }

    public String getName() {
        return name;
    }

    public Enumeration<? extends ZipEntry> entries() {
        ensureOpen();
        final Iterator<Source> it = sources.iterator();
        return new Enumeration<ZipEntry>() {
            public boolean hasMoreElements() {
                ensureOpen();
                return it.hasNext();
            }

            public ZipEntry nextElement() {
                ensureOpen();
                return new ZipEntry(it.next().entry);
            }
        };
    }

    public Stream<? extends ZipEntry> stream() {
        ensureOpen();
        ArrayList<ZipEntry> list = new ArrayList<>(sources.size());
        for (Source s : sources) {
            list.add(new ZipEntry(s.entry));
        }
        return list.stream();
    }

    public int size() {
        ensureOpen();
        return sources.size();
    }

    public void close() throws IOException {
        if (closeRequested) {
            return;
        }
        closeRequested = true;
        InputStream[] open;
        synchronized (streams) {
            open = streams.toArray(new InputStream[0]);
            streams.clear();
        }
        for (InputStream in : open) {
            in.close();
        }
        synchronized (raf) {
            raf.close();
        }
    }

    private void ensureOpen() {
        if (closeRequested) {
            throw new IllegalStateException("zip file closed");
        }
    }

    private void readCentralDirectory(Charset charset) throws IOException {
        long fileLen = raf.length();
        if (fileLen == 0) {
            throw new ZipException("zip file is empty");
        }
        if (fileLen < ENDHDR) {
            throw new ZipException("zip END header not found");
        }
        int tailLen = (int) Math.min(fileLen, ENDHDR + 0xffff);
        byte[] tail = new byte[tailLen];
        raf.seek(fileLen - tailLen);
        raf.readFully(tail, 0, tailLen);
        int end = -1;
        for (int i = tailLen - ENDHDR; i >= 0; i--) {
            if (get32(tail, i) == ENDSIG && i + ENDHDR + get16(tail, i + ENDCOM) <= tailLen) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            throw new ZipException("zip END header not found");
        }
        long endPos = fileLen - tailLen + end;
        long total = get16(tail, end + ENDTOT);
        long cenLen = get32(tail, end + ENDSIZ);
        long cenOff = get32(tail, end + ENDOFF);
        int commentLen = get16(tail, end + ENDCOM);
        if (commentLen > 0) {
            comment = new String(tail, end + ENDHDR, commentLen, charset);
        }
        if ((total == 0xffff || cenLen == ZipUtils.ZIP64_MAGICVAL || cenOff == ZipUtils.ZIP64_MAGICVAL)
                && endPos >= 20) {
            byte[] loc = new byte[20];
            raf.seek(endPos - 20);
            raf.readFully(loc, 0, 20);
            if (get32(loc, 0) == ZIP64_LOCSIG) {
                byte[] end64 = new byte[56];
                raf.seek(get64(loc, 8));
                raf.readFully(end64, 0, 56);
                if (get32(end64, 0) == ZIP64_ENDSIG) {
                    total = get64(end64, 32);
                    cenLen = get64(end64, 40);
                    cenOff = get64(end64, 48);
                }
            }
        }
        if (cenOff + cenLen > endPos || cenLen > Integer.MAX_VALUE) {
            throw new ZipException("invalid END header (bad central directory offset)");
        }
        byte[] cen = new byte[(int) cenLen];
        raf.seek(cenOff);
        raf.readFully(cen, 0, cen.length);
        int pos = 0;
        while (pos + CENHDR <= cen.length) {
            if (get32(cen, pos) != CENSIG) {
                throw new ZipException("invalid CEN header (bad signature)");
            }
            int flag = get16(cen, pos + CENFLG);
            int nlen = get16(cen, pos + CENNAM);
            int elen = get16(cen, pos + CENEXT);
            int clen = get16(cen, pos + CENCOM);
            if (pos + CENHDR + nlen + elen + clen > cen.length) {
                throw new ZipException("invalid CEN header (bad header size)");
            }
            Charset cs = (flag & ZipUtils.EFS) != 0 ? StandardCharsets.UTF_8 : charset;
            ZipEntry e = new ZipEntry(new String(cen, pos + CENHDR, nlen, cs));
            e.flag = flag;
            e.method = get16(cen, pos + CENHOW);
            e.xdostime = get32(cen, pos + CENTIM);
            e.crc = get32(cen, pos + CENCRC);
            e.csize = get32(cen, pos + CENSIZ);
            e.size = get32(cen, pos + CENLEN);
            long locOff = get32(cen, pos + CENOFF);
            if (elen > 0) {
                byte[] extra = new byte[elen];
                System.arraycopy(cen, pos + CENHDR + nlen, extra, 0, elen);
                locOff = readZip64(e, extra, locOff);
                e.setExtra0(extra, false);
            }
            if (clen > 0) {
                e.comment = new String(cen, pos + CENHDR + nlen + elen, clen, cs);
            }
            Source s = new Source(e, locOff);
            sources.add(s);
            if (!byName.containsKey(e.name)) {
                byName.put(e.name, s);
            }
            pos += CENHDR + nlen + elen + clen;
        }
        if (sources.size() != total && total != 0xffff) {
            throw new ZipException("invalid CEN header (bad entry count)");
        }
    }

    /* Applies the zip64 extended information field of a central entry; returns the local header offset. */
    private static long readZip64(ZipEntry e, byte[] extra, long locOff) {
        int off = 0;
        while (off + 4 <= extra.length) {
            int tag = get16(extra, off);
            int sz = get16(extra, off + 2);
            off += 4;
            if (off + sz > extra.length) {
                break;
            }
            if (tag == ZipUtils.ZIP64_EXTID) {
                int p = off;
                int end = off + sz;
                if (e.size == ZipUtils.ZIP64_MAGICVAL && p + 8 <= end) {
                    e.size = get64(extra, p);
                    p += 8;
                }
                if (e.csize == ZipUtils.ZIP64_MAGICVAL && p + 8 <= end) {
                    e.csize = get64(extra, p);
                    p += 8;
                }
                if (locOff == ZipUtils.ZIP64_MAGICVAL && p + 8 <= end) {
                    locOff = get64(extra, p);
                }
            }
            off += sz;
        }
        return locOff;
    }

    /* Reads a byte range of the archive; concurrent streams share the file under its lock. */
    private final class EntryInputStream extends InputStream {
        private long pos;
        private long rem;
        private boolean closed;

        EntryInputStream(long start, long len) {
            this.pos = start;
            this.rem = len;
        }

        public int read() throws IOException {
            byte[] b = new byte[1];
            return read(b, 0, 1) == 1 ? b[0] & 0xff : -1;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            if (off < 0 || len < 0 || off > b.length - len) {
                throw new IndexOutOfBoundsException();
            }
            if (rem <= 0) {
                return -1;
            }
            if (len == 0) {
                return 0;
            }
            if (len > rem) {
                len = (int) rem;
            }
            int n;
            synchronized (raf) {
                ensureOpenOrZipException();
                raf.seek(pos);
                n = raf.read(b, off, len);
            }
            if (n > 0) {
                pos += n;
                rem -= n;
            }
            if (rem == 0) {
                streams.remove(this);
            }
            return n;
        }

        public long skip(long n) throws IOException {
            if (n <= 0) {
                return 0;
            }
            if (n > rem) {
                n = rem;
            }
            pos += n;
            rem -= n;
            return n;
        }

        public int available() {
            return rem > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rem;
        }

        public void close() {
            closed = true;
            rem = 0;
            streams.remove(this);
        }
    }

    private void ensureOpenOrZipException() throws IOException {
        if (closeRequested) {
            throw new ZipException("ZipFile closed");
        }
    }

    private final class EntryInflaterInputStream extends InflaterInputStream {
        private final long size;
        private boolean eof;

        EntryInflaterInputStream(InputStream in, int bufSize, long size) {
            super(in, new Inflater(true), bufSize);
            usesDefaultInflater = true;
            this.size = size;
        }

        public void close() throws IOException {
            if (!closed) {
                streams.remove(this);
                super.close();
            }
        }

        protected void fill() throws IOException {
            if (eof) {
                throw new EOFException("Unexpected end of ZLIB input stream");
            }
            len = in.read(buf, 0, buf.length);
            if (len == -1) {
                buf[0] = 0;
                len = 1;
                eof = true;
            }
            inf.setInput(buf, 0, len);
        }

        public int available() throws IOException {
            if (closed) {
                return 0;
            }
            long avail = size - inf.getBytesWritten();
            return avail > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(avail, 0);
        }
    }
}
