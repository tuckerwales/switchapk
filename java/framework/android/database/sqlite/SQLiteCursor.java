package android.database.sqlite;

import android.database.AbstractCursor;
import android.database.CursorIndexOutOfBoundsException;
import java.util.ArrayList;

/** A cursor over a fully materialised result set. */
public class SQLiteCursor extends AbstractCursor {
    private final String[] mColumns;
    private final ArrayList<Object[]> mRows;
    private final SQLiteQuery mQuery;
    private final SQLiteCursorDriver mDriver;

    public SQLiteCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
        mDriver = driver;
        mQuery = query;
        SQLiteDatabase.Result r = query.getDatabase().runQuery(query.getSql(), query.getBindArgs());
        mColumns = r.columns;
        mRows = r.rows;
    }

    @Deprecated
    public SQLiteCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) { this(driver, editTable, query); }

    SQLiteCursor(String[] columns, ArrayList<Object[]> rows) {
        mDriver = null;
        mQuery = null;
        mColumns = columns;
        mRows = rows;
    }

    public SQLiteDatabase getDatabase() { return mQuery != null ? mQuery.getDatabase() : null; }

    private Object get(int column) {
        if (mPos < 0 || mPos >= mRows.size()) throw new CursorIndexOutOfBoundsException(mPos, mRows.size());
        Object[] row = mRows.get(mPos);
        if (column < 0 || column >= row.length) throw new IllegalStateException("Couldn't read row " + mPos + ", col " + column + " from CursorWindow.");
        return row[column];
    }

    @Override public int getCount() { return mRows.size(); }
    @Override public String[] getColumnNames() { return mColumns; }

    @Override
    public String getString(int column) {
        Object v = get(column);
        if (v == null) return null;
        if (v instanceof byte[]) throw new android.database.sqlite.SQLiteException("unable to convert BLOB to string");
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Double.toString(d);
        }
        return v.toString();
    }

    @Override public short getShort(int column) { return (short) getLong(column); }
    @Override public int getInt(int column) { return (int) getLong(column); }

    @Override
    public long getLong(int column) {
        Object v = get(column);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return (long) Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override public float getFloat(int column) { return (float) getDouble(column); }

    @Override
    public double getDouble(int column) {
        Object v = get(column);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public byte[] getBlob(int column) {
        Object v = get(column);
        if (v == null) return null;
        if (v instanceof byte[]) return (byte[]) v;
        return v.toString().getBytes();
    }

    @Override public boolean isNull(int column) { return get(column) == null; }

    @Override
    public int getType(int column) {
        Object v = get(column);
        if (v == null) return FIELD_TYPE_NULL;
        if (v instanceof Long) return FIELD_TYPE_INTEGER;
        if (v instanceof Double) return FIELD_TYPE_FLOAT;
        if (v instanceof byte[]) return FIELD_TYPE_BLOB;
        return FIELD_TYPE_STRING;
    }

    @Override
    public void close() {
        super.close();
        if (mDriver != null) mDriver.cursorClosed();
    }

    public void setSelectionArguments(String[] selectionArgs) {}
    public void setFillWindowForwardOnly(boolean fillWindowForwardOnly) {}
}
