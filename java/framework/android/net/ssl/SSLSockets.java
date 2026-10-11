package android.net.ssl;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import libcore.ssl.SslSockets;

/** Platform TLS socket options (API 29). OkHttp configures sockets through this on Android 10 and later. */
public class SSLSockets {
    SSLSockets() {
    }

    public static boolean isSupportedSocket(SSLSocket socket) {
        return SslSockets.isPlatformSocket(socket);
    }

    public static void setUseSessionTickets(SSLSocket socket, boolean useSessionTickets) {
        if (!isSupportedSocket(socket)) throw new IllegalArgumentException("Not a supported socket: " + socket);
        SslSockets.setUseSessionTickets(socket, useSessionTickets);
    }

    public static byte[] exportKeyingMaterial(SSLSocket socket, String label, byte[] context, int length)
            throws SSLException {
        if (!isSupportedSocket(socket)) throw new IllegalArgumentException("Not a supported socket: " + socket);
        throw new SSLException("Keying material export is not supported");
    }
}
