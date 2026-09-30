package android.database.sqlite;

public final class SQLiteQuery extends SQLiteProgram {
    SQLiteQuery(SQLiteDatabase db, String query, Object[] bindArgs) { super(db, query, bindArgs); }

    @Override
    public String toString() { return "SQLiteQuery: " + getSql(); }
}
