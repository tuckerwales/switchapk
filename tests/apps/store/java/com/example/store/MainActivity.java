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
        helper.close();
        if (!second) logReadOnly();
        logContextDb();
        logMemory();
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

    private static String blobText(byte[] b) {
        if (b == null || b.length == 0) return "null";
        return b.length + ":" + (b[0] & 0xff) + "," + (b[b.length - 1] & 0xff);
    }
}
