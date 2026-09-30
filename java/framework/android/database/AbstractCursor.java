package android.database;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

public abstract class AbstractCursor implements CrossProcessCursor {
    protected int mPos = -1;
    protected boolean mClosed;
    @Deprecated protected ContentResolver mContentResolver;
    private Uri mNotifyUri;
    private final DataSetObservable mDataSetObservable = new DataSetObservable();
    private final ContentObservable mContentObservable = new ContentObservable();
    private Bundle mExtras = Bundle.EMPTY;

    public abstract int getCount();
    public abstract String[] getColumnNames();
    public abstract String getString(int column);
    public abstract short getShort(int column);
    public abstract int getInt(int column);
    public abstract long getLong(int column);
    public abstract float getFloat(int column);
    public abstract double getDouble(int column);
    public abstract boolean isNull(int column);

    public int getType(int column) { return FIELD_TYPE_STRING; }
    public byte[] getBlob(int column) { throw new UnsupportedOperationException("getBlob is not supported"); }
    public CursorWindow getWindow() { return null; }
    public int getColumnCount() { return getColumnNames().length; }
    public void deactivate() { mDataSetObservable.notifyInvalidated(); }
    public boolean requery() {
        mDataSetObservable.notifyChanged();
        return true;
    }
    public boolean isClosed() { return mClosed; }
    public void close() {
        mClosed = true;
        mContentObservable.unregisterAll();
        mDataSetObservable.notifyInvalidated();
    }
    public boolean onMove(int oldPosition, int newPosition) { return true; }

    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        String result = getString(columnIndex);
        if (result != null) {
            char[] data = buffer.data;
            if (data == null || data.length < result.length()) buffer.data = result.toCharArray();
            else result.getChars(0, result.length(), data, 0);
            buffer.sizeCopied = result.length();
        } else {
            buffer.sizeCopied = 0;
        }
    }

    public AbstractCursor() {}

    public final int getPosition() { return mPos; }

    public final boolean moveToPosition(int position) {
        final int count = getCount();
        if (position >= count) {
            mPos = count;
            return false;
        }
        if (position < 0) {
            mPos = -1;
            return false;
        }
        if (position == mPos) return true;
        boolean result = onMove(mPos, position);
        if (result == false) mPos = -1;
        else mPos = position;
        return result;
    }

    public void fillWindow(int position, CursorWindow window) {}
    public final boolean move(int offset) { return moveToPosition(mPos + offset); }
    public final boolean moveToFirst() { return moveToPosition(0); }
    public final boolean moveToLast() { return moveToPosition(getCount() - 1); }
    public final boolean moveToNext() { return moveToPosition(mPos + 1); }
    public final boolean moveToPrevious() { return moveToPosition(mPos - 1); }
    public final boolean isFirst() { return mPos == 0 && getCount() != 0; }
    public final boolean isLast() { int cnt = getCount(); return mPos == (cnt - 1) && cnt != 0; }
    public final boolean isBeforeFirst() { return getCount() == 0 || mPos == -1; }
    public final boolean isAfterLast() { return getCount() == 0 || mPos == getCount(); }

    public int getColumnIndex(String columnName) {
        final int periodIndex = columnName.lastIndexOf('.');
        if (periodIndex != -1) columnName = columnName.substring(periodIndex + 1);
        String[] columnNames = getColumnNames();
        int length = columnNames.length;
        for (int i = 0; i < length; i++) if (columnNames[i].equalsIgnoreCase(columnName)) return i;
        return -1;
    }

    public int getColumnIndexOrThrow(String columnName) {
        final int index = getColumnIndex(columnName);
        if (index < 0) throw new IllegalArgumentException("column '" + columnName + "' does not exist. Available columns: " + java.util.Arrays.toString(getColumnNames()));
        return index;
    }

    public String getColumnName(int columnIndex) { return getColumnNames()[columnIndex]; }
    public void registerContentObserver(ContentObserver observer) { mContentObservable.registerObserver(observer); }
    public void unregisterContentObserver(ContentObserver observer) { if (!mClosed) mContentObservable.unregisterObserver(observer); }
    public void registerDataSetObserver(DataSetObserver observer) { mDataSetObservable.registerObserver(observer); }
    public void unregisterDataSetObserver(DataSetObserver observer) { mDataSetObservable.unregisterObserver(observer); }
    protected void onChange(boolean selfChange) { mContentObservable.dispatchChange(selfChange, null); }
    public void setNotificationUri(ContentResolver cr, Uri notifyUri) { mContentResolver = cr; mNotifyUri = notifyUri; }
    public Uri getNotificationUri() { return mNotifyUri; }
    public boolean getWantsAllOnMoveCalls() { return false; }
    public void setExtras(Bundle extras) { mExtras = (extras == null) ? Bundle.EMPTY : extras; }
    public Bundle getExtras() { return mExtras; }
    public Bundle respond(Bundle extras) { return Bundle.EMPTY; }

    protected void checkPosition() {
        if (-1 == mPos || getCount() == mPos) throw new CursorIndexOutOfBoundsException(mPos, getCount());
    }
}
