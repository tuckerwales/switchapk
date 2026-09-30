package android.database;

import android.net.Uri;
import android.os.Handler;
import java.util.Collection;

public abstract class ContentObserver {
    Handler mHandler;

    public ContentObserver(Handler handler) { mHandler = handler; }

    public boolean deliverSelfNotifications() { return false; }
    public void onChange(boolean selfChange) {}
    public void onChange(boolean selfChange, Uri uri) { onChange(selfChange); }
    public void onChange(boolean selfChange, Uri uri, int flags) { onChange(selfChange, uri); }
    public void onChange(boolean selfChange, Collection<Uri> uris, int flags) { for (Uri uri : uris) onChange(selfChange, uri, flags); }

    @Deprecated
    public final void dispatchChange(boolean selfChange) { dispatchChange(selfChange, null); }

    public final void dispatchChange(final boolean selfChange, final Uri uri) {
        if (mHandler == null) {
            onChange(selfChange, uri);
        } else {
            mHandler.post(new Runnable() {
                public void run() { onChange(selfChange, uri); }
            });
        }
    }
}
