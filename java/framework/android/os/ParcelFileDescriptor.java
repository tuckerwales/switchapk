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
    // A real descriptor: native code (androidx datastore's shared counter) truncates and maps it.
    private final FileDescriptor mFd = new FileDescriptor();
    private final ParcelFileDescriptor mWrapped;
    private boolean mClosed;

    ParcelFileDescriptor(File file, int mode, int fd) {
        mFile = file;
        mMode = mode;
        mWrapped = null;
        mFd.setInt$(fd);
    }

    public ParcelFileDescriptor(ParcelFileDescriptor wrapped) {
        mFile = wrapped.mFile;
        mMode = wrapped.mMode;
        mWrapped = wrapped;
        mFd.setInt$(wrapped.mFd.getInt$());
    }

    public static ParcelFileDescriptor open(File file, int mode) throws FileNotFoundException {
        if ((mode & MODE_CREATE) == 0 && !file.exists()) throw new FileNotFoundException(file.getPath());
        int flags;
        if ((mode & MODE_READ_WRITE) == MODE_READ_WRITE) flags = libcore.io.Os.O_RDWR;
        else if ((mode & MODE_WRITE_ONLY) != 0) flags = libcore.io.Os.O_WRONLY;
        else flags = libcore.io.Os.O_RDONLY;
        if ((mode & MODE_CREATE) != 0) flags |= libcore.io.Os.O_CREAT;
        if ((mode & MODE_TRUNCATE) != 0) flags |= libcore.io.Os.O_TRUNC;
        if ((mode & MODE_APPEND) != 0) flags |= libcore.io.Os.O_APPEND;
        int fd = libcore.io.Os.open(file.getPath(), flags, 0600);
        return new ParcelFileDescriptor(file, mode, fd);
    }

    /** Takes ownership of fd. */
    public static ParcelFileDescriptor adoptFd(int fd) { return new ParcelFileDescriptor(null, MODE_READ_WRITE, fd); }

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

    public File getFile() { return mFile; }
    public FileDescriptor getFileDescriptor() { return mFd; }
    public long getStatSize() { return mFile != null ? mFile.length() : -1; }

    public int getFd() {
        if (mClosed) throw new IllegalStateException("Already closed");
        return mFd.getInt$();
    }

    public int detachFd() {
        if (mClosed) throw new IllegalStateException("Already closed");
        int fd = mFd.getInt$();
        mFd.setInt$(-1);
        mClosed = true;
        return fd;
    }

    public void close() throws IOException {
        if (mClosed) return;
        mClosed = true;
        if (mWrapped != null) {
            mWrapped.close();
        } else if (mFd.getInt$() >= 0) {
            libcore.io.Os.close(mFd.getInt$());
        }
        mFd.setInt$(-1);
    }

    public void closeWithError(String msg) throws IOException { close(); }
    public boolean canDetectErrors() { return false; }
    public void checkError() throws IOException {}

    public String toString() { return "{ParcelFileDescriptor: " + mFd.getInt$() + "}"; }
    public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<ParcelFileDescriptor> CREATOR = new Parcelable.Creator<ParcelFileDescriptor>() {
        public ParcelFileDescriptor createFromParcel(Parcel in) { return (ParcelFileDescriptor) in.readValue(null); }
        public ParcelFileDescriptor[] newArray(int size) { return new ParcelFileDescriptor[size]; }
    };

    public static class AutoCloseInputStream extends FileInputStream {
        private final ParcelFileDescriptor mPfd;

        public AutoCloseInputStream(ParcelFileDescriptor pfd) {
            super(pfd.getFileDescriptor());
            mPfd = pfd;
        }

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

        public void close() throws IOException {
            try {
                super.close();
            } finally {
                mPfd.close();
            }
        }
    }
}
