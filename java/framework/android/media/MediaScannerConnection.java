package android.media;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.File;

/**
 * The media scanner client. There is no scanner service: a scan of an existing image, video
 * or audio file inserts it into MediaStore (as the scanner would) and reports its content
 * Uri; any other file, or a missing one, reports a null Uri. Callbacks run on a background
 * thread, as AOSP delivers them on a binder thread.
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
            if (mConnected) return;
            mConnected = true;
        }
        if (mClient != null) {
            new Thread(new Runnable() {
                public void run() { mClient.onMediaScannerConnected(); }
            }, "MediaScannerConnection").start();
        }
    }

    public void disconnect() {
        synchronized (this) {
            mConnected = false;
        }
    }

    public synchronized boolean isConnected() { return mConnected; }

    public void scanFile(final String path, final String mimeType) {
        synchronized (this) {
            if (!mConnected) throw new IllegalStateException("not connected to MediaScannerService");
        }
        new Thread(new Runnable() {
            public void run() {
                Uri uri = scan(mContext, path, mimeType);
                if (mClient != null) mClient.onScanCompleted(path, uri);
            }
        }, "MediaScannerConnection").start();
    }

    public static void scanFile(final Context context, final String[] paths, final String[] mimeTypes,
            final OnScanCompletedListener callback) {
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < paths.length; i++) {
                    String mime = mimeTypes != null && i < mimeTypes.length ? mimeTypes[i] : null;
                    Uri uri = scan(context, paths[i], mime);
                    if (callback != null) callback.onScanCompleted(paths[i], uri);
                }
            }
        }, "MediaScannerConnection").start();
    }

    public void onServiceConnected(ComponentName className, IBinder service) {}

    public void onServiceDisconnected(ComponentName className) {}

    private static Uri scan(Context context, String path, String mimeType) {
        if (path == null || context == null) return null;
        File file = new File(path);
        if (!file.isFile()) return null;
        String mime = mimeType;
        if (mime == null) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot >= 0) mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
        }
        if (mime == null) return null;
        Uri table;
        if (mime.startsWith("image/")) table = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        else if (mime.startsWith("video/")) table = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        else if (mime.startsWith("audio/")) table = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        else return null;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, file.getPath());
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.SIZE, file.length());
        try {
            ContentResolver cr = context.getContentResolver();
            return cr.insert(table, values);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
