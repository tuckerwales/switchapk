package android.webkit;

import android.graphics.Bitmap;

/** Deprecated on Android and a no-op in the Chromium WebView; same here. */
@Deprecated
public abstract class WebIconDatabase {
    @Deprecated
    public interface IconListener {
        void onReceivedIcon(String url, Bitmap icon);
    }

    private static WebIconDatabase sInstance;

    public WebIconDatabase() {
    }

    public abstract void open(String path);

    public abstract void close();

    public abstract void removeAllIcons();

    public abstract void requestIconForPageUrl(String url, IconListener listener);

    public abstract void retainIconForPageUrl(String url);

    public abstract void releaseIconForPageUrl(String url);

    public static synchronized WebIconDatabase getInstance() {
        if (sInstance == null) sInstance = new Impl();
        return sInstance;
    }

    private static final class Impl extends WebIconDatabase {
        @Override public void open(String path) { }
        @Override public void close() { }
        @Override public void removeAllIcons() { }
        @Override public void requestIconForPageUrl(String url, IconListener listener) { }
        @Override public void retainIconForPageUrl(String url) { }
        @Override public void releaseIconForPageUrl(String url) { }
    }
}
