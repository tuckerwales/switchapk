package android.database.sqlite;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import java.io.File;

public abstract class SQLiteOpenHelper implements AutoCloseable {
    private final Context mContext;
    private final String mName;
    private final int mNewVersion;
    private final int mMinimumSupportedVersion;
    private SQLiteDatabase mDatabase;
    private boolean mIsInitializing;
    private final SQLiteDatabase.CursorFactory mFactory;
    private final DatabaseErrorHandler mErrorHandler;
    private boolean mEnableWriteAheadLogging;

    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) { this(context, name, factory, version, null); }

    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version, DatabaseErrorHandler errorHandler) {
        this(context, name, factory, version, 0, errorHandler);
    }

    public SQLiteOpenHelper(Context context, String name, int version, SQLiteDatabase.OpenParams openParams) {
        this(context, name, openParams.getCursorFactory(), version, 0, openParams.getErrorHandler());
    }

    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version, int minimumSupportedVersion, DatabaseErrorHandler errorHandler) {
        if (version < 1) throw new IllegalArgumentException("Version must be >= 1, was " + version);
        mContext = context;
        mName = name;
        mFactory = factory;
        mNewVersion = version;
        mMinimumSupportedVersion = Math.max(0, minimumSupportedVersion);
        mErrorHandler = errorHandler;
    }

    public String getDatabaseName() { return mName; }

    public void setWriteAheadLoggingEnabled(boolean enabled) { mEnableWriteAheadLogging = enabled; }
    public void setLookasideConfig(int slotSize, int slotCount) {}
    public void setOpenParams(SQLiteDatabase.OpenParams openParams) {}
    public void setIdleConnectionTimeout(long idleConnectionTimeoutMs) {}

    public SQLiteDatabase getWritableDatabase() {
        synchronized (this) { return getDatabaseLocked(true); }
    }

    public SQLiteDatabase getReadableDatabase() {
        synchronized (this) { return getDatabaseLocked(false); }
    }

    private SQLiteDatabase getDatabaseLocked(boolean writable) {
        if (mDatabase != null) {
            if (!mDatabase.isOpen()) mDatabase = null;
            else return mDatabase;
        }
        if (mIsInitializing) throw new IllegalStateException("getDatabase called recursively");
        SQLiteDatabase db = mDatabase;
        try {
            mIsInitializing = true;
            if (mName == null) {
                db = SQLiteDatabase.create(mFactory);
            } else {
                File path = mContext.getDatabasePath(mName);
                db = SQLiteDatabase.openDatabase(path.getPath(), mFactory, SQLiteDatabase.CREATE_IF_NECESSARY, mErrorHandler);
            }
            onConfigure(db);
            final int version = db.getVersion();
            if (version != mNewVersion) {
                db.beginTransaction();
                try {
                    if (version == 0) {
                        onCreate(db);
                    } else {
                        if (version > mNewVersion) onDowngrade(db, version, mNewVersion);
                        else onUpgrade(db, version, mNewVersion);
                    }
                    db.setVersion(mNewVersion);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            onOpen(db);
            mDatabase = db;
            return db;
        } finally {
            mIsInitializing = false;
            if (db != null && db != mDatabase) db.close();
        }
    }

    public synchronized void close() {
        if (mIsInitializing) throw new IllegalStateException("Closed during initialization");
        if (mDatabase != null && mDatabase.isOpen()) {
            mDatabase.close();
            mDatabase = null;
        }
    }

    public void onConfigure(SQLiteDatabase db) {}
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("Can't downgrade database from version " + oldVersion + " to " + newVersion);
    }

    public void onOpen(SQLiteDatabase db) {}
}
