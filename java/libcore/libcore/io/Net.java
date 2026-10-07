package libcore.io;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Thin native socket layer under java.net (src/native/java_net.c). Descriptors are plain ints; addresses are 4- or
 * 16-byte arrays in network order. Calls that wait release the GIL. Timeouts are in milliseconds, 0 means forever.
 */
public final class Net {
    private Net() {
    }

    /** Resolved addresses as [length][bytes] entries, IPv4 first. */
    public static native byte[] getaddrinfo(String host) throws UnknownHostException;

    public static native String getnameinfo(byte[] addr);

    public static native int socket(boolean ipv6, boolean stream) throws SocketException;

    public static native void connect(int fd, byte[] addr, int port, int timeoutMs) throws IOException;

    /** addr null binds the wildcard address of the socket's family. */
    public static native void bind(int fd, byte[] addr, int port) throws SocketException;

    public static native void listen(int fd, int backlog) throws SocketException;

    /** peer receives the address (16 bytes), info = {port, address length}. */
    public static native int accept(int fd, int timeoutMs, byte[] peer, int[] info) throws IOException;

    /** Returns -1 at end of stream. */
    public static native int recv(int fd, byte[] b, int off, int len, int timeoutMs) throws IOException;

    public static native void send(int fd, byte[] b, int off, int len) throws IOException;

    public static native int recvfrom(int fd, byte[] b, int off, int len, int timeoutMs, byte[] peer, int[] info)
            throws IOException;

    public static native void sendto(int fd, byte[] b, int off, int len, byte[] addr, int port) throws IOException;

    public static native int getsockname(int fd, byte[] addr, int[] info) throws SocketException;

    public static native int getpeername(int fd, byte[] addr, int[] info) throws SocketException;

    /** opt is a java.net.SocketOptions id. SO_LINGER takes seconds, or -1 for off. */
    public static native void setOption(int fd, int opt, int value) throws SocketException;

    public static native int getOption(int fd, int opt) throws SocketException;

    public static native int available(int fd);

    /** how: 0 input, 1 output, 2 both. */
    public static native void shutdown(int fd, int how);

    public static native void close(int fd);

    /** Splits a getaddrinfo result into address arrays. */
    public static byte[][] split(byte[] packed) {
        int count = 0;
        for (int i = 0; i < packed.length; i += 1 + packed[i]) {
            count++;
        }
        byte[][] out = new byte[count][];
        int k = 0;
        for (int i = 0; i < packed.length; i += 1 + packed[i]) {
            out[k] = new byte[packed[i]];
            System.arraycopy(packed, i + 1, out[k], 0, packed[i]);
            k++;
        }
        return out;
    }

    /** Copies the first info[1] bytes of a peer buffer. */
    public static byte[] trim(byte[] peer, int[] info) {
        byte[] out = new byte[info[1] == 16 ? 16 : 4];
        System.arraycopy(peer, 0, out, 0, out.length);
        return out;
    }
}
