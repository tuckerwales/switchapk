package android.net;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Network traffic statistics. There are no per-interface or per-uid counters on the Switch, so
 * every counter reports {@link #UNSUPPORTED}, as Android does where the kernel lacks them. Thread
 * tags and uids are kept per thread so get/set pairs behave.
 */
public class TrafficStats {
    public static final int UNSUPPORTED = -1;

    private static final ThreadLocal<int[]> sThreadTag = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[] {-1, -1};
        }
    };

    public TrafficStats() {
    }

    public static void setThreadStatsTag(int tag) { sThreadTag.get()[0] = tag; }

    public static int getAndSetThreadStatsTag(int tag) {
        int[] t = sThreadTag.get();
        int old = t[0];
        t[0] = tag;
        return old;
    }

    public static int getThreadStatsTag() { return sThreadTag.get()[0]; }

    public static void clearThreadStatsTag() { sThreadTag.get()[0] = -1; }

    public static void setThreadStatsUid(int uid) { sThreadTag.get()[1] = uid; }

    public static int getThreadStatsUid() { return sThreadTag.get()[1]; }

    public static void clearThreadStatsUid() { sThreadTag.get()[1] = -1; }

    public static void tagSocket(Socket socket) throws SocketException {}

    public static void untagSocket(Socket socket) throws SocketException {}

    public static void tagDatagramSocket(DatagramSocket socket) throws SocketException {}

    public static void untagDatagramSocket(DatagramSocket socket) throws SocketException {}

    public static void tagFileDescriptor(FileDescriptor fd) throws IOException {}

    public static void untagFileDescriptor(FileDescriptor fd) throws IOException {}

    public static void incrementOperationCount(int operationCount) {}

    public static void incrementOperationCount(int tag, int operationCount) {}

    public static long getMobileTxPackets() { return UNSUPPORTED; }

    public static long getMobileRxPackets() { return UNSUPPORTED; }

    public static long getMobileTxBytes() { return UNSUPPORTED; }

    public static long getMobileRxBytes() { return UNSUPPORTED; }

    public static long getTxPackets(String iface) { return UNSUPPORTED; }

    public static long getRxPackets(String iface) { return UNSUPPORTED; }

    public static long getTxBytes(String iface) { return UNSUPPORTED; }

    public static long getRxBytes(String iface) { return UNSUPPORTED; }

    public static long getTotalTxPackets() { return UNSUPPORTED; }

    public static long getTotalRxPackets() { return UNSUPPORTED; }

    public static long getTotalTxBytes() { return UNSUPPORTED; }

    public static long getTotalRxBytes() { return UNSUPPORTED; }

    public static long getUidTxBytes(int uid) { return UNSUPPORTED; }

    public static long getUidRxBytes(int uid) { return UNSUPPORTED; }

    public static long getUidTxPackets(int uid) { return UNSUPPORTED; }

    public static long getUidRxPackets(int uid) { return UNSUPPORTED; }

    public static long getUidTcpTxBytes(int uid) { return UNSUPPORTED; }

    public static long getUidTcpRxBytes(int uid) { return UNSUPPORTED; }

    public static long getUidUdpTxBytes(int uid) { return UNSUPPORTED; }

    public static long getUidUdpRxBytes(int uid) { return UNSUPPORTED; }

    public static long getUidTcpTxSegments(int uid) { return UNSUPPORTED; }

    public static long getUidTcpRxSegments(int uid) { return UNSUPPORTED; }

    public static long getUidUdpTxPackets(int uid) { return UNSUPPORTED; }

    public static long getUidUdpRxPackets(int uid) { return UNSUPPORTED; }
}
