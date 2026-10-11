package android.content.res;

import android.os.ParcelFileDescriptor;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

/**
 * Describes an asset inside the APK. An asset stored uncompressed gets a real read-only
 * descriptor on the APK with the entry's data offset, as on Android. A compressed asset
 * (where Android's openFd throws) keeps no descriptor; our media APIs recognise
 * AssetFileDescriptor and read either kind through AssetManager.
 */
public class AssetFileDescriptor implements Closeable, android.os.Parcelable {
    public static final long UNKNOWN_LENGTH = -1;

    private final AssetManager mAssets;
    private final String mAssetName;
    private final ParcelFileDescriptor mFd;
    private final long mStartOffset;
    private final long mLength;

    AssetFileDescriptor(AssetManager assets, String name, long offset, long length) {
        this(assets, name, null, offset, length);
    }

    AssetFileDescriptor(AssetManager assets, String name, ParcelFileDescriptor fd, long offset, long length) {
        mAssets = assets;
        mAssetName = name;
        mFd = fd;
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
    public ParcelFileDescriptor getParcelFileDescriptor() { return mFd; }
    public FileDescriptor getFileDescriptor() { return mFd != null ? mFd.getFileDescriptor() : new FileDescriptor(); }
    public long getStartOffset() { return mStartOffset < 0 ? 0 : mStartOffset; }
    public long getLength() { return mLength; }
    public long getDeclaredLength() { return mLength; }
    public void close() throws IOException {
        if (mFd != null) mFd.close();
    }

    public java.io.FileInputStream createInputStream() throws IOException {
        if (mAssetName != null) return new AutoCloseInputStream(this);
        if (mFd != null) return new ParcelFileDescriptor.AutoCloseInputStream(mFd);
        return new AutoCloseInputStream(new ByteArrayInputStream(new byte[0]));
    }

    public java.io.FileOutputStream createOutputStream() throws IOException {
        throw new IOException("assets are read-only");
    }

    /** A FileInputStream over an in-memory asset. */
    public static class AutoCloseInputStream extends java.io.FileInputStream {
        private final InputStream mIn;
        private final AssetFileDescriptor mOwner;

        AutoCloseInputStream(InputStream in) {
            super(new FileDescriptor());
            mIn = in;
            mOwner = null;
        }

        public AutoCloseInputStream(AssetFileDescriptor fd) throws IOException {
            super(new FileDescriptor());
            mIn = fd.mAssetName != null ? fd.mAssets.open(fd.mAssetName) : new ByteArrayInputStream(new byte[0]);
            mOwner = fd;
        }

        @Override public int read() throws IOException { return mIn.read(); }
        @Override public int read(byte[] b) throws IOException { return mIn.read(b, 0, b.length); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return mIn.read(b, off, len); }
        @Override public long skip(long n) throws IOException { return mIn.skip(n); }
        @Override public int available() throws IOException { return mIn.available(); }
        @Override public void close() throws IOException {
            mIn.close();
            if (mOwner != null) mOwner.close();
        }
        @Override public boolean markSupported() { return mIn.markSupported(); }
        @Override public synchronized void mark(int readlimit) { mIn.mark(readlimit); }
        @Override public synchronized void reset() throws IOException { mIn.reset(); }
    }

    public int describeContents() { return 0; }
    public void writeToParcel(android.os.Parcel out, int flags) { out.writeValue(this); }

    @Override
    public String toString() { return "{AssetFileDescriptor: " + (mAssetName != null ? mAssetName : mFd) + " start=" + mStartOffset + " len=" + mLength + "}"; }
}
