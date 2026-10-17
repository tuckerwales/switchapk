package android.webkit;

import android.content.Context;

import java.util.HashMap;

public abstract class WebViewDatabase {
    private static WebViewDatabase sInstance;

    public WebViewDatabase() {
    }

    public static synchronized WebViewDatabase getInstance(Context context) {
        if (sInstance == null) sInstance = new Impl();
        return sInstance;
    }

    @Deprecated
    public abstract boolean hasUsernamePassword();

    @Deprecated
    public abstract void clearUsernamePassword();

    public abstract boolean hasHttpAuthUsernamePassword();

    public abstract void clearHttpAuthUsernamePassword();

    public abstract void setHttpAuthUsernamePassword(String host, String realm, String username, String password);

    public abstract String[] getHttpAuthUsernamePassword(String host, String realm);

    @Deprecated
    public abstract boolean hasFormData();

    public abstract void clearFormData();

    /** In-memory HTTP auth credentials; form data and saved passwords are never stored (as on Android O+). */
    private static final class Impl extends WebViewDatabase {
        private final HashMap<String, String[]> mHttpAuth = new HashMap<String, String[]>();

        private static String key(String host, String realm) {
            return host + "\u0000" + realm;
        }

        @Override public boolean hasUsernamePassword() { return false; }
        @Override public void clearUsernamePassword() { }
        @Override public synchronized boolean hasHttpAuthUsernamePassword() { return !mHttpAuth.isEmpty(); }
        @Override public synchronized void clearHttpAuthUsernamePassword() { mHttpAuth.clear(); }

        @Override
        public synchronized void setHttpAuthUsernamePassword(String host, String realm, String username,
                String password) {
            if (host == null || realm == null) return;
            mHttpAuth.put(key(host, realm), new String[] {username, password});
        }

        @Override
        public synchronized String[] getHttpAuthUsernamePassword(String host, String realm) {
            if (host == null || realm == null) return null;
            String[] v = mHttpAuth.get(key(host, realm));
            return v == null ? null : v.clone();
        }

        @Override public boolean hasFormData() { return false; }
        @Override public void clearFormData() { }
    }
}
