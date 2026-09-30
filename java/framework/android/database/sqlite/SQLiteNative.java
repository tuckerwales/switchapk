package android.database.sqlite;

/** Thin natives over the bundled SQLite library. Handles are native pointers. */
final class SQLiteNative {
    static final int SQLITE_ROW = 100;
    static final int SQLITE_DONE = 101;

    static native long nOpen(String path, int flags);
    static native void nClose(long db);
    static native long nPrepare(long db, String sql);
    static native void nFinalize(long stmt);
    static native void nBindNull(long stmt, int index);
    static native void nBindLong(long stmt, int index, long value);
    static native void nBindDouble(long stmt, int index, double value);
    static native void nBindString(long stmt, int index, String value);
    static native void nBindBlob(long stmt, int index, byte[] value);
    static native void nReset(long stmt);
    static native void nClearBindings(long stmt);
    static native int nStep(long stmt);
    static native int nBindParameterCount(long stmt);
    static native int nColumnCount(long stmt);
    static native String nColumnName(long stmt, int i);
    static native int nColumnType(long stmt, int i);
    static native long nColumnLong(long stmt, int i);
    static native double nColumnDouble(long stmt, int i);
    static native String nColumnText(long stmt, int i);
    static native byte[] nColumnBlob(long stmt, int i);
    static native long nLastInsertRowId(long db);
    static native int nChanges(long db);
    static native boolean nIsReadOnly(long stmt);
}
