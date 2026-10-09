package java.io;

import libcore.io.Os;

public class FileOutputStream extends OutputStream {
    private final FileDescriptor fd;
    private boolean closed;
    private final boolean owned;
    private final boolean isStd;
    private final StringBuilder lineBuf;
    private final String path;
    private final boolean append;
    private java.nio.channels.FileChannel channel;

    public FileOutputStream(String name) throws FileNotFoundException {
        this(name != null ? new File(name) : null, false);
    }

    public FileOutputStream(String name, boolean append) throws FileNotFoundException {
        this(name != null ? new File(name) : null, append);
    }

    public FileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    public FileOutputStream(File file, boolean append) throws FileNotFoundException {
        if (file == null) {
            throw new NullPointerException();
        }
        int flags = Os.O_WRONLY | Os.O_CREAT | (append ? Os.O_APPEND : Os.O_TRUNC);
        fd = new FileDescriptor(Os.open(file.getPath(), flags, 0666));
        owned = true;
        isStd = false;
        lineBuf = null;
        path = file.getPath();
        this.append = append;
    }

    public FileOutputStream(FileDescriptor fdObj) {
        fd = fdObj;
        owned = false;
        isStd = fdObj.fd == 1 || fdObj.fd == 2;
        lineBuf = null;
        path = null;
        append = false;
    }

    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream Closed");
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        Os.write(fd.fd, b, off, len);
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
                channel = sun.nio.ch.FileChannelImpl.open(fd.fd, path, this, false, true, append);
            }
            return channel;
        }
    }
}
