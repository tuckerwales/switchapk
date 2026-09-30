package android.database;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

public class CursorWrapper implements Cursor {
    protected final Cursor mCursor;

    public CursorWrapper(Cursor cursor) { mCursor = cursor; }

    public Cursor getWrappedCursor() { return mCursor; }
    public void close() { mCursor.close(); }
    public boolean isClosed() { return mCursor.isClosed(); }
    public int getCount() { return mCursor.getCount(); }
    @Deprecated public void deactivate() { mCursor.deactivate(); }
    public boolean moveToFirst() { return mCursor.moveToFirst(); }
    public int getColumnCount() { return mCursor.getColumnCount(); }
    public int getColumnIndex(String columnName) { return mCursor.getColumnIndex(columnName); }
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException { return mCursor.getColumnIndexOrThrow(columnName); }
    public String getColumnName(int columnIndex) { return mCursor.getColumnName(columnIndex); }
    public String[] getColumnNames() { return mCursor.getColumnNames(); }
    public double getDouble(int columnIndex) { return mCursor.getDouble(columnIndex); }
    public void setExtras(Bundle extras) { mCursor.setExtras(extras); }
    public Bundle getExtras() { return mCursor.getExtras(); }
    public float getFloat(int columnIndex) { return mCursor.getFloat(columnIndex); }
    public int getInt(int columnIndex) { return mCursor.getInt(columnIndex); }
    public long getLong(int columnIndex) { return mCursor.getLong(columnIndex); }
    public short getShort(int columnIndex) { return mCursor.getShort(columnIndex); }
    public String getString(int columnIndex) { return mCursor.getString(columnIndex); }
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) { mCursor.copyStringToBuffer(columnIndex, buffer); }
    public byte[] getBlob(int columnIndex) { return mCursor.getBlob(columnIndex); }
    public boolean getWantsAllOnMoveCalls() { return mCursor.getWantsAllOnMoveCalls(); }
    public boolean isAfterLast() { return mCursor.isAfterLast(); }
    public boolean isBeforeFirst() { return mCursor.isBeforeFirst(); }
    public boolean isFirst() { return mCursor.isFirst(); }
    public boolean isLast() { return mCursor.isLast(); }
    public int getType(int columnIndex) { return mCursor.getType(columnIndex); }
    public boolean isNull(int columnIndex) { return mCursor.isNull(columnIndex); }
    public boolean moveToLast() { return mCursor.moveToLast(); }
    public boolean move(int offset) { return mCursor.move(offset); }
    public boolean moveToPosition(int position) { return mCursor.moveToPosition(position); }
    public boolean moveToNext() { return mCursor.moveToNext(); }
    public int getPosition() { return mCursor.getPosition(); }
    public boolean moveToPrevious() { return mCursor.moveToPrevious(); }
    public void registerContentObserver(ContentObserver observer) { mCursor.registerContentObserver(observer); }
    public void registerDataSetObserver(DataSetObserver observer) { mCursor.registerDataSetObserver(observer); }
    @Deprecated public boolean requery() { return mCursor.requery(); }
    public Bundle respond(Bundle extras) { return mCursor.respond(extras); }
    public void setNotificationUri(ContentResolver cr, Uri uri) { mCursor.setNotificationUri(cr, uri); }
    public Uri getNotificationUri() { return mCursor.getNotificationUri(); }
    public void unregisterContentObserver(ContentObserver observer) { mCursor.unregisterContentObserver(observer); }
    public void unregisterDataSetObserver(DataSetObserver observer) { mCursor.unregisterDataSetObserver(observer); }
}
