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
    private final FileDescriptor mFd = new FileDescriptor();

    ParcelFileDescriptor(File file, int mode) {
        mFile = file;
        mMode = mode;
    }

    public static ParcelFileDescriptor open(File file, int mode) throws FileNotFoundException {
        if ((mode & MODE_CREATE) == 0 && !file.exists()) throw new FileNotFoundException(file.getPath());
        return new ParcelFileDescriptor(file, mode);
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

    public File getFile() { return mFile; }
    public FileDescriptor getFileDescriptor() { return mFd; }
    public long getStatSize() { return mFile.length(); }
    public int getFd() { return -1; }
    public int detachFd() { return -1; }
    public void close() throws IOException {}
    public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<ParcelFileDescriptor> CREATOR = new Parcelable.Creator<ParcelFileDescriptor>() {
        public ParcelFileDescriptor createFromParcel(Parcel in) { return (ParcelFileDescriptor) in.readValue(null); }
        public ParcelFileDescriptor[] newArray(int size) { return new ParcelFileDescriptor[size]; }
    };

    public static class AutoCloseInputStream extends FileInputStream {
        public AutoCloseInputStream(ParcelFileDescriptor pfd) throws FileNotFoundException { super(pfd.mFile); }
    }

    public static class AutoCloseOutputStream extends FileOutputStream {
        public AutoCloseOutputStream(ParcelFileDescriptor pfd) throws FileNotFoundException {
            super(pfd.mFile, (pfd.mMode & MODE_APPEND) != 0);
        }
    }
}
