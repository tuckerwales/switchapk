package libcore.ssl;

import java.util.Collections;
import java.util.Enumeration;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

/** Sessions are not cached or resumed yet: the context only keeps the settings apps make. */
final class SessionContextImpl implements SSLSessionContext {
    private int timeout = 8 * 60 * 60;
    private int cacheSize = 10;

    public SSLSession getSession(byte[] sessionId) {
        if (sessionId == null) throw new NullPointerException("sessionId == null");
        return null;
    }

    public Enumeration<byte[]> getIds() {
        return Collections.enumeration(Collections.<byte[]>emptyList());
    }

    public synchronized void setSessionTimeout(int seconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds < 0");
        timeout = seconds;
    }

    public synchronized int getSessionTimeout() {
        return timeout;
    }

    public synchronized void setSessionCacheSize(int size) {
        if (size < 0) throw new IllegalArgumentException("size < 0");
        cacheSize = size;
    }

    public synchronized int getSessionCacheSize() {
        return cacheSize;
    }
}
