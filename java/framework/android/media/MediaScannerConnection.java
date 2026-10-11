package android.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;

/**
 * No media scanner runs here: connecting succeeds at once and every scan completes with a null Uri,
 * which is what Android reports when a file could not be scanned. Callbacks come on a background
 * thread, as on Android (a binder thread there).
 */
public class MediaScannerConnection implements ServiceConnection {
    public interface OnScanCompletedListener {
        void onScanCompleted(String path, Uri uri);
    }

    public interface MediaScannerConnectionClient extends OnScanCompletedListener {
        void onMediaScannerConnected();
    }

    private final Context mContext;
    private final MediaScannerConnectionClient mClient;
    private boolean mConnected;

    public MediaScannerConnection(Context context, MediaScannerConnectionClient client) {
        mContext = context;
        mClient = client;
    }

    public void connect() {
        synchronized (this) {
            if (mConnected) {
                return;
            }
            mConnected = true;
        }
        if (mClient != null) {
            post(new Runnable() {
                public void run() {
                    mClient.onMediaScannerConnected();
                }
            });
        }
    }

    public void disconnect() {
        synchronized (this) {
            mConnected = false;
        }
    }

    public synchronized boolean isConnected() {
        return mConnected;
    }

    public void scanFile(final String path, String mimeType) {
        synchronized (this) {
            if (!mConnected) {
                throw new IllegalStateException("not connected to MediaScannerService");
            }
        }
        if (mClient != null) {
            post(new Runnable() {
                public void run() {
                    mClient.onScanCompleted(path, null);
                }
            });
        }
    }

    public static void scanFile(Context context, final String[] paths, String[] mimeTypes,
            final OnScanCompletedListener callback) {
        if (callback == null || paths == null) {
            return;
        }
        post(new Runnable() {
            public void run() {
                for (String path : paths) {
                    callback.onScanCompleted(path, null);
                }
            }
        });
    }

    private static void post(Runnable r) {
        Thread t = new Thread(r, "MediaScannerConnection");
        t.setDaemon(true);
        t.start();
    }

    public void onServiceConnected(ComponentName className, IBinder service) {
    }

    public void onServiceDisconnected(ComponentName className) {
    }
}
