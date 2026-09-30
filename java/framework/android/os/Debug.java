package android.os;

public final class Debug {
    public static final int SHOW_FULL_DETAIL = 1;
    public static final int SHOW_CLASSLOADER = (1 << 1);
    public static final int SHOW_INITIALIZED = (1 << 2);

    private Debug() {}

    public static class MemoryInfo {
        public int dalvikPss, dalvikPrivateDirty, dalvikSharedDirty;
        public int nativePss, nativePrivateDirty, nativeSharedDirty;
        public int otherPss, otherPrivateDirty, otherSharedDirty;
        public int getTotalPss() { return dalvikPss + nativePss + otherPss; }
        public int getTotalPrivateDirty() { return dalvikPrivateDirty + nativePrivateDirty + otherPrivateDirty; }
        public int getTotalSharedDirty() { return 0; }
        public int getTotalPrivateClean() { return 0; }
        public int getTotalSharedClean() { return 0; }
        public int getTotalSwappablePss() { return 0; }
        public String getMemoryStat(String statName) { return "0"; }
        public java.util.Map<String, String> getMemoryStats() { return new java.util.HashMap<String, String>(); }
    }

    public static void waitForDebugger() {}
    public static boolean waitingForDebugger() { return false; }
    public static boolean isDebuggerConnected() { return false; }
    public static void startMethodTracing() {}
    public static void startMethodTracing(String tracePath) {}
    public static void startMethodTracing(String tracePath, int bufferSize) {}
    public static void stopMethodTracing() {}
    public static long threadCpuTimeNanos() { return System.nanoTime(); }
    public static void startAllocCounting() {}
    public static void stopAllocCounting() {}
    public static long getNativeHeapSize() { return 64L << 20; }
    public static long getNativeHeapAllocatedSize() { return 16L << 20; }
    public static long getNativeHeapFreeSize() { return 48L << 20; }
    public static long getPss() { return 32 * 1024; }
    public static void getMemoryInfo(MemoryInfo memoryInfo) {}
    public static int getGlobalAllocCount() { return 0; }
    public static int getGlobalAllocSize() { return 0; }
    public static void dumpHprofData(String fileName) {}
    public static String getRuntimeStat(String statName) { return null; }
    public static java.util.Map<String, String> getRuntimeStats() { return new java.util.HashMap<String, String>(); }
}
