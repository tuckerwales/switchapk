package android.webkit;

import android.os.Handler;

public abstract class WebMessagePort {
    public static abstract class WebMessageCallback {
        public WebMessageCallback() {
        }

        public void onMessage(WebMessagePort port, WebMessage message) {
        }
    }

    /** Hidden on Android (SystemApi); package-private here. */
    WebMessagePort() {
    }

    public abstract void postMessage(WebMessage message);

    public abstract void close();

    public abstract void setWebMessageCallback(WebMessageCallback callback);

    public abstract void setWebMessageCallback(WebMessageCallback callback, Handler handler);
}
