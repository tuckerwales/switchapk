package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import libcore.io.Os;

public class FileChannel implements Channel {
    private final int fd;
    private final boolean readable, writable;
    private boolean open = true;

    FileChannel(int fd, boolean readable, boolean writable) {
        this.fd = fd;
        this.readable = readable;
        this.writable = writable;
    }

    public static FileChannel forFd(int fd, boolean readable, boolean writable) {
        return new FileChannel(fd, readable, writable);
    }

    public boolean isOpen() {
        return open;
    }

    public void close() throws IOException {
        open = false;
        synchronized (mLocks) {
            for (Lock l : mLocks) l.valid = false;
            mLocks.clear();
        }
    }

    private void ensureOpen() throws IOException {
        if (!open) throw new ClosedChannelException();
    }

    public int read(ByteBuffer dst) throws IOException {
        byte[] tmp = new byte[dst.remaining()];
        int n = Os.read(fd, tmp, 0, tmp.length);
        if (n > 0) {
            dst.put(tmp, 0, n);
        }
        return n;
    }

    public int write(ByteBuffer src) throws IOException {
        byte[] tmp = new byte[src.remaining()];
        src.get(tmp);
        Os.write(fd, tmp, 0, tmp.length);
        return tmp.length;
    }

    public long position() throws IOException {
        return Os.seek(fd, 0, Os.SEEK_CUR);
    }

    public FileChannel position(long newPosition) throws IOException {
        Os.seek(fd, newPosition, Os.SEEK_SET);
        return this;
    }

    public long size() throws IOException {
        long cur = position();
        long end = Os.seek(fd, 0, Os.SEEK_END);
        Os.seek(fd, cur, Os.SEEK_SET);
        return end;
    }

    public FileChannel truncate(long size) throws IOException {
        Os.ftruncate(fd, size);
        return this;
    }

    public void force(boolean metaData) throws IOException {
        Os.fsync(fd);
    }

    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        long saved = position();
        position(position);
        ByteBuffer buf = ByteBuffer.allocate((int) Math.min(count, 65536));
        long total = 0;
        while (total < count) {
            buf.clear();
            buf.limit((int) Math.min(buf.capacity(), count - total));
            int n = read(buf);
            if (n <= 0) {
                break;
            }
            buf.flip();
            target.write(buf);
            total += n;
        }
        position(saved);
        return total;
    }

    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        long saved = position();
        position(position);
        ByteBuffer buf = ByteBuffer.allocate((int) Math.min(count, 65536));
        long total = 0;
        while (total < count) {
            buf.clear();
            buf.limit((int) Math.min(buf.capacity(), count - total));
            int n = src.read(buf);
            if (n <= 0) {
                break;
            }
            buf.flip();
            write(buf);
            total += n;
        }
        position(saved);
        return total;
    }

    /**
     * A heap copy of the region (see MappedByteBuffer): READ_WRITE copies are written back by force(). A READ_WRITE
     * map past the end grows the file, as mapping does.
     */
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        ensureOpen();
        if (mode == null) throw new NullPointerException("Mode is null");
        if (position < 0) throw new IllegalArgumentException("Negative position");
        if (size < 0) throw new IllegalArgumentException("Negative size");
        if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("Size exceeds Integer.MAX_VALUE");
        if (mode != MapMode.READ_ONLY && !writable) throw new NonWritableChannelException();
        if (!readable) throw new NonReadableChannelException();
        if (mode == MapMode.READ_WRITE && size() < position + size) truncateUp(position + size);
        byte[] data = new byte[(int) size];
        long saved = position();
        Os.seek(fd, position, Os.SEEK_SET);
        int off = 0;
        while (off < data.length) {
            int n = Os.read(fd, data, off, data.length - off);
            if (n <= 0) break;
            off += n;
        }
        Os.seek(fd, saved, Os.SEEK_SET);
        return java.nio.HeapMappedByteBuffer.create(data, fd, position, mode == MapMode.READ_ONLY,
                mode == MapMode.READ_WRITE);
    }

    private void truncateUp(long length) throws IOException {
        Os.ftruncate(fd, length);
    }

    // Locks this channel holds. There is one app process, so nothing else can hold a lock on the file; overlapping
    // requests on the same channel fail as the JDK's in-process lock table makes them.
    private final ArrayList<Lock> mLocks = new ArrayList<Lock>();

    private final class Lock extends FileLock {
        boolean valid = true;

        Lock(long position, long size, boolean shared) {
            super(FileChannel.this, position, size, shared);
        }

        public boolean isValid() {
            synchronized (mLocks) {
                return valid && open;
            }
        }

        public void release() throws IOException {
            if (!open) throw new ClosedChannelException();
            synchronized (mLocks) {
                valid = false;
                mLocks.remove(this);
            }
        }
    }

    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return tryLock(position, size, shared);
    }

    public final FileLock lock() throws IOException {
        return lock(0L, Long.MAX_VALUE, false);
    }

    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        ensureOpen();
        if (shared && !readable) throw new NonReadableChannelException();
        if (!shared && !writable) throw new NonWritableChannelException();
        Lock lock = new Lock(position, size, shared);
        synchronized (mLocks) {
            for (Lock l : mLocks) {
                if (l.overlaps(position, size)) throw new OverlappingFileLockException();
            }
            mLocks.add(lock);
        }
        return lock;
    }

    public final FileLock tryLock() throws IOException {
        return tryLock(0L, Long.MAX_VALUE, false);
    }

    public static class MapMode {
        public static final MapMode READ_ONLY = new MapMode("READ_ONLY");
        public static final MapMode READ_WRITE = new MapMode("READ_WRITE");
        public static final MapMode PRIVATE = new MapMode("PRIVATE");

        private final String name;

        private MapMode(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }
}
