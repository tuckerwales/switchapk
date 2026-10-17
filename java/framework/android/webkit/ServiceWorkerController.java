package android.webkit;

/** There are no service workers without a JavaScript engine; this keeps the settings so apps can read them back. */
public abstract class ServiceWorkerController {
    private static ServiceWorkerController sInstance;

    public ServiceWorkerController() {
    }

    public static synchronized ServiceWorkerController getInstance() {
        if (sInstance == null) sInstance = new Impl();
        return sInstance;
    }

    public abstract ServiceWorkerWebSettings getServiceWorkerWebSettings();

    public abstract void setServiceWorkerClient(ServiceWorkerClient client);

    private static final class Impl extends ServiceWorkerController {
        private final SettingsImpl mSettings = new SettingsImpl();

        @Override
        public ServiceWorkerWebSettings getServiceWorkerWebSettings() {
            return mSettings;
        }

        @Override
        public void setServiceWorkerClient(ServiceWorkerClient client) {
        }
    }

    private static final class SettingsImpl extends ServiceWorkerWebSettings {
        private int mCacheMode = WebSettings.LOAD_DEFAULT;
        private boolean mAllowContentAccess = true;
        private boolean mAllowFileAccess = true;
        private boolean mBlockNetworkLoads;

        @Override public synchronized void setCacheMode(int mode) { mCacheMode = mode; }
        @Override public synchronized int getCacheMode() { return mCacheMode; }
        @Override public synchronized void setAllowContentAccess(boolean allow) { mAllowContentAccess = allow; }
        @Override public synchronized boolean getAllowContentAccess() { return mAllowContentAccess; }
        @Override public synchronized void setAllowFileAccess(boolean allow) { mAllowFileAccess = allow; }
        @Override public synchronized boolean getAllowFileAccess() { return mAllowFileAccess; }
        @Override public synchronized void setBlockNetworkLoads(boolean flag) { mBlockNetworkLoads = flag; }
        @Override public synchronized boolean getBlockNetworkLoads() { return mBlockNetworkLoads; }
    }
}
