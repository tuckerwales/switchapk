package android.os;

public class StatFs {
    public StatFs(String path) {}
    public void restat(String path) {}
    public int getBlockSize() { return 4096; }
    public long getBlockSizeLong() { return 4096; }
    public int getBlockCount() { return (int) getBlockCountLong(); }
    public long getBlockCountLong() { return 8L * 1024 * 1024 * 1024 / 4096; }
    public int getFreeBlocks() { return (int) getFreeBlocksLong(); }
    public long getFreeBlocksLong() { return 4L * 1024 * 1024 * 1024 / 4096; }
    public long getFreeBytes() { return 4L * 1024 * 1024 * 1024; }
    public int getAvailableBlocks() { return (int) getAvailableBlocksLong(); }
    public long getAvailableBlocksLong() { return getFreeBlocksLong(); }
    public long getAvailableBytes() { return getFreeBytes(); }
    public long getTotalBytes() { return 8L * 1024 * 1024 * 1024; }
}
