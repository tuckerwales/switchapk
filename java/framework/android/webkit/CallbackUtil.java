package android.webkit;

import android.os.Handler;
import android.os.Looper;

/** Framework-internal: delivers ValueCallback results like WebView does (async on the caller's Looper). */
final class CallbackUtil {
    private CallbackUtil() {
    }

    /** Posts the result to the calling thread's Looper if it has one, else delivers it synchronously. */
    static <T> void post(final ValueCallback<T> callback, final T value) {
        if (callback == null) return;
        Looper looper = Looper.myLooper();
        if (looper == null) {
            callback.onReceiveValue(value);
            return;
        }
        new Handler(looper).post(new Runnable() {
            @Override
            public void run() {
                callback.onReceiveValue(value);
            }
        });
    }

    /** A Looper for Handler subclasses created off a Looper thread: the caller's, else the main one. */
    static Looper looper() {
        Looper l = Looper.myLooper();
        return l != null ? l : Looper.getMainLooper();
    }
}
