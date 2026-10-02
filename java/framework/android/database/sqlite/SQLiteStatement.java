package android.database.sqlite;

public final class SQLiteStatement extends SQLiteProgram {
    SQLiteStatement(SQLiteDatabase db, String sql, Object[] bindArgs) { super(db, sql, bindArgs); }

    public void execute() { mDatabase.execInternal(mSql, getBindArgs(), SQLiteDatabase.EXEC_PLAIN); }
    public int executeUpdateDelete() { return (int) mDatabase.execInternal(mSql, getBindArgs(), SQLiteDatabase.EXEC_CHANGES); }
    public long executeInsert() { return mDatabase.execInternal(mSql, getBindArgs(), SQLiteDatabase.EXEC_INSERT); }

    public long simpleQueryForLong() {
        Object v = mDatabase.queryScalar(mSql, getBindArgs());
        if (v == null) throw new SQLiteDoneException("expected 1 row from this query but query returned no data");
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    public String simpleQueryForString() {
        Object v = mDatabase.queryScalar(mSql, getBindArgs());
        if (v == null) throw new SQLiteDoneException("expected 1 row from this query but query returned no data");
        return v.toString();
    }

    public android.os.ParcelFileDescriptor simpleQueryForBlobFileDescriptor() {
        SQLiteDatabase.Result r = mDatabase.runQuery(mSql, getBindArgs());
        if (r.rows.isEmpty() || r.columns.length == 0) {
            throw new SQLiteDoneException("expected 1 row from this query but query returned no data");
        }
        Object v = r.rows.get(0)[0];
        if (v == null) return null;
        byte[] bytes = v instanceof byte[] ? (byte[]) v : v.toString().getBytes();
        try {
            java.io.File file = java.io.File.createTempFile("sqlite-blob-", ".bin");
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException e) {
            throw new android.database.SQLException("Could not create blob file", e);
        }
    }

    @Override
    public String toString() { return "SQLiteProgram: " + getSql(); }
}
