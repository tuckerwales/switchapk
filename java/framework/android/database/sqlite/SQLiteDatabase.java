package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.DefaultDatabaseErrorHandler;
import android.database.SQLException;
import android.os.CancellationSignal;
import android.util.Pair;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SQLiteDatabase extends SQLiteClosable {
    public static final int CONFLICT_ROLLBACK = 1;
    public static final int CONFLICT_ABORT = 2;
    public static final int CONFLICT_FAIL = 3;
    public static final int CONFLICT_IGNORE = 4;
    public static final int CONFLICT_REPLACE = 5;
    public static final int CONFLICT_NONE = 0;
    private static final String[] CONFLICT_VALUES = new String[] {"", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE "};
    public static final int SQLITE_MAX_LIKE_PATTERN_LENGTH = 50000;
    public static final int OPEN_READWRITE = 0x00000000;
    public static final int OPEN_READONLY = 0x00000001;
    public static final int NO_LOCALIZED_COLLATORS = 0x00000010;
    public static final int CREATE_IF_NECESSARY = 0x10000000;
    public static final int ENABLE_WRITE_AHEAD_LOGGING = 0x20000000;
    public static final int MAX_SQL_CACHE_SIZE = 100;

    static final int EXEC_PLAIN = 0;
    static final int EXEC_CHANGES = 1;
    static final int EXEC_INSERT = 2;

    private long mHandle;
    private final String mPath;
    private final int mFlags;
    private final CursorFactory mFactory;
    private final DatabaseErrorHandler mErrorHandler;
    private int mTransactionDepth;
    private boolean mTransactionSuccessful;
    private boolean mTransactionFailed;
    private final Object mLock = new Object();

    public interface CursorFactory {
        Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable, SQLiteQuery query);
    }

    public interface CustomFunction {
        void callback(String[] args);
    }

    private SQLiteDatabase(String path, int flags, CursorFactory factory, DatabaseErrorHandler errorHandler) {
        mPath = path;
        mFlags = flags;
        mFactory = factory;
        mErrorHandler = errorHandler != null ? errorHandler : new DefaultDatabaseErrorHandler();
    }

    private void open() {
        String p = mPath;
        if (!":memory:".equals(p)) {
            File parent = new File(p).getParentFile();
            if (parent != null && !parent.exists() && (mFlags & CREATE_IF_NECESSARY) != 0) parent.mkdirs();
        }
        mHandle = SQLiteNative.nOpen(p, mFlags);
    }

    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags) { return openDatabase(path, factory, flags, null); }

    public static SQLiteDatabase openDatabase(File path, OpenParams openParams) {
        return openDatabase(path.getPath(), openParams.getCursorFactory(), openParams.getOpenFlags(), openParams.getErrorHandler());
    }

    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags, DatabaseErrorHandler errorHandler) {
        SQLiteDatabase db = new SQLiteDatabase(path, flags, factory, errorHandler);
        db.open();
        return db;
    }

    public static SQLiteDatabase openOrCreateDatabase(File file, CursorFactory factory) { return openOrCreateDatabase(file.getPath(), factory); }
    public static SQLiteDatabase openOrCreateDatabase(String path, CursorFactory factory) { return openDatabase(path, factory, CREATE_IF_NECESSARY, null); }
    public static SQLiteDatabase openOrCreateDatabase(String path, CursorFactory factory, DatabaseErrorHandler errorHandler) { return openDatabase(path, factory, CREATE_IF_NECESSARY, errorHandler); }
    public static SQLiteDatabase create(CursorFactory factory) { return openDatabase(":memory:", factory, CREATE_IF_NECESSARY); }
    public static SQLiteDatabase createInMemory(OpenParams openParams) { return openDatabase(":memory:", openParams.getCursorFactory(), CREATE_IF_NECESSARY); }

    public static boolean deleteDatabase(File file) {
        boolean deleted = file.delete();
        new File(file.getPath() + "-journal").delete();
        new File(file.getPath() + "-shm").delete();
        new File(file.getPath() + "-wal").delete();
        return deleted;
    }

    public static int releaseMemory() { return 0; }

    @Override
    protected void onAllReferencesReleased() {
        synchronized (mLock) {
            if (mHandle != 0) {
                SQLiteNative.nClose(mHandle);
                mHandle = 0;
            }
        }
    }

    private long handle() {
        if (mHandle == 0) throw new IllegalStateException("attempt to re-open an already-closed object: SQLiteDatabase: " + mPath);
        return mHandle;
    }

    // ---- low level execution ------------------------------------------------------------------

    static final class Result {
        String[] columns;
        ArrayList<Object[]> rows;
    }

    private static void bindAll(long stmt, Object[] args) {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            int idx = i + 1;
            if (a == null) SQLiteNative.nBindNull(stmt, idx);
            else if (a instanceof byte[]) SQLiteNative.nBindBlob(stmt, idx, (byte[]) a);
            else if (a instanceof Double || a instanceof Float) SQLiteNative.nBindDouble(stmt, idx, ((Number) a).doubleValue());
            else if (a instanceof Number) SQLiteNative.nBindLong(stmt, idx, ((Number) a).longValue());
            else if (a instanceof Boolean) SQLiteNative.nBindLong(stmt, idx, ((Boolean) a) ? 1 : 0);
            else SQLiteNative.nBindString(stmt, idx, a.toString());
        }
    }

    Result runQuery(String sql, Object[] args) {
        synchronized (mLock) {
            long stmt = SQLiteNative.nPrepare(handle(), sql);
            try {
                bindAll(stmt, args);
                Result r = new Result();
                int n = SQLiteNative.nColumnCount(stmt);
                r.columns = new String[n];
                for (int i = 0; i < n; i++) r.columns[i] = SQLiteNative.nColumnName(stmt, i);
                r.rows = new ArrayList<Object[]>();
                while (SQLiteNative.nStep(stmt) == SQLiteNative.SQLITE_ROW) {
                    Object[] row = new Object[n];
                    for (int i = 0; i < n; i++) {
                        switch (SQLiteNative.nColumnType(stmt, i)) {
                            case 1: row[i] = SQLiteNative.nColumnLong(stmt, i); break;
                            case 2: row[i] = SQLiteNative.nColumnDouble(stmt, i); break;
                            case 3: row[i] = SQLiteNative.nColumnText(stmt, i); break;
                            case 4: row[i] = SQLiteNative.nColumnBlob(stmt, i); break;
                            default: row[i] = null; break;
                        }
                    }
                    r.rows.add(row);
                }
                return r;
            } finally {
                SQLiteNative.nFinalize(stmt);
            }
        }
    }

    Object queryScalar(String sql, Object[] args) {
        Result r = runQuery(sql, args);
        if (r.rows.isEmpty() || r.columns.length == 0) return null;
        return r.rows.get(0)[0];
    }

    long execInternal(String sql, Object[] args, int mode) {
        synchronized (mLock) {
            long stmt = SQLiteNative.nPrepare(handle(), sql);
            try {
                bindAll(stmt, args);
                while (SQLiteNative.nStep(stmt) == SQLiteNative.SQLITE_ROW) {}
            } finally {
                SQLiteNative.nFinalize(stmt);
            }
            if (mode == EXEC_CHANGES) return SQLiteNative.nChanges(mHandle);
            if (mode == EXEC_INSERT) return SQLiteNative.nChanges(mHandle) > 0 ? SQLiteNative.nLastInsertRowId(mHandle) : -1;
            return 0;
        }
    }

    // ---- transactions ---------------------------------------------------------------------

    public void beginTransaction() { beginTransactionInternal("BEGIN EXCLUSIVE;"); }
    public void beginTransactionNonExclusive() { beginTransactionInternal("BEGIN IMMEDIATE;"); }
    public void beginTransactionReadOnly() { beginTransactionInternal("BEGIN;"); }
    public void beginTransactionWithListener(SQLiteTransactionListener transactionListener) { beginTransaction(); if (transactionListener != null) transactionListener.onBegin(); }
    public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener transactionListener) { beginTransactionWithListener(transactionListener); }

    private void beginTransactionInternal(String sql) {
        synchronized (mLock) {
            if (mTransactionDepth == 0) {
                execInternal(sql, null, EXEC_PLAIN);
                mTransactionFailed = false;
            }
            mTransactionDepth++;
            mTransactionSuccessful = false;
        }
    }

    public void endTransaction() {
        synchronized (mLock) {
            if (mTransactionDepth == 0) throw new IllegalStateException("no transaction pending");
            if (!mTransactionSuccessful) mTransactionFailed = true;
            mTransactionDepth--;
            mTransactionSuccessful = false;
            if (mTransactionDepth == 0) execInternal(mTransactionFailed ? "ROLLBACK;" : "COMMIT;", null, EXEC_PLAIN);
            else mTransactionSuccessful = true;
        }
    }

    public void setTransactionSuccessful() {
        synchronized (mLock) {
            if (mTransactionDepth == 0) throw new IllegalStateException("no transaction pending");
            mTransactionSuccessful = true;
        }
    }

    public boolean inTransaction() { synchronized (mLock) { return mTransactionDepth > 0; } }
    public boolean isDbLockedByCurrentThread() { return inTransaction(); }
    @Deprecated public boolean isDbLockedByOtherThreads() { return false; }
    @Deprecated public boolean yieldIfContended() { return false; }
    public boolean yieldIfContendedSafely() { return false; }
    public boolean yieldIfContendedSafely(long sleepAfterYieldDelay) { return false; }
    @Deprecated public Map<String, String> getSyncedTables() { return new java.util.HashMap<String, String>(0); }

    // ---- metadata -------------------------------------------------------------------------------

    public int getVersion() { return ((Long) queryScalar("PRAGMA user_version;", null)).intValue(); }
    public void setVersion(int version) { execSQL("PRAGMA user_version = " + version); }
    public long getMaximumSize() { Object v = queryScalar("PRAGMA max_page_count;", null); return v == null ? 0 : ((Number) v).longValue() * getPageSize(); }
    public long setMaximumSize(long numBytes) { return numBytes; }
    public long getPageSize() { Object v = queryScalar("PRAGMA page_size;", null); return v == null ? 4096 : ((Number) v).longValue(); }
    public void setPageSize(long numBytes) { execSQL("PRAGMA page_size = " + numBytes); }
    @Deprecated public void markTableSyncable(String table, String deletedTable) {}
    @Deprecated public void markTableSyncable(String table, String foreignKey, String updateTable) {}
    public static String findEditTable(String tables) {
        int spacepos = tables.indexOf(' ');
        int commapos = tables.indexOf(',');
        if (spacepos > 0 && (spacepos < commapos || commapos < 0)) return tables.substring(0, spacepos);
        else if (commapos > 0 && (commapos < spacepos || spacepos < 0)) return tables.substring(0, commapos);
        return tables;
    }

    public SQLiteStatement compileStatement(String sql) throws SQLException { return new SQLiteStatement(this, sql, null); }

    // ---- queries ---------------------------------------------------------------------------------

    public Cursor query(boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return queryWithFactory(null, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit, null);
    }

    public Cursor query(boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit, CancellationSignal cancellationSignal) {
        return queryWithFactory(null, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit, cancellationSignal);
    }

    public Cursor queryWithFactory(CursorFactory cursorFactory, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return queryWithFactory(cursorFactory, distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit, null);
    }

    public Cursor queryWithFactory(CursorFactory cursorFactory, boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit, CancellationSignal cancellationSignal) {
        String sql = SQLiteQueryBuilder.buildQueryString(distinct, table, columns, selection, groupBy, having, orderBy, limit);
        return rawQueryWithFactory(cursorFactory, sql, selectionArgs, findEditTable(table), cancellationSignal);
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return query(false, table, columns, selection, selectionArgs, groupBy, having, orderBy, null);
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return query(false, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) { return rawQueryWithFactory(null, sql, selectionArgs, null, null); }
    public Cursor rawQuery(String sql, String[] selectionArgs, CancellationSignal cancellationSignal) { return rawQueryWithFactory(null, sql, selectionArgs, null, cancellationSignal); }
    public Cursor rawQueryWithFactory(CursorFactory cursorFactory, String sql, String[] selectionArgs, String editTable) { return rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable, null); }

    public Cursor rawQueryWithFactory(CursorFactory cursorFactory, final String sql, String[] selectionArgs, final String editTable, CancellationSignal cancellationSignal) {
        final SQLiteQuery q = new SQLiteQuery(this, sql, selectionArgs);
        CursorFactory f = cursorFactory != null ? cursorFactory : mFactory;
        SQLiteCursorDriver driver = new SQLiteCursorDriver() {
            public Cursor query(CursorFactory factory, String[] bindArgs) { return new SQLiteCursor(this, editTable, q); }
            public void cursorDeactivated() {}
            public void cursorRequeried(Cursor cursor) {}
            public void cursorClosed() {}
            public void setBindArguments(String[] bindArgs) {}
        };
        if (f != null) return f.newCursor(this, driver, editTable, q);
        return new SQLiteCursor(driver, editTable, q);
    }

    public long insert(String table, String nullColumnHack, ContentValues values) {
        try {
            return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE);
        } catch (SQLException e) {
            android.util.Log.e("SQLiteDatabase", "Error inserting " + values, e);
            return -1;
        }
    }

    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
        return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE);
    }

    public long replace(String table, String nullColumnHack, ContentValues initialValues) {
        try {
            return insertWithOnConflict(table, nullColumnHack, initialValues, CONFLICT_REPLACE);
        } catch (SQLException e) {
            android.util.Log.e("SQLiteDatabase", "Error inserting " + initialValues, e);
            return -1;
        }
    }

    public long replaceOrThrow(String table, String nullColumnHack, ContentValues initialValues) throws SQLException {
        return insertWithOnConflict(table, nullColumnHack, initialValues, CONFLICT_REPLACE);
    }

    public long insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues, int conflictAlgorithm) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT");
        sql.append(CONFLICT_VALUES[conflictAlgorithm]);
        sql.append(" INTO ");
        sql.append(table);
        sql.append('(');
        Object[] bindArgs = null;
        int size = (initialValues != null && !initialValues.isEmpty()) ? initialValues.size() : 0;
        if (size > 0) {
            bindArgs = new Object[size];
            int i = 0;
            for (String colName : initialValues.keySet()) {
                sql.append((i > 0) ? "," : "");
                sql.append(colName);
                bindArgs[i++] = initialValues.get(colName);
            }
            sql.append(')');
            sql.append(" VALUES (");
            for (i = 0; i < size; i++) sql.append((i > 0) ? ",?" : "?");
        } else {
            sql.append(nullColumnHack).append(") VALUES (NULL");
        }
        sql.append(')');
        return execInternal(sql.toString(), bindArgs, EXEC_INSERT);
    }

    public int delete(String table, String whereClause, String[] whereArgs) {
        return (int) execInternal("DELETE FROM " + table + (whereClause != null && !whereClause.isEmpty() ? " WHERE " + whereClause : ""), whereArgs, EXEC_CHANGES);
    }

    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return updateWithOnConflict(table, values, whereClause, whereArgs, CONFLICT_NONE);
    }

    public int updateWithOnConflict(String table, ContentValues values, String whereClause, String[] whereArgs, int conflictAlgorithm) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Empty values");
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(CONFLICT_VALUES[conflictAlgorithm]);
        sql.append(table);
        sql.append(" SET ");
        int setValuesSize = values.size();
        int bindArgsSize = (whereArgs == null) ? setValuesSize : (setValuesSize + whereArgs.length);
        Object[] bindArgs = new Object[bindArgsSize];
        int i = 0;
        for (String colName : values.keySet()) {
            sql.append((i > 0) ? "," : "");
            sql.append(colName);
            bindArgs[i++] = values.get(colName);
            sql.append("=?");
        }
        if (whereArgs != null) for (i = setValuesSize; i < bindArgsSize; i++) bindArgs[i] = whereArgs[i - setValuesSize];
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }
        return (int) execInternal(sql.toString(), bindArgs, EXEC_CHANGES);
    }

    public void execSQL(String sql) throws SQLException { execInternal(sql, null, EXEC_PLAIN); }

    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        if (bindArgs == null) throw new IllegalArgumentException("Empty bindArgs");
        execInternal(sql, bindArgs, EXEC_PLAIN);
    }

    public void execPerConnectionSQL(String sql, Object[] bindArgs) throws SQLException { execInternal(sql, bindArgs, EXEC_PLAIN); }
    public void validateSql(String sql, CancellationSignal cancellationSignal) { SQLiteNative.nFinalize(SQLiteNative.nPrepare(handle(), sql)); }
    public boolean isReadOnly() { return (mFlags & OPEN_READONLY) != 0; }
    public boolean isInMemoryDatabase() { return ":memory:".equals(mPath); }
    public boolean isOpen() { return mHandle != 0; }
    public boolean needUpgrade(int newVersion) { return newVersion > getVersion(); }
    public final String getPath() { return mPath; }
    public void setLocale(Locale locale) {}
    public void setMaxSqlCacheSize(int cacheSize) {}
    public void setForeignKeyConstraintsEnabled(boolean enable) { execSQL("PRAGMA foreign_keys = " + (enable ? "ON" : "OFF")); }
    public boolean enableWriteAheadLogging() { return true; }
    public void disableWriteAheadLogging() {}
    public boolean isWriteAheadLoggingEnabled() { return false; }
    public List<Pair<String, String>> getAttachedDbs() {
        ArrayList<Pair<String, String>> l = new ArrayList<Pair<String, String>>();
        l.add(new Pair<String, String>("main", mPath));
        return l;
    }
    public boolean isDatabaseIntegrityOk() { return true; }
    @Override public String toString() { return "SQLiteDatabase: " + getPath(); }

    public static final class OpenParams {
        private final int mOpenFlags;
        private final CursorFactory mCursorFactory;
        private final DatabaseErrorHandler mErrorHandler;

        OpenParams(int flags, CursorFactory factory, DatabaseErrorHandler handler) {
            mOpenFlags = flags;
            mCursorFactory = factory;
            mErrorHandler = handler;
        }

        public int getOpenFlags() { return mOpenFlags; }
        public CursorFactory getCursorFactory() { return mCursorFactory; }
        public DatabaseErrorHandler getErrorHandler() { return mErrorHandler; }
        public int getLookasideSlotSize() { return -1; }
        public int getLookasideSlotCount() { return -1; }
        public long getIdleConnectionTimeout() { return -1; }
        public String getJournalMode() { return null; }
        public String getSynchronousMode() { return null; }

        public static final class Builder {
            private int mOpenFlags;
            private CursorFactory mCursorFactory;
            private DatabaseErrorHandler mErrorHandler;

            public Builder() {}
            public Builder(OpenParams params) {
                mOpenFlags = params.mOpenFlags;
                mCursorFactory = params.mCursorFactory;
                mErrorHandler = params.mErrorHandler;
            }
            public Builder setLookasideConfig(int slotSize, int slotCount) { return this; }
            public Builder addOpenFlags(int openFlags) { mOpenFlags |= openFlags; return this; }
            public Builder removeOpenFlags(int openFlags) { mOpenFlags &= ~openFlags; return this; }
            public Builder setOpenFlags(int openFlags) { mOpenFlags = openFlags; return this; }
            public Builder setCursorFactory(CursorFactory cursorFactory) { mCursorFactory = cursorFactory; return this; }
            public Builder setErrorHandler(DatabaseErrorHandler errorHandler) { mErrorHandler = errorHandler; return this; }
            public Builder setIdleConnectionTimeout(long idleConnectionTimeoutMs) { return this; }
            public Builder setJournalMode(String journalMode) { return this; }
            public Builder setSynchronousMode(String syncMode) { return this; }
            public OpenParams build() { return new OpenParams(mOpenFlags, mCursorFactory, mErrorHandler); }
        }
    }
}
