package android.database.sqlite;

import android.database.Cursor;
import java.util.Map;
import java.util.regex.Pattern;

public class SQLiteQueryBuilder {
    private static final Pattern sLimitPattern = Pattern.compile("\\s*\\d+\\s*(,\\s*\\d+\\s*)?");
    private Map<String, String> mProjectionMap = null;
    private String mTables = "";
    private StringBuilder mWhereClause = null;
    private boolean mDistinct;
    private SQLiteDatabase.CursorFactory mFactory;
    private boolean mStrict;

    public SQLiteQueryBuilder() { mDistinct = false; mFactory = null; }

    public void setDistinct(boolean distinct) { mDistinct = distinct; }
    public boolean isDistinct() { return mDistinct; }
    public String getTables() { return mTables; }
    public void setTables(String inTables) { mTables = inTables; }

    public void appendWhere(CharSequence inWhere) {
        if (mWhereClause == null) mWhereClause = new StringBuilder(inWhere.length() + 16);
        mWhereClause.append(inWhere);
    }

    public void appendWhereEscapeString(String inWhere) {
        if (mWhereClause == null) mWhereClause = new StringBuilder(inWhere.length() + 16);
        mWhereClause.append('\'').append(inWhere.replace("'", "''")).append('\'');
    }

    public void appendWhereStandalone(CharSequence inWhere) {
        if (mWhereClause == null) mWhereClause = new StringBuilder(inWhere.length() + 16);
        if (mWhereClause.length() > 0) mWhereClause.append(" AND ");
        mWhereClause.append('(').append(inWhere).append(')');
    }

    public void setProjectionMap(Map<String, String> columnMap) { mProjectionMap = columnMap; }
    public Map<String, String> getProjectionMap() { return mProjectionMap; }
    public void setCursorFactory(SQLiteDatabase.CursorFactory factory) { mFactory = factory; }
    public SQLiteDatabase.CursorFactory getCursorFactory() { return mFactory; }
    public void setStrict(boolean strict) { mStrict = strict; }
    public boolean isStrict() { return mStrict; }

    public static String buildQueryString(boolean distinct, String tables, String[] columns, String where, String groupBy, String having, String orderBy, String limit) {
        if (isEmpty(groupBy) && !isEmpty(having)) throw new IllegalArgumentException("HAVING clauses are only permitted when using a groupBy clause");
        if (!isEmpty(limit) && !sLimitPattern.matcher(limit).matches()) throw new IllegalArgumentException("invalid LIMIT clauses:" + limit);
        StringBuilder query = new StringBuilder(120);
        query.append("SELECT ");
        if (distinct) query.append("DISTINCT ");
        if (columns != null && columns.length != 0) appendColumns(query, columns);
        else query.append("* ");
        query.append("FROM ");
        query.append(tables);
        appendClause(query, " WHERE ", where);
        appendClause(query, " GROUP BY ", groupBy);
        appendClause(query, " HAVING ", having);
        appendClause(query, " ORDER BY ", orderBy);
        appendClause(query, " LIMIT ", limit);
        return query.toString();
    }

    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }

    private static void appendClause(StringBuilder s, String name, String clause) {
        if (!isEmpty(clause)) {
            s.append(name);
            s.append(clause);
        }
    }

    public static void appendColumns(StringBuilder s, String[] columns) {
        int n = columns.length;
        for (int i = 0; i < n; i++) {
            String column = columns[i];
            if (column != null) {
                if (i > 0) s.append(", ");
                s.append(column);
            }
        }
        s.append(' ');
    }

    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder, null);
    }

    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit) {
        String sql = buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
        return db.rawQueryWithFactory(mFactory, sql, selectionArgs, SQLiteDatabase.findEditTable(mTables));
    }

    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit, android.os.CancellationSignal cancellationSignal) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder, limit);
    }

    public String buildQuery(String[] projectionIn, String selection, String groupBy, String having, String sortOrder, String limit) {
        String[] projection = computeProjection(projectionIn);
        String where = mWhereClause != null && mWhereClause.length() > 0
                ? (selection != null && !selection.isEmpty() ? "(" + mWhereClause + ") AND (" + selection + ")" : mWhereClause.toString())
                : selection;
        return buildQueryString(mDistinct, mTables, projection, where, groupBy, having, sortOrder, limit);
    }

    @Deprecated
    public String buildQuery(String[] projectionIn, String selection, String[] selectionArgs, String groupBy, String having, String sortOrder, String limit) {
        return buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
    }

    private String[] computeProjection(String[] projectionIn) {
        if (projectionIn != null && projectionIn.length > 0) {
            if (mProjectionMap != null) {
                String[] projection = new String[projectionIn.length];
                for (int i = 0; i < projectionIn.length; i++) {
                    String userColumn = projectionIn[i];
                    String column = mProjectionMap.get(userColumn);
                    projection[i] = column != null ? column : userColumn;
                }
                return projection;
            }
            return projectionIn;
        } else if (mProjectionMap != null) {
            String[] projection = new String[mProjectionMap.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : mProjectionMap.entrySet()) projection[i++] = entry.getValue();
            return projection;
        }
        return null;
    }
}
