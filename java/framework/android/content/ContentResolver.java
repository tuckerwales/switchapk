package android.content;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Resolves content: URIs against the application's own providers (declared in
 * its manifest and installed by ActivityThread), plus file: and
 * android.resource: URIs.
 */
public abstract class ContentResolver {
    public static final String SCHEME_CONTENT = "content";
    public static final String SCHEME_ANDROID_RESOURCE = "android.resource";
    public static final String SCHEME_FILE = "file";
    public static final String CURSOR_ITEM_BASE_TYPE = "vnd.android.cursor.item";
    public static final String CURSOR_DIR_BASE_TYPE = "vnd.android.cursor.dir";
    public static final String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
    public static final String SYNC_EXTRAS_MANUAL = "force";
    public static final String SYNC_EXTRAS_EXPEDITED = "expedited";
    public static final String QUERY_ARG_SQL_SELECTION = "android:query-arg-sql-selection";
    public static final String QUERY_ARG_SQL_SELECTION_ARGS = "android:query-arg-sql-selection-args";
    public static final String QUERY_ARG_SQL_SORT_ORDER = "android:query-arg-sql-sort-order";
    public static final String QUERY_ARG_LIMIT = "android:query-arg-limit";
    public static final String QUERY_ARG_OFFSET = "android:query-arg-offset";
    public static final int NOTIFY_SYNC_TO_NETWORK = 1 << 0;
    public static final int NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS = 1 << 1;
    public static final int NOTIFY_INSERT = 1 << 2;
    public static final int NOTIFY_UPDATE = 1 << 3;
    public static final int NOTIFY_DELETE = 1 << 4;
    public static final int SYNC_OBSERVER_TYPE_SETTINGS = 1 << 0;

    private static final HashMap<String, ContentProvider> sProviders = new HashMap<String, ContentProvider>();
    private static final ArrayList<Object[]> sObservers = new ArrayList<Object[]>();

    private final Context mContext;

    public ContentResolver(Context context) { mContext = context; }

    /** Registers an in-process provider for one or more ';'-separated authorities. */
    public static void installProvider(String authorities, ContentProvider provider) {
        synchronized (sProviders) {
            for (String a : authorities.split(";")) if (!a.isEmpty()) sProviders.put(a.trim(), provider);
        }
    }

    static ContentProvider findProvider(Uri uri) {
        String auth = uri.getAuthority();
        if (auth == null) return null;
        synchronized (sProviders) { return sProviders.get(auth); }
    }

    public final ContentProviderClient acquireContentProviderClient(Uri uri) {
        ContentProvider p = findProvider(uri);
        return p != null ? new ContentProviderClient(p) : null;
    }

    public final ContentProviderClient acquireContentProviderClient(String name) {
        ContentProvider p;
        synchronized (sProviders) { p = sProviders.get(name); }
        return p != null ? new ContentProviderClient(p) : null;
    }

    public final ContentProviderClient acquireUnstableContentProviderClient(Uri uri) { return acquireContentProviderClient(uri); }
    public final ContentProviderClient acquireUnstableContentProviderClient(String name) { return acquireContentProviderClient(name); }

    public final String getType(Uri url) {
        ContentProvider p = findProvider(url);
        if (p != null) return p.getType(url);
        if (SCHEME_FILE.equals(url.getScheme())) return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(url.toString()));
        return null;
    }

    public String[] getStreamTypes(Uri url, String mimeTypeFilter) {
        ContentProvider p = findProvider(url);
        return p != null ? p.getStreamTypes(url, mimeTypeFilter) : null;
    }

    public final Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    public final Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        ContentProvider p = findProvider(uri);
        if (p == null) {
            Log.w("ContentResolver", "no provider for " + uri);
            return null;
        }
        return p.query(uri, projection, selection, selectionArgs, sortOrder, cancellationSignal);
    }

    public final Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
        ContentProvider p = findProvider(uri);
        return p != null ? p.query(uri, projection, queryArgs, cancellationSignal) : null;
    }

    public final Uri canonicalize(Uri url) { ContentProvider p = findProvider(url); return p != null ? p.canonicalize(url) : null; }
    public final Uri uncanonicalize(Uri url) { ContentProvider p = findProvider(url); return p != null ? p.uncanonicalize(url) : url; }
    public final boolean refresh(Uri url, Bundle args, CancellationSignal cancellationSignal) { return false; }

    public final InputStream openInputStream(Uri uri) throws FileNotFoundException {
        String scheme = uri.getScheme();
        if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
            OpenResourceIdResult r = getResourceId(uri);
            try {
                return r.r.openRawResource(r.id);
            } catch (Resources.NotFoundException ex) {
                throw new FileNotFoundException("Resource does not exist: " + uri);
            }
        } else if (SCHEME_FILE.equals(scheme) || scheme == null) {
            return new FileInputStream(uri.getPath());
        } else {
            AssetFileDescriptor fd = openAssetFileDescriptor(uri, "r", null);
            try {
                return fd != null ? fd.createInputStream() : null;
            } catch (java.io.IOException e) {
                throw new FileNotFoundException("Unable to create stream");
            }
        }
    }

    public final OutputStream openOutputStream(Uri uri) throws FileNotFoundException { return openOutputStream(uri, "w"); }

    public final OutputStream openOutputStream(Uri uri, String mode) throws FileNotFoundException {
        if (SCHEME_FILE.equals(uri.getScheme()) || uri.getScheme() == null) {
            File f = new File(uri.getPath());
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            return new FileOutputStream(f, mode.contains("a"));
        }
        ContentProvider p = findProvider(uri);
        if (p == null) throw new FileNotFoundException("No content provider: " + uri);
        ParcelFileDescriptor pfd = p.openFile(uri, mode);
        if (pfd == null) throw new FileNotFoundException(uri.toString());
        return new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
    }

    public final ParcelFileDescriptor openFileDescriptor(Uri uri, String mode) throws FileNotFoundException { return openFileDescriptor(uri, mode, null); }

    public final ParcelFileDescriptor openFileDescriptor(Uri uri, String mode, CancellationSignal cancellationSignal) throws FileNotFoundException {
        if (SCHEME_FILE.equals(uri.getScheme()) || uri.getScheme() == null) {
            return ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.parseMode(mode));
        }
        ContentProvider p = findProvider(uri);
        if (p == null) throw new FileNotFoundException("No content provider: " + uri);
        return p.openFile(uri, mode, cancellationSignal);
    }

    public final AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode) throws FileNotFoundException { return openAssetFileDescriptor(uri, mode, null); }

    public final AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode, CancellationSignal cancellationSignal) throws FileNotFoundException {
        String scheme = uri.getScheme();
        if (SCHEME_ANDROID_RESOURCE.equals(scheme)) {
            OpenResourceIdResult r = getResourceId(uri);
            try {
                return r.r.openRawResourceFd(r.id);
            } catch (Resources.NotFoundException ex) {
                throw new FileNotFoundException("Resource does not exist: " + uri);
            }
        }
        if (SCHEME_FILE.equals(scheme)) {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.parseMode(mode));
            return new AssetFileDescriptor(pfd, 0, -1);
        }
        ContentProvider p = findProvider(uri);
        if (p == null) throw new FileNotFoundException("No content provider: " + uri);
        return p.openAssetFile(uri, mode, cancellationSignal);
    }

    public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts) throws FileNotFoundException {
        return openAssetFileDescriptor(uri, "r");
    }

    public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts, CancellationSignal cancellationSignal) throws FileNotFoundException {
        return openAssetFileDescriptor(uri, "r");
    }

    public class OpenResourceIdResult {
        public Resources r;
        public int id;
    }

    public OpenResourceIdResult getResourceId(Uri uri) throws FileNotFoundException {
        String authority = uri.getAuthority();
        Resources r = mContext.getResources();
        if (authority == null || authority.isEmpty()) throw new FileNotFoundException("No authority: " + uri);
        List<String> path = uri.getPathSegments();
        if (path == null) throw new FileNotFoundException("No path: " + uri);
        int len = path.size();
        int id;
        if (len == 1) {
            try {
                id = Integer.parseInt(path.get(0));
            } catch (NumberFormatException e) {
                throw new FileNotFoundException("Single path segment is not a resource ID: " + uri);
            }
        } else if (len == 2) {
            id = r.getIdentifier(path.get(1), path.get(0), authority);
        } else {
            throw new FileNotFoundException("More than two path segments: " + uri);
        }
        if (id == 0) throw new FileNotFoundException("No resource found for: " + uri);
        OpenResourceIdResult res = new OpenResourceIdResult();
        res.r = r;
        res.id = id;
        return res;
    }

    public final Uri insert(Uri url, ContentValues values) {
        ContentProvider p = findProvider(url);
        if (p == null) return null;
        Uri r = p.insert(url, values);
        return r;
    }

    public final Uri insert(Uri url, ContentValues values, Bundle extras) { ContentProvider p = findProvider(url); return p != null ? p.insert(url, values, extras) : null; }

    public ContentProviderResult[] applyBatch(String authority, ArrayList<ContentProviderOperation> operations) throws android.os.RemoteException, OperationApplicationException {
        ContentProvider p;
        synchronized (sProviders) { p = sProviders.get(authority); }
        if (p == null) throw new IllegalArgumentException("Unknown authority " + authority);
        return p.applyBatch(authority, operations);
    }

    public final int bulkInsert(Uri url, ContentValues[] values) { ContentProvider p = findProvider(url); return p != null ? p.bulkInsert(url, values) : 0; }
    public final int delete(Uri url, String where, String[] selectionArgs) { ContentProvider p = findProvider(url); return p != null ? p.delete(url, where, selectionArgs) : 0; }
    public final int delete(Uri url, Bundle extras) { ContentProvider p = findProvider(url); return p != null ? p.delete(url, extras) : 0; }
    public final int update(Uri uri, ContentValues values, String where, String[] selectionArgs) { ContentProvider p = findProvider(uri); return p != null ? p.update(uri, values, where, selectionArgs) : 0; }
    public final int update(Uri uri, ContentValues values, Bundle extras) { ContentProvider p = findProvider(uri); return p != null ? p.update(uri, values, extras) : 0; }
    public final Bundle call(Uri uri, String method, String arg, Bundle extras) { ContentProvider p = findProvider(uri); return p != null ? p.call(uri.getAuthority(), method, arg, extras) : null; }
    public final Bundle call(String authority, String method, String arg, Bundle extras) {
        ContentProvider p;
        synchronized (sProviders) { p = sProviders.get(authority); }
        return p != null ? p.call(authority, method, arg, extras) : null;
    }

    public final void registerContentObserver(Uri uri, boolean notifyForDescendants, ContentObserver observer) {
        synchronized (sObservers) { sObservers.add(new Object[] {uri, notifyForDescendants, observer}); }
    }

    public final void unregisterContentObserver(ContentObserver observer) {
        synchronized (sObservers) {
            for (int i = sObservers.size() - 1; i >= 0; i--) if (sObservers.get(i)[2] == observer) sObservers.remove(i);
        }
    }

    public void notifyChange(Uri uri, ContentObserver observer) { notifyChange(uri, observer, true); }
    public void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) { notifyChange(uri, observer, 0); }
    public void notifyChange(java.util.Collection<Uri> uris, ContentObserver observer, int flags) { for (Uri u : uris) notifyChange(u, observer, flags); }

    public void notifyChange(Uri uri, ContentObserver observer, int flags) {
        ArrayList<Object[]> snapshot;
        synchronized (sObservers) { snapshot = new ArrayList<Object[]>(sObservers); }
        String s = uri.toString();
        for (Object[] o : snapshot) {
            String target = o[0].toString();
            boolean desc = (Boolean) o[1];
            ContentObserver obs = (ContentObserver) o[2];
            if (obs == observer && !obs.deliverSelfNotifications()) continue;
            if (s.equals(target) || (desc && s.startsWith(target)) || target.startsWith(s)) obs.dispatchChange(obs == observer, uri);
        }
    }

    public void takePersistableUriPermission(Uri uri, int modeFlags) {}
    public void releasePersistableUriPermission(Uri uri, int modeFlags) {}
    public List<android.content.UriPermission> getPersistedUriPermissions() { return new ArrayList<android.content.UriPermission>(); }
    public List<android.content.UriPermission> getOutgoingPersistedUriPermissions() { return new ArrayList<android.content.UriPermission>(); }
    public static void requestSync(android.accounts.Account account, String authority, Bundle extras) {}
    public static void setSyncAutomatically(android.accounts.Account account, String authority, boolean sync) {}
    public static boolean getSyncAutomatically(android.accounts.Account account, String authority) { return false; }
    public static boolean getMasterSyncAutomatically() { return false; }
    public static void setMasterSyncAutomatically(boolean sync) {}
    public static void addPeriodicSync(android.accounts.Account account, String authority, Bundle extras, long pollFrequency) {}
    public static void removePeriodicSync(android.accounts.Account account, String authority, Bundle extras) {}
    public static void setIsSyncable(android.accounts.Account account, String authority, int syncable) {}
    public static int getIsSyncable(android.accounts.Account account, String authority) { return 0; }
    public static void cancelSync(android.accounts.Account account, String authority) {}
    public static ContentResolver wrap(ContentProvider wrapped) { return new ContentResolver(wrapped.getContext()) {}; }
}
