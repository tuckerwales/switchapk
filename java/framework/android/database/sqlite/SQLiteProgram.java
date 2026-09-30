package android.database.sqlite;

import java.util.ArrayList;

public abstract class SQLiteProgram extends SQLiteClosable {
    final SQLiteDatabase mDatabase;
    final String mSql;
    private final ArrayList<Object> mBindArgs = new ArrayList<Object>();

    SQLiteProgram(SQLiteDatabase db, String sql, Object[] bindArgs) {
        mDatabase = db;
        mSql = sql.trim();
        if (bindArgs != null) for (int i = 0; i < bindArgs.length; i++) bind(i + 1, bindArgs[i]);
    }

    final SQLiteDatabase getDatabase() { return mDatabase; }
    final String getSql() { return mSql; }
    final Object[] getBindArgs() { return mBindArgs.toArray(); }

    @Deprecated
    public final int getUniqueId() { return -1; }

    private void bind(int index, Object value) {
        while (mBindArgs.size() < index) mBindArgs.add(null);
        mBindArgs.set(index - 1, value);
    }

    public void bindNull(int index) { bind(index, null); }
    public void bindLong(int index, long value) { bind(index, value); }
    public void bindDouble(int index, double value) { bind(index, value); }

    public void bindString(int index, String value) {
        if (value == null) throw new IllegalArgumentException("the bind value at index " + index + " is null");
        bind(index, value);
    }

    public void bindBlob(int index, byte[] value) {
        if (value == null) throw new IllegalArgumentException("the bind value at index " + index + " is null");
        bind(index, value);
    }

    public void clearBindings() { mBindArgs.clear(); }

    public void bindAllArgsAsStrings(String[] bindArgs) {
        if (bindArgs != null) for (int i = bindArgs.length; i != 0; i--) bindString(i, bindArgs[i - 1]);
    }

    @Override
    protected void onAllReferencesReleased() { clearBindings(); }
}
