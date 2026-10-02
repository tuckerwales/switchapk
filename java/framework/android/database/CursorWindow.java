package android.database;

import android.database.sqlite.SQLiteClosable;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;

/**
 * An in-memory page of cursor rows. Row numbers passed to get and put are
 * absolute positions; the window holds the range that starts at
 * {@link #getStartPosition()}. A value that does not fit in the byte budget
 * is refused so {@code fillWindow} stops, as the native window does.
 */
public class CursorWindow extends SQLiteClosable implements Parcelable {
    /** Default budget, matching the platform cursor window. */
    private static final long DEFAULT_SIZE = 2L * 1024L * 1024L;
    private static final int ROW_OVERHEAD = 8;

    private final String mName;
    private final long mWindowSizeBytes;
    private int mStartPos;
    private int mNumColumns;
    private long mUsed;
    private final ArrayList<Object[]> mRows = new ArrayList<Object[]>();

    public CursorWindow(String name) { this(name, DEFAULT_SIZE); }

    public CursorWindow(String name, long windowSizeBytes) {
        if (windowSizeBytes <= 0) throw new IllegalArgumentException("windowSizeBytes must be positive");
        mName = name;
        mWindowSizeBytes = windowSizeBytes;
    }

    /** @deprecated local and remote windows are the same here. */
    @Deprecated
    public CursorWindow(boolean localWindow) { this((String) null); }

    @Override
    protected void onAllReferencesReleased() {
        mRows.clear();
        mNumColumns = 0;
        mUsed = 0;
    }

    public void clear() {
        acquireReference();
        try {
            mRows.clear();
            mNumColumns = 0;
            mUsed = 0;
        } finally {
            releaseReference();
        }
    }

    public int getStartPosition() { return mStartPos; }

    public void setStartPosition(int pos) { mStartPos = pos; }

    public int getNumRows() {
        acquireReference();
        try {
            return mRows.size();
        } finally {
            releaseReference();
        }
    }

    public boolean setNumColumns(int columnNum) {
        acquireReference();
        try {
            if (mNumColumns > 0) return mNumColumns == columnNum;
            if (columnNum < 0) return false;
            mNumColumns = columnNum;
            return true;
        } finally {
            releaseReference();
        }
    }

    public boolean allocRow() {
        acquireReference();
        try {
            if (mNumColumns <= 0) return false;
            if (mUsed + ROW_OVERHEAD > mWindowSizeBytes) return false;
            mRows.add(new Object[mNumColumns]);
            mUsed += ROW_OVERHEAD;
            return true;
        } finally {
            releaseReference();
        }
    }

    public void freeLastRow() {
        acquireReference();
        try {
            if (mRows.isEmpty()) return;
            Object[] row = mRows.remove(mRows.size() - 1);
            mUsed -= ROW_OVERHEAD;
            for (int i = 0; i < row.length; i++) mUsed -= sizeOf(row[i]);
            if (mUsed < 0) mUsed = 0;
        } finally {
            releaseReference();
        }
    }

    public int getType(int row, int column) {
        acquireReference();
        try {
            return typeOf(cell(row, column));
        } finally {
            releaseReference();
        }
    }

    public boolean isNull(int row, int column) { return getType(row, column) == Cursor.FIELD_TYPE_NULL; }
    public boolean isBlob(int row, int column) { return getType(row, column) == Cursor.FIELD_TYPE_BLOB; }
    public boolean isLong(int row, int column) { return getType(row, column) == Cursor.FIELD_TYPE_INTEGER; }
    public boolean isFloat(int row, int column) { return getType(row, column) == Cursor.FIELD_TYPE_FLOAT; }
    public boolean isString(int row, int column) { return getType(row, column) == Cursor.FIELD_TYPE_STRING; }

    public byte[] getBlob(int row, int column) {
        acquireReference();
        try {
            Object v = cell(row, column);
            if (v == null) return null;
            if (v instanceof byte[]) return (byte[]) v;
            return v.toString().getBytes();
        } finally {
            releaseReference();
        }
    }

    public String getString(int row, int column) {
        acquireReference();
        try {
            Object v = cell(row, column);
            return v == null ? null : v.toString();
        } finally {
            releaseReference();
        }
    }

    public void copyStringToBuffer(int row, int column, CharArrayBuffer buffer) {
        String result = getString(row, column);
        if (result != null) {
            char[] data = buffer.data;
            if (data == null || data.length < result.length()) buffer.data = result.toCharArray();
            else result.getChars(0, result.length(), data, 0);
            buffer.sizeCopied = result.length();
        } else {
            buffer.sizeCopied = 0;
        }
    }

    public long getLong(int row, int column) {
        acquireReference();
        try {
            Object v = cell(row, column);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v == null) return 0;
            try {
                return (long) Double.parseDouble(v.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        } finally {
            releaseReference();
        }
    }

    public double getDouble(int row, int column) {
        acquireReference();
        try {
            Object v = cell(row, column);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v == null) return 0;
            try {
                return Double.parseDouble(v.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        } finally {
            releaseReference();
        }
    }

    public short getShort(int row, int column) { return (short) getLong(row, column); }
    public int getInt(int row, int column) { return (int) getLong(row, column); }
    public float getFloat(int row, int column) { return (float) getDouble(row, column); }

    public boolean putBlob(byte[] value, int row, int column) {
        if (value == null) return putNull(row, column);
        return put(row, column, value);
    }

    public boolean putString(String value, int row, int column) {
        if (value == null) return putNull(row, column);
        return put(row, column, value);
    }

    public boolean putLong(long value, int row, int column) { return put(row, column, Long.valueOf(value)); }

    public boolean putDouble(double value, int row, int column) { return put(row, column, Double.valueOf(value)); }

    public boolean putNull(int row, int column) { return put(row, column, null); }

    private boolean put(int row, int column, Object value) {
        acquireReference();
        try {
            int index = row - mStartPos;
            if (index < 0 || index >= mRows.size() || column < 0 || column >= mNumColumns) return false;
            Object[] cells = mRows.get(index);
            long next = mUsed - sizeOf(cells[column]) + sizeOf(value);
            if (next > mWindowSizeBytes) return false;
            cells[column] = value;
            mUsed = next;
            return true;
        } finally {
            releaseReference();
        }
    }

    private Object cell(int row, int column) {
        int index = row - mStartPos;
        if (index < 0 || index >= mRows.size() || column < 0 || column >= mNumColumns) return null;
        return mRows.get(index)[column];
    }

    private static int typeOf(Object v) {
        if (v == null) return Cursor.FIELD_TYPE_NULL;
        if (v instanceof byte[]) return Cursor.FIELD_TYPE_BLOB;
        if (v instanceof Double) return Cursor.FIELD_TYPE_FLOAT;
        if (v instanceof Long) return Cursor.FIELD_TYPE_INTEGER;
        return Cursor.FIELD_TYPE_STRING;
    }

    private static long sizeOf(Object v) {
        if (v == null) return 0;
        if (v instanceof String) return ((String) v).length() * 2L;
        if (v instanceof byte[]) return ((byte[]) v).length;
        return 8;
    }

    @Override
    public String toString() {
        return (mName == null ? "<unnamed>" : mName) + " {" + mRows.size() + "x" + mNumColumns + "}";
    }

    public static CursorWindow newFromParcel(Parcel p) { return CREATOR.createFromParcel(p); }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        acquireReference();
        try {
            dest.writeString(mName);
            dest.writeLong(mWindowSizeBytes);
            dest.writeInt(mStartPos);
            dest.writeInt(mNumColumns);
            dest.writeInt(mRows.size());
            for (int r = 0; r < mRows.size(); r++) {
                Object[] row = mRows.get(r);
                for (int c = 0; c < mNumColumns; c++) {
                    Object v = row[c];
                    int type = typeOf(v);
                    dest.writeInt(type);
                    if (type == Cursor.FIELD_TYPE_INTEGER) dest.writeLong(((Long) v).longValue());
                    else if (type == Cursor.FIELD_TYPE_FLOAT) dest.writeDouble(((Double) v).doubleValue());
                    else if (type == Cursor.FIELD_TYPE_STRING) dest.writeString((String) v);
                    else if (type == Cursor.FIELD_TYPE_BLOB) dest.writeByteArray((byte[]) v);
                }
            }
        } finally {
            releaseReference();
        }
    }

    private CursorWindow(Parcel p) {
        mName = p.readString();
        long size = p.readLong();
        mWindowSizeBytes = size > 0 ? size : DEFAULT_SIZE;
        mStartPos = p.readInt();
        mNumColumns = p.readInt();
        int rows = p.readInt();
        for (int r = 0; r < rows; r++) {
            Object[] row = new Object[Math.max(mNumColumns, 0)];
            for (int c = 0; c < mNumColumns; c++) {
                int type = p.readInt();
                if (type == Cursor.FIELD_TYPE_INTEGER) row[c] = Long.valueOf(p.readLong());
                else if (type == Cursor.FIELD_TYPE_FLOAT) row[c] = Double.valueOf(p.readDouble());
                else if (type == Cursor.FIELD_TYPE_STRING) row[c] = p.readString();
                else if (type == Cursor.FIELD_TYPE_BLOB) row[c] = p.createByteArray();
            }
            mRows.add(row);
            mUsed += ROW_OVERHEAD;
            for (int c = 0; c < row.length; c++) mUsed += sizeOf(row[c]);
        }
    }

    public static final Parcelable.Creator<CursorWindow> CREATOR = new Parcelable.Creator<CursorWindow>() {
        public CursorWindow createFromParcel(Parcel source) { return new CursorWindow(source); }
        public CursorWindow[] newArray(int size) { return new CursorWindow[size]; }
    };
}
