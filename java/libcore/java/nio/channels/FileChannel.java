package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import libcore.io.Os;

public class FileChannel extends java.nio.channels.spi.AbstractInterruptibleChannel
        implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel {
    private final int fd;
    private final boolean readable, writable;
    private final ArrayList<FileLock> locks = new ArrayList<FileLock>();

    FileChannel(int fd, boolean readable, boolean writable) {
        this.fd = fd;
        this.readable = readable;
        this.writable = writable;
    }

    public static FileChannel forFd(int fd, boolean readable, boolean writable) {
        return new FileChannel(fd, readable, writable);
    }

    protected void implCloseChannel() throws IOException {
        synchronized (locks) {
            locks.clear();
        }
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

    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            if (!dsts[i].hasRemaining()) continue;
            int n = read(dsts[i]);
            if (n < 0) return total == 0 ? -1 : total;
            total += n;
            if (dsts[i].hasRemaining()) break;
        }
        return total;
    }

    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += write(srcs[i]);
        }
        return total;
    }

    /** Reads at an absolute file position without moving the channel position. */
    public int read(ByteBuffer dst, long position) throws IOException {
        if (position < 0) throw new IllegalArgumentException("Negative position");
        synchronized (this) {
            long saved = position();
            try {
                position(position);
                return read(dst);
            } finally {
                position(saved);
            }
        }
    }

    /** Writes at an absolute file position without moving the channel position. */
    public int write(ByteBuffer src, long position) throws IOException {
        if (position < 0) throw new IllegalArgumentException("Negative position");
        synchronized (this) {
            long saved = position();
            try {
                position(position);
                return write(src);
            } finally {
                position(saved);
            }
        }
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

    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("Size exceeds Integer.MAX_VALUE");
        MappedByteBuffer b = (MappedByteBuffer) ByteBuffer.allocate((int) size);
        while (b.hasRemaining()) {
            if (read(b, position + b.position()) <= 0) break;
        }
        b.clear();
        if (mode == MapMode.READ_WRITE) MappedByteBuffer.attachToFile(b, this, position);
        return b;
    }

    // switchapk runs one app per process, so locks only need to be tracked, not
    // enforced against other processes. Overlapping locks from the same process
    // throw, as on Android.
    public final FileLock lock() throws IOException {
        return lock(0L, Long.MAX_VALUE, false);
    }

    public FileLock lock(long position, long size, boolean shared) throws IOException {
        FileLock lock = tryLock(position, size, shared);
        if (lock == null) throw new OverlappingFileLockException();
        return lock;
    }

    public final FileLock tryLock() throws IOException {
        return tryLock(0L, Long.MAX_VALUE, false);
    }

    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        if (!isOpen()) throw new ClosedChannelException();
        if (shared && !readable) throw new NonReadableChannelException();
        if (!shared && !writable) throw new NonWritableChannelException();
        synchronized (locks) {
            for (FileLock l : locks) {
                if (l.overlaps(position, size)) throw new OverlappingFileLockException();
            }
            FileLock lock = new Lock(this, position, size, shared);
            locks.add(lock);
            return lock;
        }
    }

    private static final class Lock extends FileLock {
        private boolean valid = true;

        Lock(FileChannel channel, long position, long size, boolean shared) {
            super(channel, position, size, shared);
        }

        public boolean isValid() {
            synchronized (channel().locks) {
                return valid && channel().isOpen();
            }
        }

        public void release() throws IOException {
            synchronized (channel().locks) {
                valid = false;
                channel().locks.remove(this);
            }
        }
    }

    public static class MapMode {
        public static final MapMode READ_ONLY = new MapMode();
        public static final MapMode READ_WRITE = new MapMode();
        public static final MapMode PRIVATE = new MapMode();
    }
}
