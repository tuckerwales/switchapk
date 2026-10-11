package sun.nio.ch;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.NIOAccess;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Set;
import libcore.io.Os;

/**
 * The FileChannel behind FileInputStream, FileOutputStream, RandomAccessFile and FileChannel.open.
 * Locks are advisory within this process only (one app per process, nothing else shares its files).
 */
public class FileChannelImpl extends FileChannel {
    /* Locks held by any channel, so two channels on the same file see each other (by path when known). */
    private static final ArrayList<Lock> LOCKS = new ArrayList<>();

    private final int fd;
    private final Closeable parent;
    private final String path;
    private final boolean readable;
    private final boolean writable;
    private final boolean append;
    private final ArrayList<MappedByteBuffer> writableMaps = new ArrayList<>();

    private FileChannelImpl(int fd, String path, Closeable parent, boolean readable, boolean writable,
            boolean append) {
        this.fd = fd;
        this.path = path;
        this.parent = parent;
        this.readable = readable;
        this.writable = writable;
        this.append = append;
    }

    /* parent is the stream that owns fd; closing the channel closes it. */
    public static FileChannel open(int fd, String path, Closeable parent, boolean readable,
            boolean writable, boolean append) {
        return new FileChannelImpl(fd, path, parent, readable, writable, append);
    }

    public static FileChannel open(String path, Set<? extends OpenOption> options) throws IOException {
        boolean read = options.contains(StandardOpenOption.READ);
        boolean write = options.contains(StandardOpenOption.WRITE);
        boolean append = options.contains(StandardOpenOption.APPEND);
        if (append) {
            write = true;
        }
        if (!write) {
            read = true;
        }
        if (append && (read || options.contains(StandardOpenOption.TRUNCATE_EXISTING))) {
            throw new IllegalArgumentException("APPEND + " + (read ? "READ" : "TRUNCATE_EXISTING") + " not allowed");
        }
        int flags = read && write ? Os.O_RDWR : write ? Os.O_WRONLY : Os.O_RDONLY;
        if (write) {
            if (options.contains(StandardOpenOption.CREATE_NEW)) {
                if (new java.io.File(path).exists()) {
                    throw new java.nio.file.FileAlreadyExistsException(path);
                }
                flags |= Os.O_CREAT;
            } else if (options.contains(StandardOpenOption.CREATE)) {
                flags |= Os.O_CREAT;
            }
            if (options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
                flags |= Os.O_TRUNC;
            }
            if (append) {
                flags |= Os.O_APPEND;
            }
        }
        int raw;
        try {
            raw = Os.open(path, flags, 0666);
        } catch (java.io.FileNotFoundException e) {
            throw new java.nio.file.NoSuchFileException(path);
        }
        return new FileChannelImpl(raw, path, null, read, write, append);
    }

    private void ensureOpen() throws IOException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    private void ensureReadable() throws IOException {
        ensureOpen();
        if (!readable) {
            throw new NonReadableChannelException();
        }
    }

    private void ensureWritable() throws IOException {
        ensureOpen();
        if (!writable) {
            throw new NonWritableChannelException();
        }
    }

    protected void implCloseChannel() throws IOException {
        synchronized (writableMaps) {
            for (MappedByteBuffer m : writableMaps) {
                m.force();
            }
            writableMaps.clear();
        }
        synchronized (LOCKS) {
            for (int i = LOCKS.size() - 1; i >= 0; i--) {
                if (LOCKS.get(i).channel() == this) {
                    LOCKS.remove(i).valid = false;
                }
            }
        }
        if (parent != null) {
            parent.close();
        } else {
            Os.close(fd);
        }
    }

    private int readInto(ByteBuffer dst) throws IOException {
        int n = dst.remaining();
        if (n == 0) {
            return 0;
        }
        if (dst.hasArray()) {
            int r = Os.read(fd, dst.array(), dst.arrayOffset() + dst.position(), n);
            if (r > 0) {
                dst.position(dst.position() + r);
            }
            return r;
        }
        byte[] tmp = new byte[n];
        int r = Os.read(fd, tmp, 0, n);
        if (r > 0) {
            dst.put(tmp, 0, r);
        }
        return r;
    }

    private int writeFrom(ByteBuffer src) throws IOException {
        int n = src.remaining();
        if (src.hasArray()) {
            Os.write(fd, src.array(), src.arrayOffset() + src.position(), n);
            src.position(src.position() + n);
            return n;
        }
        byte[] tmp = new byte[n];
        src.get(tmp);
        Os.write(fd, tmp, 0, n);
        return n;
    }

    public synchronized int read(ByteBuffer dst) throws IOException {
        ensureReadable();
        return readInto(dst);
    }

    public synchronized long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > dsts.length - length) {
            throw new IndexOutOfBoundsException();
        }
        ensureReadable();
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            if (!dsts[i].hasRemaining()) {
                continue;
            }
            int want = dsts[i].remaining();
            int n = readInto(dsts[i]);
            if (n < 0) {
                return total == 0 ? -1 : total;
            }
            total += n;
            if (n < want) {
                break;
            }
        }
        return total;
    }

    public synchronized int write(ByteBuffer src) throws IOException {
        ensureWritable();
        return writeFrom(src);
    }

    public synchronized long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > srcs.length - length) {
            throw new IndexOutOfBoundsException();
        }
        ensureWritable();
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += writeFrom(srcs[i]);
        }
        return total;
    }

    public synchronized long position() throws IOException {
        ensureOpen();
        if (append) {
            return Os.seek(fd, 0, Os.SEEK_END);
        }
        return Os.seek(fd, 0, Os.SEEK_CUR);
    }

    public synchronized FileChannel position(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IllegalArgumentException();
        }
        ensureOpen();
        Os.seek(fd, newPosition, Os.SEEK_SET);
        return this;
    }

    public synchronized long size() throws IOException {
        ensureOpen();
        long cur = Os.seek(fd, 0, Os.SEEK_CUR);
        long end = Os.seek(fd, 0, Os.SEEK_END);
        Os.seek(fd, cur, Os.SEEK_SET);
        return end;
    }

    public synchronized FileChannel truncate(long size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Negative size");
        }
        ensureWritable();
        long cur = Os.seek(fd, 0, Os.SEEK_CUR);
        if (size < size()) {
            Os.ftruncate(fd, size);
        }
        Os.seek(fd, Math.min(cur, size), Os.SEEK_SET);
        return this;
    }

    public void force(boolean metaData) throws IOException {
        ensureOpen();
        Os.fsync(fd);
    }

    public synchronized long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        ensureReadable();
        if (position < 0 || count < 0) {
            throw new IllegalArgumentException();
        }
        long saved = Os.seek(fd, 0, Os.SEEK_CUR);
        Os.seek(fd, position, Os.SEEK_SET);
        ByteBuffer buf = ByteBuffer.allocate((int) Math.max(1, Math.min(count, 65536)));
        long total = 0;
        try {
            while (total < count) {
                buf.clear();
                buf.limit((int) Math.min(buf.capacity(), count - total));
                int n = readInto(buf);
                if (n <= 0) {
                    break;
                }
                buf.flip();
                while (buf.hasRemaining()) {
                    target.write(buf);
                }
                total += n;
            }
        } finally {
            Os.seek(fd, saved, Os.SEEK_SET);
        }
        return total;
    }

    public synchronized long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        ensureWritable();
        if (position < 0 || count < 0) {
            throw new IllegalArgumentException();
        }
        if (position > size()) {
            return 0;
        }
        long saved = Os.seek(fd, 0, Os.SEEK_CUR);
        Os.seek(fd, position, Os.SEEK_SET);
        ByteBuffer buf = ByteBuffer.allocate((int) Math.max(1, Math.min(count, 65536)));
        long total = 0;
        try {
            while (total < count) {
                buf.clear();
                buf.limit((int) Math.min(buf.capacity(), count - total));
                int n = src.read(buf);
                if (n <= 0) {
                    break;
                }
                buf.flip();
                writeFrom(buf);
                total += n;
            }
        } finally {
            Os.seek(fd, saved, Os.SEEK_SET);
        }
        return total;
    }

    public synchronized int read(ByteBuffer dst, long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        ensureReadable();
        long saved = Os.seek(fd, 0, Os.SEEK_CUR);
        try {
            Os.seek(fd, position, Os.SEEK_SET);
            return readInto(dst);
        } finally {
            Os.seek(fd, saved, Os.SEEK_SET);
        }
    }

    public synchronized int write(ByteBuffer src, long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        ensureWritable();
        long saved = Os.seek(fd, 0, Os.SEEK_CUR);
        try {
            Os.seek(fd, position, Os.SEEK_SET);
            return writeFrom(src);
        } finally {
            Os.seek(fd, saved, Os.SEEK_SET);
        }
    }

    public synchronized MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        ensureOpen();
        if (mode == null) {
            throw new NullPointerException("Mode is null");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Negative size");
        }
        if (position + size < 0) {
            throw new IllegalArgumentException("Position + size overflow");
        }
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Size exceeds Integer.MAX_VALUE");
        }
        if (!readable) {
            throw new NonReadableChannelException();
        }
        if (mode != MapMode.READ_ONLY && !writable) {
            throw new NonWritableChannelException();
        }
        if (mode == MapMode.READ_WRITE && size() < position + size) {
            Os.ftruncate(fd, position + size);
        }
        byte[] data = new byte[(int) size];
        long saved = Os.seek(fd, 0, Os.SEEK_CUR);
        try {
            Os.seek(fd, position, Os.SEEK_SET);
            int off = 0;
            while (off < data.length) {
                int n = Os.read(fd, data, off, data.length - off);
                if (n <= 0) {
                    break;
                }
                off += n;
            }
        } finally {
            Os.seek(fd, saved, Os.SEEK_SET);
        }
        if (mode != MapMode.READ_WRITE) {
            return NIOAccess.newMappedByteBuffer(data, position, mode == MapMode.READ_ONLY, null);
        }
        MappedByteBuffer m = NIOAccess.newMappedByteBuffer(data, position, false, new NIOAccess.WriteBack() {
            public void write(byte[] b, int offset, int length, long filePosition) {
                writeBack(b, offset, length, filePosition);
            }
        });
        synchronized (writableMaps) {
            writableMaps.add(m);
        }
        return m;
    }

    /* force() after the channel closed has nothing to write to; the close already wrote the region. */
    private synchronized void writeBack(byte[] b, int offset, int length, long filePosition) {
        if (!isOpen()) {
            return;
        }
        try {
            long saved = Os.seek(fd, 0, Os.SEEK_CUR);
            Os.seek(fd, filePosition, Os.SEEK_SET);
            Os.write(fd, b, offset, length);
            Os.seek(fd, saved, Os.SEEK_SET);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public FileLock lock(long position, long size, boolean shared) throws IOException {
        FileLock l = tryLock(position, size, shared);
        if (l == null) {
            throw new OverlappingFileLockException();
        }
        return l;
    }

    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        ensureOpen();
        if (shared && !readable) {
            throw new NonReadableChannelException();
        }
        if (!shared && !writable) {
            throw new NonWritableChannelException();
        }
        Lock lock = new Lock(this, position, size, shared);
        synchronized (LOCKS) {
            for (Lock other : LOCKS) {
                if (other.sameFile(this) && other.overlaps(position, size)) {
                    throw new OverlappingFileLockException();
                }
            }
            LOCKS.add(lock);
        }
        return lock;
    }

    private static final class Lock extends FileLock {
        volatile boolean valid = true;

        Lock(FileChannelImpl channel, long position, long size, boolean shared) {
            super(channel, position, size, shared);
        }

        boolean sameFile(FileChannelImpl c) {
            FileChannelImpl mine = (FileChannelImpl) channel();
            if (mine == c) {
                return true;
            }
            return mine.path != null && c.path != null
                    && new java.io.File(mine.path).getAbsolutePath().equals(new java.io.File(c.path).getAbsolutePath());
        }

        public boolean isValid() {
            return valid && channel().isOpen();
        }

        public void release() throws IOException {
            if (!channel().isOpen()) {
                throw new ClosedChannelException();
            }
            synchronized (LOCKS) {
                LOCKS.remove(this);
            }
            valid = false;
        }
    }
}
