package android.webkit;

import java.util.HashSet;
import java.util.Set;

/** No geolocation without a JavaScript engine: remembers allowed origins only so apps can read them back. */
public class GeolocationPermissions {
    public interface Callback {
        void invoke(String origin, boolean allow, boolean retain);
    }

    private static GeolocationPermissions sInstance;
    private final Set<String> mAllowed = new HashSet<String>();

    /** Hidden on Android; package-private here. */
    GeolocationPermissions() {
    }

    public static synchronized GeolocationPermissions getInstance() {
        if (sInstance == null) sInstance = new GeolocationPermissions();
        return sInstance;
    }

    public void getOrigins(ValueCallback<Set<String>> callback) {
        Set<String> copy;
        synchronized (mAllowed) {
            copy = new HashSet<String>(mAllowed);
        }
        CallbackUtil.post(callback, copy);
    }

    public void getAllowed(String origin, ValueCallback<Boolean> callback) {
        boolean allowed;
        synchronized (mAllowed) {
            allowed = origin != null && mAllowed.contains(origin);
        }
        CallbackUtil.post(callback, allowed);
    }

    public void clear(String origin) {
        synchronized (mAllowed) {
            mAllowed.remove(origin);
        }
    }

    public void allow(String origin) {
        if (origin == null) return;
        synchronized (mAllowed) {
            mAllowed.add(origin);
        }
    }

    public void clearAll() {
        synchronized (mAllowed) {
            mAllowed.clear();
        }
    }
}
