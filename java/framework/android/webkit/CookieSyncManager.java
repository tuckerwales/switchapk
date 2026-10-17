package android.webkit;

import android.content.Context;

/** Deprecated: cookies live in CookieManager, which needs no syncing. All operations are no-ops. */
@Deprecated
public final class CookieSyncManager implements Runnable {
    private static CookieSyncManager sRef;
    private static final Object sLock = new Object();

    private CookieSyncManager() {
    }

    public static CookieSyncManager getInstance() {
        synchronized (sLock) {
            if (sRef == null) {
                throw new IllegalStateException("CookieSyncManager::createInstance() needs to be called "
                        + "before CookieSyncManager::getInstance()");
            }
            return sRef;
        }
    }

    public static CookieSyncManager createInstance(Context context) {
        synchronized (sLock) {
            if (context == null) throw new IllegalArgumentException("Invalid context argument");
            if (sRef == null) sRef = new CookieSyncManager();
            return sRef;
        }
    }

    @Deprecated
    public void sync() {
        CookieManager.getInstance().flush();
    }

    @Deprecated
    protected void syncFromRamToFlash() {
        CookieManager.getInstance().flush();
    }

    @Deprecated
    public void resetSync() {
    }

    @Deprecated
    public void startSync() {
    }

    @Deprecated
    public void stopSync() {
    }

    @Override
    public void run() {
    }
}
