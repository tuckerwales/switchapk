package java.io;

public final class FileDescriptor {
    int fd;

    public static final FileDescriptor in = new FileDescriptor(0);
    public static final FileDescriptor out = new FileDescriptor(1);
    public static final FileDescriptor err = new FileDescriptor(2);

    public FileDescriptor() {
        fd = -1;
    }

    FileDescriptor(int fd) {
        this.fd = fd;
    }

    public boolean valid() {
        return fd != -1;
    }

    public void sync() throws SyncFailedException {
        try {
            libcore.io.Os.fsync(fd);
        } catch (IOException e) {
            throw new SyncFailedException(e.getMessage());
        }
    }

    public int getInt$() {
        return fd;
    }

    public void setInt$(int fd) {
        this.fd = fd;
    }
}
