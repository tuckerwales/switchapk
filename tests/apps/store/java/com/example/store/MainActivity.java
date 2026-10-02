package com.example.store;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteReadOnlyDatabaseException;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/**
 * First launch creates notes.db at version 1. The second launch of the same
 * data directory opens it at version 2, so onUpgrade runs, and reads the
 * preference written by the first launch.
 */
public class MainActivity extends Activity {
    private static final String TAG = "STORE";

    public static class NoteHelper extends SQLiteOpenHelper {
        public NoteHelper(Context context, int version) {
            super(context, "notes.db", null, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE note ("
                    + "id INTEGER PRIMARY KEY, kind TEXT UNIQUE, body TEXT, payload BLOB, score REAL)");
            ContentValues cv = new ContentValues();
            cv.put("kind", "created");
            cv.put("body", "alpha");
            cv.put("payload", new byte[] {1, 2, 3, 4});
            cv.put("score", Double.valueOf(1.5));
            db.insertOrThrow("note", null, cv);
            Log.i(TAG, "onCreate " + getDatabaseName());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("ALTER TABLE note ADD COLUMN extra TEXT");
            ContentValues cv = new ContentValues();
            cv.put("kind", "upgrade");
            cv.put("body", "v" + oldVersion + "to" + newVersion);
            cv.put("extra", "added");
            db.insertOrThrow("note", null, cv);
            Log.i(TAG, "onUpgrade " + oldVersion + " " + newVersion);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("store", MODE_PRIVATE);
        boolean second = prefs.contains("token");
        NoteHelper helper = new NoteHelper(this, second ? 2 : 1);
        SQLiteDatabase db = helper.getWritableDatabase();
        if (!second) {
            insertRolledBack(db);
            insertKept(db);
            insertDuplicate(db);
            prefs.edit().putString("token", "run1").putInt("n", 7).commit();
            Log.i(TAG, "pref-write");
        } else {
            Log.i(TAG, "pref-read " + prefs.getString("token", "") + " " + prefs.getInt("n", -1));
        }
        logKind(db, "created", true);
        logKind(db, "kept", false);
        logKind(db, "rolled", false);
        if (second) logUpgrade(db);
        logDbUtils(db);
        helper.close();
        if (!second) logReadOnly();
        logContextDb();
        logMemory();
        logProviders(second);
        View swatch = new View(this);
        swatch.setBackgroundColor(second ? 0xFF1E88E5 : 0xFF43A047);
        setContentView(swatch);
    }

    private static void insertRolledBack(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("kind", "rolled");
            cv.put("body", "gone");
            db.insertOrThrow("note", null, cv);
        } finally {
            db.endTransaction();
        }
    }

    private static void insertKept(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("kind", "kept");
            cv.put("body", "beta");
            cv.put("payload", new byte[] {9});
            cv.put("score", Double.valueOf(2.25));
            db.insertOrThrow("note", null, cv);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void insertDuplicate(SQLiteDatabase db) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("kind", "created");
            cv.put("body", "nope");
            db.insertOrThrow("note", null, cv);
            Log.i(TAG, "constraint missing");
        } catch (SQLiteConstraintException e) {
            Log.i(TAG, "constraint");
        }
    }

    private void logReadOnly() {
        SQLiteDatabase ro = SQLiteDatabase.openDatabase(getDatabasePath("notes.db").getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
        try {
            ro.execSQL("INSERT INTO note (kind, body) VALUES ('ro', 'no')");
            Log.i(TAG, "readonly missing");
        } catch (SQLiteReadOnlyDatabaseException e) {
            Log.i(TAG, "readonly");
        } finally {
            ro.close();
        }
    }

    private void logContextDb() {
        SQLiteDatabase db = openOrCreateDatabase("side.db", MODE_PRIVATE, null);
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS t (x INTEGER)");
            Cursor c = db.rawQuery("SELECT x FROM t", null);
            try {
                if (c.moveToFirst()) Log.i(TAG, "context-db read " + c.getInt(0));
                else {
                    db.execSQL("INSERT INTO t (x) VALUES (4)");
                    Log.i(TAG, "context-db write 4");
                }
            } finally {
                c.close();
            }
        } finally {
            db.close();
        }
    }

    private static void logMemory() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        try {
            db.execSQL("CREATE TABLE t (x INTEGER)");
            db.execSQL("INSERT INTO t (x) VALUES (3)");
            Cursor c = db.rawQuery("SELECT x FROM t", null);
            try {
                c.moveToFirst();
                Log.i(TAG, "memory " + c.getInt(0));
            } finally {
                c.close();
            }
        } finally {
            db.close();
        }
    }

    private static void logKind(SQLiteDatabase db, String kind, boolean full) {
        String[] cols = full ? new String[] {"body", "payload", "score"} : new String[] {"body"};
        Cursor c = db.query("note", cols, "kind=?", new String[] {kind}, null, null, null);
        try {
            if (!c.moveToFirst()) {
                Log.i(TAG, "query " + kind + " n=0");
                return;
            }
            if (!full) {
                Log.i(TAG, "query " + kind + " n=" + c.getCount() + " body=" + c.getString(0));
                return;
            }
            Log.i(TAG, "query " + kind + " n=" + c.getCount() + " body=" + c.getString(0) + " blob="
                    + blobText(c.getBlob(1)) + " score=" + c.getDouble(2));
        } finally {
            c.close();
        }
    }

    private static void logUpgrade(SQLiteDatabase db) {
        Cursor c = db.query("note", new String[] {"body", "extra"}, "kind=?", new String[] {"upgrade"}, null, null,
                null);
        try {
            if (!c.moveToFirst()) {
                Log.i(TAG, "query upgrade n=0");
                return;
            }
            String extra = c.isNull(1) ? "null" : c.getString(1);
            Log.i(TAG, "query upgrade n=" + c.getCount() + " body=" + c.getString(0) + " extra=" + extra);
        } finally {
            c.close();
        }
    }

    private void logDbUtils(SQLiteDatabase db) {
        Log.i(TAG, "entries " + android.database.DatabaseUtils.queryNumEntries(db, "note"));
        String body = android.database.DatabaseUtils.stringForQuery(db,
                "SELECT body FROM note WHERE kind=?", new String[] {"created"});
        Log.i(TAG, "string " + body);
        String escaped = android.database.DatabaseUtils.sqlEscapeString("a'b");
        Log.i(TAG, "escape " + ("'a''b'".equals(escaped) ? "ok" : escaped));
        String where = android.database.DatabaseUtils.concatenateWhere("kind=?", "body=?");
        Log.i(TAG, "where " + ("(kind=?) AND (body=?)".equals(where) ? "ok" : where));
        int sel = android.database.DatabaseUtils.getSqlStatementType("SELECT 1");
        int ddl = android.database.DatabaseUtils.getSqlStatementType("CREATE TABLE t (x INTEGER)");
        int abort = android.database.DatabaseUtils.getSqlStatementType("ROLLBACK");
        int other = android.database.DatabaseUtils.getSqlStatementType("ROLLBACK TO save");
        int comment = android.database.DatabaseUtils.getSqlStatementType("-- c\nSELECT 1");
        Log.i(TAG, "types " + sel + "," + ddl + "," + abort + "," + other + "," + comment);
        String key = android.database.DatabaseUtils.getCollationKey("Ab");
        Log.i(TAG, "collate " + ("ab".equals(key) ? "ok" : key));
        logWindow(db);
        logTinyWindow();
        logBlobFd(db);
        logRowValues(db);
    }

    private static void logWindow(SQLiteDatabase db) {
        Cursor c = db.query("note", new String[] {"body"}, "kind=?", new String[] {"created"}, null, null, null);
        try {
            android.database.CursorWindow window = new android.database.CursorWindow("notes");
            ((android.database.CrossProcessCursor) c).fillWindow(0, window);
            Log.i(TAG, "window body=" + window.getString(0, 0));
            android.os.Parcel parcel = android.os.Parcel.obtain();
            window.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            android.database.CursorWindow copy = android.database.CursorWindow.CREATOR.createFromParcel(parcel);
            Log.i(TAG, "parcel body=" + copy.getString(0, 0));
        } finally {
            c.close();
        }
    }

    private static void logTinyWindow() {
        android.database.MatrixCursor cursor = new android.database.MatrixCursor(new String[] {"body"});
        String body = "abcdefghijklmnopqrstuvwxyz";
        cursor.addRow(new Object[] {body});
        cursor.addRow(new Object[] {"second-row-should-not-fit"});
        android.database.CursorWindow window = new android.database.CursorWindow("tiny", 64);
        ((android.database.CrossProcessCursor) cursor).fillWindow(0, window);
        String got = window.getString(0, 0);
        if (window.getNumRows() == 1 && body.equals(got)) Log.i(TAG, "tiny rows=1");
        else Log.i(TAG, "tiny fail rows=" + window.getNumRows() + " body=" + got);
    }

    private static void logBlobFd(SQLiteDatabase db) {
        android.os.ParcelFileDescriptor pfd = android.database.DatabaseUtils.blobFileDescriptorForQuery(db,
                "SELECT payload FROM note WHERE kind=?", new String[] {"created"});
        try {
            java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd);
            try {
                byte[] buf = new byte[16];
                int n = in.read(buf);
                byte[] got = new byte[n > 0 ? n : 0];
                if (n > 0) System.arraycopy(buf, 0, got, 0, n);
                Log.i(TAG, "blobfd " + blobText(got));
            } finally {
                in.close();
            }
        } catch (java.io.IOException e) {
            Log.i(TAG, "blobfd fail " + e.getMessage());
        }
    }

    private static void logRowValues(SQLiteDatabase db) {
        Cursor c = db.query("note", new String[] {"body"}, "kind=?", new String[] {"created"}, null, null, null);
        try {
            c.moveToFirst();
            android.content.ContentValues values = new android.content.ContentValues();
            android.database.DatabaseUtils.cursorRowToContentValues(c, values);
            Log.i(TAG, "row body=" + values.getAsString("body"));
        } finally {
            c.close();
        }
    }

    private void logProviders(boolean second) {
        android.content.ContentResolver cr = getContentResolver();
        if (second) {
            int prev = android.provider.Settings.System.getInt(cr,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS, -1);
            Log.i(TAG, "settings prev=" + prev);
        }
        android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, 40);
        int now = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, -1);
        Log.i(TAG, "settings " + now);
        logMedia(cr);
        logFile(cr);
        logDump(second);
    }

    private void logMedia(android.content.ContentResolver cr) {
        try {
            android.net.Uri ext = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            android.provider.MediaStore.Images.Media.insertImage(cr, "/data/local/tmp/media/shot.png", "shot.png",
                    "shot");
            Cursor c = cr.query(ext, new String[] {android.provider.MediaStore.MediaColumns.DISPLAY_NAME}, null, null,
                    null);
            String name = "none";
            if (c != null) {
                try {
                    if (c.moveToFirst()) name = c.getString(0);
                } finally {
                    c.close();
                }
            }
            Log.i(TAG, "media " + ext + " " + name);
        } catch (Exception e) {
            Log.i(TAG, "media fail " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void logFile(android.content.ContentResolver cr) {
        try {
            java.io.File note = new java.io.File(getFilesDir(), "note.txt");
            java.io.FileOutputStream out = new java.io.FileOutputStream(note);
            try {
                out.write(new byte[] {'h', 'e', 'l', 'l', 'o'});
            } finally {
                out.close();
            }
            Class<?> type = Class.forName("androidx.core.content.FileProvider");
            java.lang.reflect.Method get = type.getMethod("getUriForFile", android.content.Context.class, String.class,
                    java.io.File.class);
            android.net.Uri uri = (android.net.Uri) get.invoke(null, this, "com.example.store.files", note);
            java.io.InputStream in = cr.openInputStream(uri);
            byte[] buf = new byte[16];
            int n = in.read(buf);
            in.close();
            String text = n > 0 ? new String(buf, 0, n, "UTF-8") : "";
            Log.i(TAG, "file " + note.getName() + "=" + text + " " + cr.getType(uri));
            android.net.Uri bad = android.net.Uri.parse("content://com.example.store.files/files/../note.txt");
            try {
                java.io.InputStream escaped = cr.openInputStream(bad);
                if (escaped != null) escaped.close();
                Log.i(TAG, "file escape missing");
            } catch (SecurityException e) {
                Log.i(TAG, "file escape");
            } catch (java.io.FileNotFoundException e) {
                Log.i(TAG, "file escape");
            }
        } catch (Exception e) {
            Log.i(TAG, "file fail " + e.getClass().getSimpleName() + " " + e.getMessage());
            Throwable cause = e.getCause();
            if (cause != null) Log.i(TAG, "file cause " + cause.getClass().getSimpleName() + " " + cause.getMessage());
        }
    }

    private void logDump(boolean second) {
        try {
            if (!second) {
                android.database.DatabaseUtils.createDbFromSqlStatements(this, "dump.db", 3,
                        "CREATE TABLE t (x INTEGER);\nINSERT INTO t (x) VALUES (9);\n");
            }
            SQLiteDatabase dump = openOrCreateDatabase("dump.db", MODE_PRIVATE, null);
            try {
                Cursor c = dump.rawQuery("SELECT x FROM t", null);
                try {
                    c.moveToFirst();
                    Log.i(TAG, "dump " + c.getInt(0) + " v" + dump.getVersion());
                } finally {
                    c.close();
                }
            } finally {
                dump.close();
            }
        } catch (Exception e) {
            Log.i(TAG, "dump fail " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static String blobText(byte[] b) {
        if (b == null || b.length == 0) return "null";
        return b.length + ":" + (b[0] & 0xff) + "," + (b[b.length - 1] & 0xff);
    }
}
