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

    public android.os.ParcelFileDescriptor simpleQueryForBlobFileDescriptor() { return null; }

    @Override
    public String toString() { return "SQLiteProgram: " + getSql(); }
}
