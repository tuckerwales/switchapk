package android.content.res;

import android.os.ParcelFileDescriptor;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

/**
 * Describes an asset inside the APK. As on Android, a stored (uncompressed) asset has a real file
 * descriptor: the APK opened read-only, with the start offset of the asset's data, so code that passes
 * (fd, offset, length) along works. Compressed assets have no descriptor (Android refuses openFd for
 * them); media APIs recognise their AssetFileDescriptor and read them through AssetManager.
 */
public class AssetFileDescriptor implements Closeable, android.os.Parcelable {
    public static final long UNKNOWN_LENGTH = -1;

    private final AssetManager mAssets;
    private final String mAssetName;
    private ParcelFileDescriptor mFd;
    private final long mStartOffset;
    private final long mLength;

    AssetFileDescriptor(AssetManager assets, String name, long offset, long length) {
        mAssets = assets;
        mAssetName = name;
        mFd = null;
        mStartOffset = offset;
        mLength = length;
    }

    public AssetFileDescriptor(ParcelFileDescriptor fd, long startOffset, long length) {
        mAssets = null;
        mAssetName = null;
        mFd = fd;
        mStartOffset = startOffset;
        mLength = length;
    }

    /** Name of the asset (relative to assets/) when this describes an APK asset. */
    public String getAssetName() { return mAssetName; }
    public ParcelFileDescriptor getParcelFileDescriptor() {
        openApkFd();
        return mFd;
    }

    /** A stored asset gets the APK itself, read-only, as its descriptor. */
    private synchronized void openApkFd() {
        if (mFd != null || mAssetName == null || mStartOffset < 0) return;
        String apk = AssetManager.getApkPath();
        if (apk == null) return;
        try {
            mFd = ParcelFileDescriptor.open(new java.io.File(apk), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            mFd = null;
        }
    }
    public FileDescriptor getFileDescriptor() {
        openApkFd();
        return mFd != null ? mFd.getFileDescriptor() : new FileDescriptor();
    }
    public long getStartOffset() { return mStartOffset < 0 ? 0 : mStartOffset; }
    public long getLength() { return mLength; }
    public long getDeclaredLength() { return mLength; }
    public void close() throws IOException {
        ParcelFileDescriptor fd;
        synchronized (this) {
            fd = mAssetName != null ? mFd : null;
            if (fd != null) mFd = null;
        }
        if (fd != null) fd.close();
    }

    public java.io.FileInputStream createInputStream() throws IOException {
        if (mAssetName != null) return new AutoCloseInputStream(mAssets.open(mAssetName));
        if (mFd != null) return new ParcelFileDescriptor.AutoCloseInputStream(mFd);
        return new AutoCloseInputStream(new ByteArrayInputStream(new byte[0]));
    }

    public java.io.FileOutputStream createOutputStream() throws IOException {
        throw new IOException("assets are read-only");
    }

    /** A FileInputStream over an in-memory asset. */
    public static class AutoCloseInputStream extends java.io.FileInputStream {
        private final InputStream mIn;

        AutoCloseInputStream(InputStream in) {
            super(new FileDescriptor());
            mIn = in;
        }

        public AutoCloseInputStream(AssetFileDescriptor fd) throws IOException {
            this(fd.mAssetName != null ? fd.mAssets.open(fd.mAssetName) : new ByteArrayInputStream(new byte[0]));
        }

        @Override public int read() throws IOException { return mIn.read(); }
        @Override public int read(byte[] b) throws IOException { return mIn.read(b, 0, b.length); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return mIn.read(b, off, len); }
        @Override public long skip(long n) throws IOException { return mIn.skip(n); }
        @Override public int available() throws IOException { return mIn.available(); }
        @Override public void close() throws IOException { mIn.close(); }
        @Override public boolean markSupported() { return mIn.markSupported(); }
        @Override public synchronized void mark(int readlimit) { mIn.mark(readlimit); }
        @Override public synchronized void reset() throws IOException { mIn.reset(); }
    }

    public int describeContents() { return 0; }
    public void writeToParcel(android.os.Parcel out, int flags) { out.writeValue(this); }

    @Override
    public String toString() { return "{AssetFileDescriptor: " + (mAssetName != null ? mAssetName : mFd) + " start=" + mStartOffset + " len=" + mLength + "}"; }
}
