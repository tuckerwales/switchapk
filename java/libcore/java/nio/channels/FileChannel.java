package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
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

    public ByteBuffer map(MapMode mode, long position, long size) throws IOException {
        long saved = position();
        position(position);
        ByteBuffer b = ByteBuffer.allocate((int) size);
        read(b);
        b.flip();
        position(saved);
        return b;
    }

    public static class MapMode {
        public static final MapMode READ_ONLY = new MapMode();
        public static final MapMode READ_WRITE = new MapMode();
        public static final MapMode PRIVATE = new MapMode();
    }
}
