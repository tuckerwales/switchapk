package android.content;

import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public abstract class ContentProvider implements ComponentCallbacks2 {
    private Context mContext;
    private String mReadPermission;
    private String mWritePermission;
    private String mAuthority;
    private boolean mExported;

    public ContentProvider() {}

    public final Context getContext() { return mContext; }
    public final Context requireContext() {
        if (mContext == null) throw new IllegalStateException("Cannot find context from the provider.");
        return mContext;
    }
    public final String getCallingPackage() { return mContext != null ? mContext.getPackageName() : null; }
    public final String getCallingAttributionTag() { return null; }
    protected final void setReadPermission(String permission) { mReadPermission = permission; }
    public final String getReadPermission() { return mReadPermission; }
    protected final void setWritePermission(String permission) { mWritePermission = permission; }
    public final String getWritePermission() { return mWritePermission; }

    public abstract boolean onCreate();
    public void onConfigurationChanged(Configuration newConfig) {}
    public void onLowMemory() {}
    public void onTrimMemory(int level) {}
    public abstract Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        return query(uri, projection, selection, selectionArgs, sortOrder);
    }
    public Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
        String sel = queryArgs != null ? queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION) : null;
        String[] args = queryArgs != null ? queryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS) : null;
        String order = queryArgs != null ? queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER) : null;
        return query(uri, projection, sel, args, order, cancellationSignal);
    }
    public abstract String getType(Uri uri);
    public Uri canonicalize(Uri url) { return null; }
    public Uri uncanonicalize(Uri url) { return url; }
    public boolean refresh(Uri uri, Bundle args, CancellationSignal cancellationSignal) { return false; }
    public abstract Uri insert(Uri uri, ContentValues values);
    public Uri insert(Uri uri, ContentValues values, Bundle extras) { return insert(uri, values); }
    public int bulkInsert(Uri uri, ContentValues[] values) {
        int numValues = values.length;
        for (int i = 0; i < numValues; i++) insert(uri, values[i]);
        return numValues;
    }
    public abstract int delete(Uri uri, String selection, String[] selectionArgs);
    public int delete(Uri uri, Bundle extras) { return delete(uri, extras != null ? extras.getString(ContentResolver.QUERY_ARG_SQL_SELECTION) : null, extras != null ? extras.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS) : null); }
    public abstract int update(Uri uri, ContentValues values, String selection, String[] selectionArgs);
    public int update(Uri uri, ContentValues values, Bundle extras) { return update(uri, values, extras != null ? extras.getString(ContentResolver.QUERY_ARG_SQL_SELECTION) : null, extras != null ? extras.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS) : null); }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { throw new FileNotFoundException("No files supported by provider at " + uri); }
    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException { return openFile(uri, mode); }
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        ParcelFileDescriptor fd = openFile(uri, mode);
        return fd != null ? new AssetFileDescriptor(fd, 0, -1) : null;
    }
    public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException { return openAssetFile(uri, mode); }
    protected final ParcelFileDescriptor openFileHelper(Uri uri, String mode) throws FileNotFoundException {
        throw new FileNotFoundException("openFileHelper not supported: " + uri);
    }
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) { return null; }
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts) throws FileNotFoundException { return openAssetFile(uri, "r"); }
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts, CancellationSignal signal) throws FileNotFoundException { return openTypedAssetFile(uri, mimeTypeFilter, opts); }
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        return new ContentProviderResult[0];
    }
    public ContentProviderResult[] applyBatch(String authority, ArrayList<ContentProviderOperation> operations) throws OperationApplicationException { return applyBatch(operations); }
    public Bundle call(String method, String arg, Bundle extras) { return null; }
    public Bundle call(String authority, String method, String arg, Bundle extras) { return call(method, arg, extras); }
    public void shutdown() {}
    public void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {}

    public void attachInfo(Context context, ProviderInfo info) {
        if (mContext == null) {
            mContext = context;
            if (info != null) {
                setReadPermission(info.readPermission);
                setWritePermission(info.writePermission);
                mAuthority = info.authority;
                mExported = info.exported;
            }
            ContentProvider.this.onCreate();
        }
    }

    public final void restoreCallingIdentity(Object identity) {}
    public final Object clearCallingIdentity() { return null; }

    public interface PipeDataWriter<T> {
        void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType, Bundle opts, T args);
    }
}
