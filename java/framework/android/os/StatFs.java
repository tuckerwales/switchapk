package android.os;

/** File system space, from statvfs on the host and on the Switch's SD card. */
public class StatFs {
    private long mBlockSize = 4096;
    private long mBlocks;
    private long mFree;
    private long mAvailable;

    public StatFs(String path) {
        restat(path);
    }

    public void restat(String path) {
        long[] v = libcore.io.Os.statvfs(path);
        if (v == null) throw new IllegalArgumentException("Invalid path: " + path);
        mBlockSize = v[0];
        mBlocks = v[1];
        mFree = v[2];
        mAvailable = v[3];
    }

    @Deprecated
    public int getBlockSize() { return (int) mBlockSize; }
    public long getBlockSizeLong() { return mBlockSize; }
    @Deprecated
    public int getBlockCount() { return (int) mBlocks; }
    public long getBlockCountLong() { return mBlocks; }
    @Deprecated
    public int getFreeBlocks() { return (int) mFree; }
    public long getFreeBlocksLong() { return mFree; }
    public long getFreeBytes() { return mFree * mBlockSize; }
    @Deprecated
    public int getAvailableBlocks() { return (int) mAvailable; }
    public long getAvailableBlocksLong() { return mAvailable; }
    public long getAvailableBytes() { return mAvailable * mBlockSize; }
    public long getTotalBytes() { return mBlocks * mBlockSize; }
}
