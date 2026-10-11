package sun.nio.ch;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;

/** Helpers shared by the channels: buffer staging and address checks. */
final class NioIo {
    private NioIo() {
    }

    static InetSocketAddress checkAddress(SocketAddress sa) {
        if (sa == null) {
            throw new NullPointerException();
        }
        if (!(sa instanceof InetSocketAddress)) {
            throw new UnsupportedAddressTypeException();
        }
        InetSocketAddress isa = (InetSocketAddress) sa;
        if (isa.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
        return isa;
    }

    /** The address to connect to: the wildcard means this host, as in the JDK. */
    static InetAddress target(InetSocketAddress isa) {
        InetAddress a = isa.getAddress();
        return a.isAnyLocalAddress() ? InetAddress.getLoopbackAddress() : a;
    }

    static InetAddress peer(byte[] peer, int[] info) {
        byte[] a = new byte[info[1] == 16 ? 16 : 4];
        System.arraycopy(peer, 0, a, 0, a.length);
        try {
            return InetAddress.getByAddress(a);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    /** Staging array for a read into dst (its own array when it has one). */
    static byte[] readArray(ByteBuffer dst) {
        return dst.hasArray() ? dst.array() : new byte[dst.remaining()];
    }

    static int readOffset(ByteBuffer dst) {
        return dst.hasArray() ? dst.arrayOffset() + dst.position() : 0;
    }

    /** Moves n bytes read into the staging array over to dst. */
    static void finishRead(ByteBuffer dst, byte[] b, int n) {
        if (n <= 0) {
            return;
        }
        if (dst.hasArray()) {
            dst.position(dst.position() + n);
        } else {
            dst.put(b, 0, n);
        }
    }

    static byte[] writeArray(ByteBuffer src) {
        if (src.hasArray()) {
            return src.array();
        }
        byte[] b = new byte[src.remaining()];
        src.duplicate().get(b);
        return b;
    }

    static int writeOffset(ByteBuffer src) {
        return src.hasArray() ? src.arrayOffset() + src.position() : 0;
    }

    static void finishWrite(ByteBuffer src, int n) {
        if (n > 0) {
            src.position(src.position() + n);
        }
    }
}
