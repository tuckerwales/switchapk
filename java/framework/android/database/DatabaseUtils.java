package android.database;

import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.sqlite.SQLiteAbortException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteProgram;
import android.database.sqlite.SQLiteStatement;
import android.os.OperationCanceledException;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Helpers for queries, SQL text and copying a cursor into a window or ContentValues. */
public class DatabaseUtils {
    private static final String TAG = "DatabaseUtils";

    public static final int STATEMENT_SELECT = 1;
    public static final int STATEMENT_UPDATE = 2;
    public static final int STATEMENT_ATTACH = 3;
    public static final int STATEMENT_BEGIN = 4;
    public static final int STATEMENT_COMMIT = 5;
    public static final int STATEMENT_ABORT = 6;
    public static final int STATEMENT_PRAGMA = 7;
    public static final int STATEMENT_DDL = 8;
    public static final int STATEMENT_UNPREPARED = 9;
    public static final int STATEMENT_OTHER = 99;

    public DatabaseUtils() {}

    public static final void writeExceptionToParcel(Parcel reply, Exception e) {
        int code = exceptionCode(e);
        boolean logException = code != 1 && code != 11;
        if (code == 0) {
            reply.writeException(e);
            Log.e(TAG, "Writing exception to parcel", e);
            return;
        }
        reply.writeInt(code);
        reply.writeString(e.getMessage());
        if (logException) Log.e(TAG, "Writing exception to parcel", e);
    }

    public static final void readExceptionFromParcel(Parcel reply) {
        int code = reply.readInt();
        if (code == 0) return;
        throwException(code, reply.readString());
    }

    public static void readExceptionWithFileNotFoundExceptionFromParcel(Parcel reply) throws FileNotFoundException {
        int code = reply.readInt();
        if (code == 0) return;
        String msg = reply.readString();
        if (code == 1) throw new FileNotFoundException(msg);
        throwException(code, msg);
    }

    public static void readExceptionWithOperationApplicationExceptionFromParcel(Parcel reply)
            throws OperationApplicationException {
        int code = reply.readInt();
        if (code == 0) return;
        String msg = reply.readString();
        if (code == 10) throw new OperationApplicationException(msg);
        throwException(code, msg);
    }

    private static int exceptionCode(Exception e) {
        if (e instanceof FileNotFoundException) return 1;
        if (e instanceof IllegalArgumentException) return 2;
        if (e instanceof UnsupportedOperationException) return 3;
        if (e instanceof SQLiteAbortException) return 4;
        if (e instanceof SQLiteConstraintException) return 5;
        if (e instanceof SQLiteDatabaseCorruptException) return 6;
        if (e instanceof SQLiteFullException) return 7;
        if (e instanceof SQLiteDiskIOException) return 8;
        if (e instanceof SQLiteException) return 9;
        if (e instanceof OperationApplicationException) return 10;
        if (e instanceof OperationCanceledException) return 11;
        return 0;
    }

    private static void throwException(int code, String msg) {
        switch (code) {
            case 2: throw new IllegalArgumentException(msg);
            case 3: throw new UnsupportedOperationException(msg);
            case 4: throw new SQLiteAbortException(msg);
            case 5: throw new SQLiteConstraintException(msg);
            case 6: throw new SQLiteDatabaseCorruptException(msg);
            case 7: throw new SQLiteFullException(msg);
            case 8: throw new SQLiteDiskIOException(msg);
            case 9: throw new SQLiteException(msg);
            case 11: throw new OperationCanceledException(msg);
            default: throw new RuntimeException("unknown exception code " + code + ": " + msg);
        }
    }

    public static void bindObjectToProgram(SQLiteProgram prog, int index, Object value) {
        if (value == null) {
            prog.bindNull(index);
        } else if (value instanceof Double || value instanceof Float) {
            prog.bindDouble(index, ((Number) value).doubleValue());
        } else if (value instanceof Number) {
            prog.bindLong(index, ((Number) value).longValue());
        } else if (value instanceof Boolean) {
            prog.bindLong(index, ((Boolean) value).booleanValue() ? 1 : 0);
        } else if (value instanceof byte[]) {
            prog.bindBlob(index, (byte[]) value);
        } else {
            prog.bindString(index, value.toString());
        }
    }

    /** framework-internal. Fills {@code window} from {@code cursor} until the window is full. */
    static void cursorFillWindow(Cursor cursor, int position, CursorWindow window) {
        if (position < 0 || position >= cursor.getCount()) return;
        final int oldPos = cursor.getPosition();
        final int numColumns = cursor.getColumnCount();
        window.clear();
        window.setStartPosition(position);
        window.setNumColumns(numColumns);
        if (cursor.moveToPosition(position)) {
            rowloop:
            do {
                if (!window.allocRow()) break;
                for (int i = 0; i < numColumns; i++) {
                    final boolean success;
                    switch (cursor.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL:
                            success = window.putNull(position, i);
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            success = window.putLong(cursor.getLong(i), position, i);
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            success = window.putDouble(cursor.getDouble(i), position, i);
                            break;
                        case Cursor.FIELD_TYPE_BLOB: {
                            byte[] value = cursor.getBlob(i);
                            success = value != null ? window.putBlob(value, position, i) : window.putNull(position, i);
                            break;
                        }
                        default: {
                            String value = cursor.getString(i);
                            success = value != null ? window.putString(value, position, i) : window.putNull(position, i);
                            break;
                        }
                    }
                    if (!success) {
                        window.freeLastRow();
                        break rowloop;
                    }
                }
                position += 1;
            } while (cursor.moveToNext());
        }
        cursor.moveToPosition(oldPos);
    }

    public static void appendEscapedSQLString(StringBuilder sb, String sqlString) {
        sb.append('\'');
        int length = sqlString.length();
        for (int i = 0; i < length; i++) {
            char c = sqlString.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i == length - 1) continue;
                if (Character.isLowSurrogate(sqlString.charAt(i + 1))) {
                    sb.append(c);
                    sb.append(sqlString.charAt(i + 1));
                    continue;
                }
                continue;
            }
            if (Character.isLowSurrogate(c)) continue;
            if (c == '\'') sb.append('\'');
            sb.append(c);
        }
        sb.append('\'');
    }

    public static String sqlEscapeString(String value) {
        StringBuilder escaper = new StringBuilder();
        appendEscapedSQLString(escaper, value);
        return escaper.toString();
    }

    public static final void appendValueToSql(StringBuilder sql, Object value) {
        if (value == null) sql.append("NULL");
        else if (value instanceof Boolean) sql.append(((Boolean) value).booleanValue() ? '1' : '0');
        else appendEscapedSQLString(sql, value.toString());
    }

    public static String concatenateWhere(String a, String b) {
        if (TextUtils.isEmpty(a)) return b;
        if (TextUtils.isEmpty(b)) return a;
        return "(" + a + ") AND (" + b + ")";
    }

    public static String getCollationKey(String name) {
        byte[] arr = collationKey(name);
        try {
            return new String(arr, 0, keyLen(arr), "ISO8859_1");
        } catch (Exception ex) {
            return "";
        }
    }

    public static String getHexCollationKey(String name) {
        byte[] arr = collationKey(name);
        int n = keyLen(arr);
        char[] keys = new char[n * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0, j = 0; i < n; i++) {
            keys[j++] = digits[(arr[i] & 0xf0) >>> 4];
            keys[j++] = digits[arr[i] & 0x0f];
        }
        return new String(keys);
    }

    /** Primary-strength key: case-folded UTF-8 plus a trailing 0, which {@link #keyLen} drops. */
    private static byte[] collationKey(String name) {
        byte[] raw;
        try {
            raw = name.toLowerCase(Locale.ROOT).getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            raw = name.toLowerCase(Locale.ROOT).getBytes();
        }
        byte[] out = new byte[raw.length + 1];
        System.arraycopy(raw, 0, out, 0, raw.length);
        return out;
    }

    private static int keyLen(byte[] arr) {
        return arr[arr.length - 1] != 0 ? arr.length : arr.length - 1;
    }

    public static void dumpCursor(Cursor cursor) { dumpCursor(cursor, System.out); }

    public static void dumpCursor(Cursor cursor, PrintStream stream) {
        stream.println(">>>>> Dumping cursor " + cursor);
        if (cursor != null) {
            int startPos = cursor.getPosition();
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) dumpCurrentRow(cursor, stream);
            cursor.moveToPosition(startPos);
        }
        stream.println("<<<<<");
    }

    public static void dumpCursor(Cursor cursor, StringBuilder sb) {
        sb.append(">>>>> Dumping cursor ").append(cursor).append('\n');
        if (cursor != null) {
            int startPos = cursor.getPosition();
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) dumpCurrentRow(cursor, sb);
            cursor.moveToPosition(startPos);
        }
        sb.append("<<<<<\n");
    }

    public static String dumpCursorToString(Cursor cursor) {
        StringBuilder sb = new StringBuilder();
        dumpCursor(cursor, sb);
        return sb.toString();
    }

    public static void dumpCurrentRow(Cursor cursor) { dumpCurrentRow(cursor, System.out); }

    public static void dumpCurrentRow(Cursor cursor, PrintStream stream) {
        String[] cols = cursor.getColumnNames();
        stream.println("" + cursor.getPosition() + " {");
        for (int i = 0; i < cols.length; i++) {
            String value;
            try {
                value = cursor.getString(i);
            } catch (SQLiteException e) {
                value = "<unprintable>";
            }
            stream.println("   " + cols[i] + '=' + value);
        }
        stream.println("}");
    }

    public static void dumpCurrentRow(Cursor cursor, StringBuilder sb) {
        String[] cols = cursor.getColumnNames();
        sb.append(cursor.getPosition()).append(" {\n");
        for (int i = 0; i < cols.length; i++) {
            String value;
            try {
                value = cursor.getString(i);
            } catch (SQLiteException e) {
                value = "<unprintable>";
            }
            sb.append("   ").append(cols[i]).append('=').append(value).append('\n');
        }
        sb.append("}\n");
    }

    public static String dumpCurrentRowToString(Cursor cursor) {
        StringBuilder sb = new StringBuilder();
        dumpCurrentRow(cursor, sb);
        return sb.toString();
    }

    public static void cursorStringToContentValues(Cursor cursor, String field, ContentValues values) {
        cursorStringToContentValues(cursor, field, values, field);
    }

    public static void cursorStringToInsertHelper(Cursor cursor, String field, InsertHelper inserter, int index) {
        inserter.bind(index, cursor.getString(cursor.getColumnIndexOrThrow(field)));
    }

    public static void cursorStringToContentValues(Cursor cursor, String field, ContentValues values, String key) {
        values.put(key, cursor.getString(cursor.getColumnIndexOrThrow(field)));
    }

    public static void cursorIntToContentValues(Cursor cursor, String field, ContentValues values) {
        cursorIntToContentValues(cursor, field, values, field);
    }

    public static void cursorIntToContentValues(Cursor cursor, String field, ContentValues values, String key) {
        int colIndex = cursor.getColumnIndex(field);
        if (!cursor.isNull(colIndex)) values.put(key, Integer.valueOf(cursor.getInt(colIndex)));
        else values.put(key, (Integer) null);
    }

    public static void cursorLongToContentValues(Cursor cursor, String field, ContentValues values) {
        cursorLongToContentValues(cursor, field, values, field);
    }

    public static void cursorLongToContentValues(Cursor cursor, String field, ContentValues values, String key) {
        int colIndex = cursor.getColumnIndex(field);
        if (!cursor.isNull(colIndex)) values.put(key, Long.valueOf(cursor.getLong(colIndex)));
        else values.put(key, (Long) null);
    }

    public static void cursorDoubleToCursorValues(Cursor cursor, String field, ContentValues values) {
        cursorDoubleToContentValues(cursor, field, values, field);
    }

    public static void cursorDoubleToContentValues(Cursor cursor, String field, ContentValues values, String key) {
        int colIndex = cursor.getColumnIndex(field);
        if (!cursor.isNull(colIndex)) values.put(key, Double.valueOf(cursor.getDouble(colIndex)));
        else values.put(key, (Double) null);
    }

    public static void cursorRowToContentValues(Cursor cursor, ContentValues values) {
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB) values.put(columns[i], cursor.getBlob(i));
            else values.put(columns[i], cursor.getString(i));
        }
    }

    public static long queryNumEntries(SQLiteDatabase db, String table) {
        return queryNumEntries(db, table, null, null);
    }

    public static long queryNumEntries(SQLiteDatabase db, String table, String selection) {
        return queryNumEntries(db, table, selection, null);
    }

    public static long queryNumEntries(SQLiteDatabase db, String table, String selection, String[] selectionArgs) {
        String where = !TextUtils.isEmpty(selection) ? " where " + selection : "";
        return longForQuery(db, "select count(*) from " + table + where, selectionArgs);
    }

    public static long longForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        SQLiteStatement prog = db.compileStatement(query);
        try {
            return longForQuery(prog, selectionArgs);
        } finally {
            prog.close();
        }
    }

    public static long longForQuery(SQLiteStatement prog, String[] selectionArgs) {
        prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForLong();
    }

    public static String stringForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        SQLiteStatement prog = db.compileStatement(query);
        try {
            return stringForQuery(prog, selectionArgs);
        } finally {
            prog.close();
        }
    }

    public static String stringForQuery(SQLiteStatement prog, String[] selectionArgs) {
        prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForString();
    }

    public static ParcelFileDescriptor blobFileDescriptorForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        SQLiteStatement prog = db.compileStatement(query);
        try {
            return blobFileDescriptorForQuery(prog, selectionArgs);
        } finally {
            prog.close();
        }
    }

    public static ParcelFileDescriptor blobFileDescriptorForQuery(SQLiteStatement prog, String[] selectionArgs) {
        prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForBlobFileDescriptor();
    }

    public static void cursorStringToContentValuesIfPresent(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) values.put(column, cursor.getString(index));
    }

    public static void cursorLongToContentValuesIfPresent(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) values.put(column, Long.valueOf(cursor.getLong(index)));
    }

    public static void cursorShortToContentValuesIfPresent(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) values.put(column, Short.valueOf(cursor.getShort(index)));
    }

    public static void cursorIntToContentValuesIfPresent(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) values.put(column, Integer.valueOf(cursor.getInt(index)));
    }

    public static void cursorFloatToContentValuesIfPresent(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) values.put(column, Float.valueOf(cursor.getFloat(index)));
    }

    public static void cursorDoubleToContentValuesIfPresent(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) values.put(column, Double.valueOf(cursor.getDouble(index)));
    }

    public static void createDbFromSqlStatements(Context context, String dbName, int dbVersion, String sqlStatements) {
        SQLiteDatabase db = context.openOrCreateDatabase(dbName, 0, null);
        String[] statements = TextUtils.split(sqlStatements, ";\n");
        for (int i = 0; i < statements.length; i++) {
            if (TextUtils.isEmpty(statements[i])) continue;
            db.execSQL(statements[i]);
        }
        db.setVersion(dbVersion);
        db.close();
    }

    public static int getSqlStatementType(String sql) {
        String prefix = statementPrefix(sql);
        if (prefix == null) return STATEMENT_OTHER;
        String upper = sql.toUpperCase(Locale.ROOT);
        if ("SEL".equals(prefix)) return STATEMENT_SELECT;
        if ("INS".equals(prefix) || "UPD".equals(prefix) || "REP".equals(prefix) || "DEL".equals(prefix)) {
            return STATEMENT_UPDATE;
        }
        if ("ATT".equals(prefix)) return STATEMENT_ATTACH;
        if ("COM".equals(prefix) || "END".equals(prefix)) return STATEMENT_COMMIT;
        if ("ROL".equals(prefix)) return upper.contains(" TO ") ? STATEMENT_OTHER : STATEMENT_ABORT;
        if ("BEG".equals(prefix)) return STATEMENT_BEGIN;
        if ("PRA".equals(prefix)) return STATEMENT_PRAGMA;
        if ("CRE".equals(prefix) || "DRO".equals(prefix) || "ALT".equals(prefix)) return STATEMENT_DDL;
        if ("ANA".equals(prefix) || "DET".equals(prefix)) return STATEMENT_UNPREPARED;
        return STATEMENT_OTHER;
    }

    /** Three letters of the statement, skipping whitespace and comments. */
    private static String statementPrefix(String sql) {
        if (sql == null) return null;
        int limit = sql.length() - 2;
        int i = 0;
        while (i < limit) {
            char c = sql.charAt(i);
            if (c <= ' ') {
                i++;
            } else if (c == '-') {
                if (sql.charAt(i + 1) != '-') return takePrefix(sql, i);
                i = sql.indexOf('\n', i + 2);
                if (i < 0) return null;
                i++;
            } else if (c == '/') {
                if (sql.charAt(i + 1) != '*') return takePrefix(sql, i);
                i++;
                do {
                    i = sql.indexOf('*', i + 1);
                    if (i < 0) return null;
                    i++;
                } while (i < sql.length() && sql.charAt(i) != '/');
                i++;
            } else {
                return takePrefix(sql, i);
            }
        }
        return null;
    }

    private static String takePrefix(String sql, int n) {
        int end = Math.min(n + 3, sql.length());
        if (end - n < 3) return null;
        return sql.substring(n, end).toUpperCase(Locale.ROOT);
    }

    public static String[] appendSelectionArgs(String[] originalValues, String[] newValues) {
        if (originalValues == null || originalValues.length == 0) return newValues;
        String[] result = new String[originalValues.length + newValues.length];
        System.arraycopy(originalValues, 0, result, 0, originalValues.length);
        System.arraycopy(newValues, 0, result, originalValues.length, newValues.length);
        return result;
    }

    /**
     * Builds one INSERT for repeated rows. Deprecated on Android; kept because apps still call it.
     */
    @Deprecated
    public static class InsertHelper {
        /** Column index of the name in PRAGMA table_info. */
        private static final int TABLE_INFO_PRAGMA_COLUMNNAME_INDEX = 1;
        /** Column index of the default in PRAGMA table_info. */
        private static final int TABLE_INFO_PRAGMA_DEFAULT_INDEX = 4;

        private final SQLiteDatabase mDb;
        private final String mTableName;
        private HashMap<String, Integer> mColumns;
        private String mInsertSQL;
        private SQLiteStatement mInsertStatement;
        private SQLiteStatement mReplaceStatement;
        private SQLiteStatement mPreparedStatement;

        public InsertHelper(SQLiteDatabase db, String tableName) {
            mDb = db;
            mTableName = tableName;
        }

        private void buildSQL() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("INSERT INTO ");
            sb.append(mTableName);
            sb.append(" (");
            StringBuilder sbv = new StringBuilder(128);
            sbv.append("VALUES (");
            Cursor cur = mDb.rawQuery("PRAGMA table_info(" + mTableName + ")", null);
            try {
                mColumns = new HashMap<String, Integer>(cur.getCount());
                int i = 1;
                int count = cur.getCount();
                while (cur.moveToNext()) {
                    String columnName = cur.getString(TABLE_INFO_PRAGMA_COLUMNNAME_INDEX);
                    String defaultValue = cur.getString(TABLE_INFO_PRAGMA_DEFAULT_INDEX);
                    mColumns.put(columnName, Integer.valueOf(i));
                    sb.append("'");
                    sb.append(columnName);
                    sb.append("'");
                    if (defaultValue == null) sbv.append("?");
                    else sbv.append("COALESCE(?, ").append(defaultValue).append(")");
                    sb.append(i == count ? ") " : ", ");
                    sbv.append(i == count ? ");" : ", ");
                    i++;
                }
            } finally {
                cur.close();
            }
            sb.append(sbv);
            mInsertSQL = sb.toString();
        }

        private SQLiteStatement getStatement(boolean allowReplace) {
            if (allowReplace) {
                if (mReplaceStatement == null) {
                    if (mInsertSQL == null) buildSQL();
                    String replaceSQL = "INSERT OR REPLACE" + mInsertSQL.substring(6);
                    mReplaceStatement = mDb.compileStatement(replaceSQL);
                }
                return mReplaceStatement;
            }
            if (mInsertStatement == null) {
                if (mInsertSQL == null) buildSQL();
                mInsertStatement = mDb.compileStatement(mInsertSQL);
            }
            return mInsertStatement;
        }

        private long insertInternal(ContentValues values, boolean allowReplace) {
            mDb.beginTransactionNonExclusive();
            try {
                SQLiteStatement stmt = getStatement(allowReplace);
                stmt.clearBindings();
                for (Map.Entry<String, Object> e : values.valueSet()) {
                    DatabaseUtils.bindObjectToProgram(stmt, getColumnIndex(e.getKey()), e.getValue());
                }
                long result = stmt.executeInsert();
                mDb.setTransactionSuccessful();
                return result;
            } catch (SQLException e) {
                Log.e(TAG, "Error inserting into " + mTableName, e);
                return -1;
            } finally {
                mDb.endTransaction();
            }
        }

        public int getColumnIndex(String key) {
            getStatement(false);
            Integer index = mColumns.get(key);
            if (index == null) throw new IllegalArgumentException("column '" + key + "' is invalid");
            return index.intValue();
        }

        public void bind(int index, double value) { mPreparedStatement.bindDouble(index, value); }
        public void bind(int index, float value) { mPreparedStatement.bindDouble(index, value); }
        public void bind(int index, long value) { mPreparedStatement.bindLong(index, value); }
        public void bind(int index, int value) { mPreparedStatement.bindLong(index, value); }
        public void bind(int index, boolean value) { mPreparedStatement.bindLong(index, value ? 1 : 0); }
        public void bindNull(int index) { mPreparedStatement.bindNull(index); }

        public void bind(int index, byte[] value) {
            if (value == null) mPreparedStatement.bindNull(index);
            else mPreparedStatement.bindBlob(index, value);
        }

        public void bind(int index, String value) {
            if (value == null) mPreparedStatement.bindNull(index);
            else mPreparedStatement.bindString(index, value);
        }

        public long insert(ContentValues values) { return insertInternal(values, false); }

        public long execute() {
            if (mPreparedStatement == null) {
                throw new IllegalStateException("you must prepare this inserter before calling execute");
            }
            try {
                return mPreparedStatement.executeInsert();
            } catch (SQLException e) {
                Log.e(TAG, "Error executing InsertHelper with table " + mTableName, e);
                return -1;
            } finally {
                mPreparedStatement = null;
            }
        }

        public void prepareForInsert() {
            mPreparedStatement = getStatement(false);
            mPreparedStatement.clearBindings();
        }

        public void prepareForReplace() {
            mPreparedStatement = getStatement(true);
            mPreparedStatement.clearBindings();
        }

        public long replace(ContentValues values) { return insertInternal(values, true); }

        public void close() {
            if (mInsertStatement != null) {
                mInsertStatement.close();
                mInsertStatement = null;
            }
            if (mReplaceStatement != null) {
                mReplaceStatement.close();
                mReplaceStatement = null;
            }
            mInsertSQL = null;
            mColumns = null;
        }
    }
}
