package java.io;

import libcore.io.Os;

public class FileInputStream extends InputStream {
    private final FileDescriptor fd;
    private final String path;
    private boolean closed;
    private final boolean owned;
    private java.nio.channels.FileChannel channel;

    public FileInputStream(String name) throws FileNotFoundException {
        this(name != null ? new File(name) : null);
    }

    public FileInputStream(File file) throws FileNotFoundException {
        if (file == null) {
            throw new NullPointerException();
        }
        path = file.getPath();
        if (file.isDirectory()) {
            throw new FileNotFoundException(path + " (Is a directory)");
        }
        fd = new FileDescriptor(Os.open(path, Os.O_RDONLY, 0));
        owned = true;
    }

    public FileInputStream(FileDescriptor fdObj) {
        fd = fdObj;
        path = null;
        owned = false;
    }

    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n <= 0 ? -1 : b[0] & 0xff;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream Closed");
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        return Os.read(fd.fd, b, off, len);
    }

    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        try {
            long cur = Os.seek(fd.fd, 0, Os.SEEK_CUR);
            long end = Os.seek(fd.fd, 0, Os.SEEK_END);
            long target = Math.min(end, cur + n);
            Os.seek(fd.fd, target, Os.SEEK_SET);
            return target - cur;
        } catch (IOException e) {
            return super.skip(n);
        }
    }

    public int available() throws IOException {
        if (closed) {
            throw new IOException("Stream Closed");
        }
        return Os.available(fd.fd);
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (owned) {
            Os.close(fd.fd);
        }
        if (channel != null) {
            channel.close();
        }
    }

    public final FileDescriptor getFD() throws IOException {
        return fd;
    }

    public java.nio.channels.FileChannel getChannel() {
        synchronized (this) {
            if (channel == null) {
                channel = sun.nio.ch.FileChannelImpl.open(fd.fd, path, this, true, false, false);
            }
            return channel;
        }
    }
}
