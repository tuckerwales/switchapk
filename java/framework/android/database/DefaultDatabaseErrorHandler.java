package android.database;

import android.database.sqlite.SQLiteDatabase;

public final class DefaultDatabaseErrorHandler implements DatabaseErrorHandler {
    public void onCorruption(SQLiteDatabase dbObj) {
        android.util.Log.e("DefaultDatabaseErrorHandler", "Corruption reported by sqlite on database: " + dbObj.getPath());
    }
}
