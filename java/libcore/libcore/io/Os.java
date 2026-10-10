package libcore.io;

import java.io.FileNotFoundException;
import java.io.IOException;

/** Thin native file-system layer. Paths are Android paths; the VM maps them onto the host file system. */
public final class Os {
    private Os() {
    }

    public static final int O_RDONLY = 0;
    public static final int O_WRONLY = 1;
    public static final int O_RDWR = 2;
    public static final int O_CREAT = 0x40;
    public static final int O_TRUNC = 0x200;
    public static final int O_APPEND = 0x400;

    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    public static native int open(String path, int flags, int mode) throws FileNotFoundException;

    public static native int read(int fd, byte[] b, int off, int len) throws IOException;

    public static native void write(int fd, byte[] b, int off, int len) throws IOException;

    public static native void close(int fd) throws IOException;

    public static native int available(int fd) throws IOException;

    public static native long seek(int fd, long offset, int whence) throws IOException;

    public static native void fsync(int fd) throws IOException;

    public static native void ftruncate(int fd, long length) throws IOException;

    /** Returns {exists, isDir, isFile, length, mtimeMillis, readable, writable} encoded in a long[]. */
    public static native long[] stat(String path);

    /** {fragment size, total blocks, free blocks, blocks available to apps} of the file system, or null. */
    public static native long[] statvfs(String path);

    public static native String[] list(String path);

    public static native boolean mkdir(String path);

    public static native boolean remove(String path);

    public static native boolean rename(String from, String to);

    public static native boolean setLastModified(String path, long time);

    public static native String hostPath(String path);

    public static native long freeSpace(String path);
}
