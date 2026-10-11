package android.os;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ParcelFileDescriptor implements Parcelable, java.io.Closeable {
    public static final int MODE_WORLD_READABLE = 0x00000001;
    public static final int MODE_WORLD_WRITEABLE = 0x00000002;
    public static final int MODE_READ_ONLY = 0x10000000;
    public static final int MODE_WRITE_ONLY = 0x20000000;
    public static final int MODE_READ_WRITE = 0x30000000;
    public static final int MODE_CREATE = 0x08000000;
    public static final int MODE_TRUNCATE = 0x04000000;
    public static final int MODE_APPEND = 0x02000000;

    private final File mFile;
    private final int mMode;
    private final FileDescriptor mFd;
    /** The descriptor this one wraps (the copy constructor and wrap share it). */
    private final ParcelFileDescriptor mWrapped;
    private boolean mClosed;

    public interface OnCloseListener {
        void onClose(IOException e);
    }

    /** A real descriptor, opened from mode like Android's open(2) flags. */
    ParcelFileDescriptor(File file, int mode) throws FileNotFoundException {
        mFile = file;
        mMode = mode;
        mWrapped = null;
        int flags;
        if ((mode & MODE_READ_WRITE) == MODE_READ_WRITE) {
            flags = libcore.io.Os.O_RDWR;
        } else if ((mode & MODE_WRITE_ONLY) == MODE_WRITE_ONLY) {
            flags = libcore.io.Os.O_WRONLY;
        } else {
            flags = libcore.io.Os.O_RDONLY;
        }
        if ((mode & MODE_CREATE) != 0) flags |= libcore.io.Os.O_CREAT;
        if ((mode & MODE_TRUNCATE) != 0) flags |= libcore.io.Os.O_TRUNC;
        if ((mode & MODE_APPEND) != 0) flags |= libcore.io.Os.O_APPEND;
        mFd = new FileDescriptor();
        mFd.setInt$(libcore.io.Os.open(file.getPath(), flags, 0600));
    }

    private ParcelFileDescriptor(FileDescriptor fd) {
        mFile = null;
        mMode = 0;
        mWrapped = null;
        mFd = fd;
    }

    public ParcelFileDescriptor(ParcelFileDescriptor wrapped) {
        mFile = wrapped.mFile;
        mMode = wrapped.mMode;
        mWrapped = wrapped;
        mFd = wrapped.mFd;
    }

    public static ParcelFileDescriptor open(File file, int mode) throws FileNotFoundException {
        if ((mode & MODE_CREATE) == 0 && !file.exists()) throw new FileNotFoundException(file.getPath());
        if ((mode & MODE_READ_WRITE) == 0) {
            throw new IllegalArgumentException("Must specify MODE_READ_ONLY, MODE_WRITE_ONLY, or MODE_READ_WRITE");
        }
        return new ParcelFileDescriptor(file, mode);
    }

    /** The listener runs on close; there is no peer to report errors, so it gets null. */
    public static ParcelFileDescriptor open(File file, int mode, Handler handler, final OnCloseListener listener)
            throws IOException {
        if (handler == null) throw new IllegalArgumentException("Handler must not be null");
        if (listener == null) throw new IllegalArgumentException("Listener must not be null");
        return wrap(open(file, mode), handler, listener);
    }

    public static ParcelFileDescriptor wrap(ParcelFileDescriptor pfd, final Handler handler,
            final OnCloseListener listener) throws IOException {
        return new ParcelFileDescriptor(pfd) {
            @Override
            public void close() throws IOException {
                super.close();
                handler.post(new Runnable() {
                    public void run() { listener.onClose(null); }
                });
            }
        };
    }

    public static ParcelFileDescriptor adoptFd(int fd) {
        FileDescriptor f = new FileDescriptor();
        f.setInt$(fd);
        return new ParcelFileDescriptor(f);
    }

    public static ParcelFileDescriptor fromFd(int fd) throws IOException {
        return adoptFd(libcore.io.Os.dup(fd));
    }

    public static ParcelFileDescriptor dup(FileDescriptor orig) throws IOException {
        if (orig == null || !orig.valid()) throw new IOException("invalid file descriptor");
        return adoptFd(libcore.io.Os.dup(orig.getInt$()));
    }

    public ParcelFileDescriptor dup() throws IOException {
        if (mWrapped != null) return mWrapped.dup();
        return dup(getFileDescriptor());
    }

    public static int parseMode(String mode) {
        switch (mode) {
            case "r": return MODE_READ_ONLY;
            case "w": case "wt": return MODE_WRITE_ONLY | MODE_CREATE | MODE_TRUNCATE;
            case "wa": return MODE_WRITE_ONLY | MODE_CREATE | MODE_APPEND;
            case "rw": return MODE_READ_WRITE | MODE_CREATE;
            case "rwt": return MODE_READ_WRITE | MODE_CREATE | MODE_TRUNCATE;
        }
        throw new IllegalArgumentException("Bad mode '" + mode + "'");
    }

    /** @hide Android's hidden accessor: the path when this came from open(). */
    public File getFile() { return mFile; }

    public FileDescriptor getFileDescriptor() {
        if (mWrapped != null) return mWrapped.getFileDescriptor();
        return mFd;
    }

    public long getStatSize() {
        if (mWrapped != null) return mWrapped.getStatSize();
        if (mFile != null && mFile.isFile()) return mFile.length();
        return -1;
    }

    public int getFd() {
        if (mWrapped != null) return mWrapped.getFd();
        if (mClosed) throw new IllegalStateException("Already closed");
        return mFd.getInt$();
    }

    public int detachFd() {
        if (mWrapped != null) return mWrapped.detachFd();
        if (mClosed) throw new IllegalStateException("Already closed");
        int fd = mFd.getInt$();
        mFd.setInt$(-1);
        mClosed = true;
        return fd;
    }

    public void close() throws IOException {
        if (mWrapped != null) {
            mWrapped.close();
            return;
        }
        synchronized (this) {
            if (mClosed) return;
            mClosed = true;
        }
        int fd = mFd.getInt$();
        mFd.setInt$(-1);
        if (fd >= 0) libcore.io.Os.close(fd);
    }

    public void closeWithError(String msg) throws IOException {
        close();
    }

    public boolean canDetectErrors() {
        if (mWrapped != null) return mWrapped.canDetectErrors();
        return false;
    }

    public void checkError() throws IOException {
        if (mWrapped != null) mWrapped.checkError();
    }

    @Override
    public String toString() {
        if (mWrapped != null) return mWrapped.toString();
        return "{ParcelFileDescriptor: " + mFd.getInt$() + (mFile != null ? " " + mFile : "") + "}";
    }

    public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<ParcelFileDescriptor> CREATOR = new Parcelable.Creator<ParcelFileDescriptor>() {
        public ParcelFileDescriptor createFromParcel(Parcel in) { return (ParcelFileDescriptor) in.readValue(null); }
        public ParcelFileDescriptor[] newArray(int size) { return new ParcelFileDescriptor[size]; }
    };

    /** Reads the descriptor itself (its position is shared) and closes it when closed. */
    public static class AutoCloseInputStream extends FileInputStream {
        private final ParcelFileDescriptor mPfd;

        public AutoCloseInputStream(ParcelFileDescriptor pfd) {
            super(pfd.getFileDescriptor());
            mPfd = pfd;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                mPfd.close();
            }
        }
    }

    public static class AutoCloseOutputStream extends FileOutputStream {
        private final ParcelFileDescriptor mPfd;

        public AutoCloseOutputStream(ParcelFileDescriptor pfd) {
            super(pfd.getFileDescriptor());
            mPfd = pfd;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                mPfd.close();
            }
        }
    }
}
