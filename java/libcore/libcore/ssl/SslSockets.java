package libcore.ssl;

import javax.net.ssl.SSLSocket;

/** What android.net.ssl.SSLSockets needs from the platform sockets (the framework cannot see their class). */
public final class SslSockets {
    private SslSockets() {
    }

    public static boolean isPlatformSocket(SSLSocket socket) {
        return socket instanceof SslSocketImpl;
    }

    public static void setUseSessionTickets(SSLSocket socket, boolean use) {
        ((SslSocketImpl) socket).setUseSessionTickets(use);
    }
}
