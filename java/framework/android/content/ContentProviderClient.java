package android.content;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class ContentProviderClient implements AutoCloseable {
    private final ContentProvider mProvider;

    ContentProviderClient(ContentProvider provider) { mProvider = provider; }

    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return mProvider.query(url, projection, selection, selectionArgs, sortOrder); }
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) { return mProvider.query(url, projection, selection, selectionArgs, sortOrder, cancellationSignal); }
    public String getType(Uri url) { return mProvider.getType(url); }
    public Uri insert(Uri url, ContentValues initialValues) { return mProvider.insert(url, initialValues); }
    public int bulkInsert(Uri url, ContentValues[] initialValues) { return mProvider.bulkInsert(url, initialValues); }
    public int delete(Uri url, String selection, String[] selectionArgs) { return mProvider.delete(url, selection, selectionArgs); }
    public int update(Uri url, ContentValues values, String selection, String[] selectionArgs) { return mProvider.update(url, values, selection, selectionArgs); }
    public ParcelFileDescriptor openFile(Uri url, String mode) throws FileNotFoundException { return mProvider.openFile(url, mode); }
    public AssetFileDescriptor openAssetFile(Uri url, String mode) throws FileNotFoundException { return mProvider.openAssetFile(url, mode); }
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException { return mProvider.applyBatch(operations); }
    public Bundle call(String method, String arg, Bundle extras) { return mProvider.call(method, arg, extras); }
    public ContentProvider getLocalContentProvider() { return mProvider; }
    public boolean release() { return true; }
    public void close() {}
}
