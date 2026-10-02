/*
 * SQLiteNative: open, prepare, bind, step and column access over the bundled
 * amalgamation. Handles are sqlite3 and sqlite3_stmt pointers. A zero handle
 * is a closed object. nFinalize(0) is a no-op because Java always finalizes
 * in a finally block, including when prepare failed.
 *
 * Paths other than ":memory:" go through platform_map_path so the file lands
 * beside the directories java.io creates. The Switch build uses its own VFS
 * (sqlite_vfs_switch.c); this file only asks that VFS to install mutexes.
 */
#include "android.h"

#include "sqlite/sqlite3.h"

#include <stdlib.h>
#include <string.h>

#define LOG_TAG "sqlite"

enum {
    OPEN_READONLY = 0x00000001,
    CREATE_IF_NECESSARY = 0x10000000,
    ENABLE_WRITE_AHEAD_LOGGING = 0x20000000
};

#ifdef __SWITCH__
void sqlite_switch_prepare(void);
#endif

static const char *exc_class(int rc) {
    switch (rc & 0xff) {
    case SQLITE_CONSTRAINT: return "Landroid/database/sqlite/SQLiteConstraintException;";
    case SQLITE_IOERR: return "Landroid/database/sqlite/SQLiteDiskIOException;";
    case SQLITE_CORRUPT:
    case SQLITE_NOTADB: return "Landroid/database/sqlite/SQLiteDatabaseCorruptException;";
    case SQLITE_FULL: return "Landroid/database/sqlite/SQLiteFullException;";
    case SQLITE_CANTOPEN: return "Landroid/database/sqlite/SQLiteCantOpenDatabaseException;";
    case SQLITE_READONLY: return "Landroid/database/sqlite/SQLiteReadOnlyDatabaseException;";
    case SQLITE_NOMEM: return "Landroid/database/sqlite/SQLiteOutOfMemoryException;";
    case SQLITE_BUSY: return "Landroid/database/sqlite/SQLiteDatabaseLockedException;";
    case SQLITE_LOCKED: return "Landroid/database/sqlite/SQLiteTableLockedException;";
    case SQLITE_MISUSE: return "Landroid/database/sqlite/SQLiteMisuseException;";
    case SQLITE_ABORT: return "Landroid/database/sqlite/SQLiteAbortException;";
    case SQLITE_PERM: return "Landroid/database/sqlite/SQLiteAccessPermException;";
    case SQLITE_RANGE: return "Landroid/database/sqlite/SQLiteBindOrColumnIndexOutOfRangeException;";
    case SQLITE_TOOBIG: return "Landroid/database/sqlite/SQLiteBlobTooBigException;";
    case SQLITE_MISMATCH: return "Landroid/database/sqlite/SQLiteDatatypeMismatchException;";
    default: return "Landroid/database/sqlite/SQLiteException;";
    }
}

static void throw_db(VMThread *t, sqlite3 *db, int rc) {
    char buf[512];
    const char *msg = db ? sqlite3_errmsg(db) : sqlite3_errstr(rc);
    snprintf(buf, sizeof buf, "%s", msg && msg[0] ? msg : "sqlite error");
    vm_throw_new(t, exc_class(rc), "%s", buf);
}

static void throw_stmt(VMThread *t, sqlite3_stmt *stmt, int rc) {
    throw_db(t, stmt ? sqlite3_db_handle(stmt) : NULL, rc);
}

static sqlite3 *as_db(VMThread *t, int64_t handle) {
    if (!handle) {
        vm_throw_new(t, "Landroid/database/sqlite/SQLiteMisuseException;", "database is closed");
        return NULL;
    }
    return (sqlite3 *)(intptr_t)handle;
}

static sqlite3_stmt *as_stmt(VMThread *t, int64_t handle) {
    if (!handle) {
        vm_throw_new(t, "Landroid/database/sqlite/SQLiteMisuseException;", "statement is closed");
        return NULL;
    }
    return (sqlite3_stmt *)(intptr_t)handle;
}

static int column_ok(VMThread *t, sqlite3_stmt *stmt, int index) {
    int n = sqlite3_column_count(stmt);
    if (index < 0 || index >= n) {
        vm_throw_new(t, "Landroid/database/sqlite/SQLiteBindOrColumnIndexOutOfRangeException;",
                     "column index %d out of range, count %d", index, n);
        return 0;
    }
    return 1;
}

/* static native long nOpen(String path, int flags) */
NATIVE(SQLite_nOpen) {
    char *arg = nat_str(A_OBJ(0));
    int flags = A_INT(1);
    if (!arg) {
        vm_throw_new(t, "Landroid/database/sqlite/SQLiteException;", "path is null");
        R_LONG(0);
        return;
    }
    char *path;
    if (strcmp(arg, ":memory:") == 0) path = sa_strdup(arg);
    else path = platform_map_path(arg);
    free(arg);
    if (!path) {
        vm_throw_new(t, "Landroid/database/sqlite/SQLiteCantOpenDatabaseException;", "path is empty");
        R_LONG(0);
        return;
    }
    int oflags;
    if (flags & OPEN_READONLY) oflags = SQLITE_OPEN_READONLY;
    else {
        oflags = SQLITE_OPEN_READWRITE;
        if (flags & CREATE_IF_NECESSARY) oflags |= SQLITE_OPEN_CREATE;
    }
#ifdef __SWITCH__
    sqlite_switch_prepare();
#endif
    sqlite3 *db = NULL;
    vm_gil_release(t);
    int rc = sqlite3_open_v2(path, &db, oflags, NULL);
    if (rc == SQLITE_OK && (flags & ENABLE_WRITE_AHEAD_LOGGING)) {
        /* WAL is optional. A build that omits it keeps the rollback journal. */
        sqlite3_exec(db, "PRAGMA journal_mode=WAL;", NULL, NULL, NULL);
    }
    vm_gil_acquire(t);
    free(path);
    if (rc != SQLITE_OK) {
        char buf[512];
        const char *msg = db ? sqlite3_errmsg(db) : sqlite3_errstr(rc);
        snprintf(buf, sizeof buf, "%s", msg && msg[0] ? msg : "cannot open database");
        if (db) sqlite3_close(db);
        vm_throw_new(t, exc_class(rc), "%s", buf);
        R_LONG(0);
        return;
    }
    R_LONG((int64_t)(intptr_t)db);
}

/* static native void nClose(long db) */
NATIVE(SQLite_nClose) {
    int64_t handle = A_LONG(0);
    if (!handle) return;
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    vm_gil_release(t);
    int rc = sqlite3_close_v2(db);
    vm_gil_acquire(t);
    if (rc != SQLITE_OK) throw_db(t, NULL, rc);
}

/* static native long nPrepare(long db, String sql) */
NATIVE(SQLite_nPrepare) {
    sqlite3 *db = as_db(t, A_LONG(0));
    if (!db) {
        R_LONG(0);
        return;
    }
    char *sql = nat_str(A_OBJ(2));
    if (!sql) {
        vm_throw_new(t, "Landroid/database/sqlite/SQLiteException;", "sql is null");
        R_LONG(0);
        return;
    }
    sqlite3_stmt *stmt = NULL;
    vm_gil_release(t);
    int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    vm_gil_acquire(t);
    free(sql);
    if (rc != SQLITE_OK) {
        throw_db(t, db, rc);
        R_LONG(0);
        return;
    }
    R_LONG((int64_t)(intptr_t)stmt);
}

/* static native void nFinalize(long stmt) */
NATIVE(SQLite_nFinalize) {
    int64_t handle = A_LONG(0);
    if (!handle) return;
    sqlite3_stmt *stmt = (sqlite3_stmt *)(intptr_t)handle;
    vm_gil_release(t);
    sqlite3_finalize(stmt);
    vm_gil_acquire(t);
}

static int checked_bind(VMThread *t, sqlite3_stmt *stmt, int rc) {
    if (rc == SQLITE_OK) return 1;
    throw_stmt(t, stmt, rc);
    return 0;
}

/* static native void nBindNull(long stmt, int index) */
NATIVE(SQLite_nBindNull) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    checked_bind(t, stmt, sqlite3_bind_null(stmt, A_INT(2)));
}

/* static native void nBindLong(long stmt, int index, long value) */
NATIVE(SQLite_nBindLong) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    checked_bind(t, stmt, sqlite3_bind_int64(stmt, A_INT(2), A_LONG(3)));
}

/* static native void nBindDouble(long stmt, int index, double value) */
NATIVE(SQLite_nBindDouble) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    checked_bind(t, stmt, sqlite3_bind_double(stmt, A_INT(2), A_DOUBLE(3)));
}

/* static native void nBindString(long stmt, int index, String value) */
NATIVE(SQLite_nBindString) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    char *s = nat_str(A_OBJ(3));
    int rc = s ? sqlite3_bind_text(stmt, A_INT(2), s, -1, SQLITE_TRANSIENT) : sqlite3_bind_null(stmt, A_INT(2));
    free(s);
    checked_bind(t, stmt, rc);
}

/* static native void nBindBlob(long stmt, int index, byte[] value) */
NATIVE(SQLite_nBindBlob) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    ArrayObject *a = A_ARR(3);
    int rc;
    if (!a) rc = sqlite3_bind_null(stmt, A_INT(2));
    else rc = sqlite3_bind_blob(stmt, A_INT(2), a->length ? ARRAY_DATA(a, uint8_t) : NULL, a->length, SQLITE_TRANSIENT);
    checked_bind(t, stmt, rc);
}

/* static native void nReset(long stmt) */
NATIVE(SQLite_nReset) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    checked_bind(t, stmt, sqlite3_reset(stmt));
}

/* static native void nClearBindings(long stmt) */
NATIVE(SQLite_nClearBindings) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) return;
    checked_bind(t, stmt, sqlite3_clear_bindings(stmt));
}

/* static native int nStep(long stmt) */
NATIVE(SQLite_nStep) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt) {
        R_INT(SQLITE_MISUSE);
        return;
    }
    vm_gil_release(t);
    int rc = sqlite3_step(stmt);
    vm_gil_acquire(t);
    if (rc != SQLITE_ROW && rc != SQLITE_DONE) throw_stmt(t, stmt, rc);
    R_INT(rc);
}

/* static native int nBindParameterCount(long stmt) */
NATIVE(SQLite_nBindParameterCount) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    R_INT(stmt ? sqlite3_bind_parameter_count(stmt) : 0);
}

/* static native int nColumnCount(long stmt) */
NATIVE(SQLite_nColumnCount) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    R_INT(stmt ? sqlite3_column_count(stmt) : 0);
}

/* static native String nColumnName(long stmt, int i) */
NATIVE(SQLite_nColumnName) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    if (!stmt || !column_ok(t, stmt, A_INT(2))) {
        R_OBJ(NULL);
        return;
    }
    /* Copy before allocating: a GC during the Java string build must not
       depend on the statement's column pointer. */
    const char *name = sqlite3_column_name(stmt, A_INT(2));
    char *copy = name ? sa_strdup(name) : NULL;
    Object *s = copy ? vm_new_string_utf8(t, copy) : NULL;
    free(copy);
    R_OBJ(s);
}

/* static native int nColumnType(long stmt, int i) */
NATIVE(SQLite_nColumnType) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    int index = A_INT(2);
    if (!stmt || !column_ok(t, stmt, index)) {
        R_INT(SQLITE_NULL);
        return;
    }
    /* SQLITE_INTEGER=1, FLOAT=2, TEXT=3, BLOB=4, NULL=5. Java switches on these. */
    R_INT(sqlite3_column_type(stmt, index));
}

/* static native long nColumnLong(long stmt, int i) */
NATIVE(SQLite_nColumnLong) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    int index = A_INT(2);
    if (!stmt || !column_ok(t, stmt, index)) {
        R_LONG(0);
        return;
    }
    R_LONG(sqlite3_column_int64(stmt, index));
}

/* static native double nColumnDouble(long stmt, int i) */
NATIVE(SQLite_nColumnDouble) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    int index = A_INT(2);
    if (!stmt || !column_ok(t, stmt, index)) {
        R_DOUBLE(0);
        return;
    }
    R_DOUBLE(sqlite3_column_double(stmt, index));
}

/* static native String nColumnText(long stmt, int i) */
NATIVE(SQLite_nColumnText) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    int index = A_INT(2);
    if (!stmt || !column_ok(t, stmt, index) || sqlite3_column_type(stmt, index) == SQLITE_NULL) {
        R_OBJ(NULL);
        return;
    }
    const char *text = (const char *)sqlite3_column_text(stmt, index);
    char *copy = text ? sa_strdup(text) : NULL;
    Object *s = copy ? vm_new_string_utf8(t, copy) : NULL;
    free(copy);
    R_OBJ(s);
}

/* static native byte[] nColumnBlob(long stmt, int i) */
NATIVE(SQLite_nColumnBlob) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    int index = A_INT(2);
    if (!stmt || !column_ok(t, stmt, index) || sqlite3_column_type(stmt, index) == SQLITE_NULL) {
        R_OBJ(NULL);
        return;
    }
    int n = sqlite3_column_bytes(stmt, index);
    const void *p = n > 0 ? sqlite3_column_blob(stmt, index) : NULL;
    void *copy = NULL;
    if (n > 0 && p) {
        copy = malloc((size_t)n);
        if (!copy) {
            vm_throw_new(t, "Landroid/database/sqlite/SQLiteOutOfMemoryException;", "blob copy");
            R_OBJ(NULL);
            return;
        }
        memcpy(copy, p, (size_t)n);
    }
    ArrayObject *a = vm_alloc_prim_array(t, 'B', n);
    if (!a) {
        free(copy);
        R_OBJ(NULL);
        return;
    }
    if (copy) memcpy(ARRAY_DATA(a, uint8_t), copy, (size_t)n);
    free(copy);
    R_OBJ(a);
}

/* static native long nLastInsertRowId(long db) */
NATIVE(SQLite_nLastInsertRowId) {
    sqlite3 *db = as_db(t, A_LONG(0));
    R_LONG(db ? sqlite3_last_insert_rowid(db) : 0);
}

/* static native int nChanges(long db) */
NATIVE(SQLite_nChanges) {
    sqlite3 *db = as_db(t, A_LONG(0));
    R_INT(db ? sqlite3_changes(db) : 0);
}

/* static native boolean nIsReadOnly(long stmt) */
NATIVE(SQLite_nIsReadOnly) {
    sqlite3_stmt *stmt = as_stmt(t, A_LONG(0));
    R_BOOL(stmt && sqlite3_stmt_readonly(stmt));
}

static const NativeMethodReg g_regs[] = {
    {"Landroid/database/sqlite/SQLiteNative;", "nOpen", "(Ljava/lang/String;I)J", SQLite_nOpen},
    {"Landroid/database/sqlite/SQLiteNative;", "nClose", "(J)V", SQLite_nClose},
    {"Landroid/database/sqlite/SQLiteNative;", "nPrepare", "(JLjava/lang/String;)J", SQLite_nPrepare},
    {"Landroid/database/sqlite/SQLiteNative;", "nFinalize", "(J)V", SQLite_nFinalize},
    {"Landroid/database/sqlite/SQLiteNative;", "nBindNull", "(JI)V", SQLite_nBindNull},
    {"Landroid/database/sqlite/SQLiteNative;", "nBindLong", "(JIJ)V", SQLite_nBindLong},
    {"Landroid/database/sqlite/SQLiteNative;", "nBindDouble", "(JID)V", SQLite_nBindDouble},
    {"Landroid/database/sqlite/SQLiteNative;", "nBindString", "(JILjava/lang/String;)V", SQLite_nBindString},
    {"Landroid/database/sqlite/SQLiteNative;", "nBindBlob", "(JI[B)V", SQLite_nBindBlob},
    {"Landroid/database/sqlite/SQLiteNative;", "nReset", "(J)V", SQLite_nReset},
    {"Landroid/database/sqlite/SQLiteNative;", "nClearBindings", "(J)V", SQLite_nClearBindings},
    {"Landroid/database/sqlite/SQLiteNative;", "nStep", "(J)I", SQLite_nStep},
    {"Landroid/database/sqlite/SQLiteNative;", "nBindParameterCount", "(J)I", SQLite_nBindParameterCount},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnCount", "(J)I", SQLite_nColumnCount},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnName", "(JI)Ljava/lang/String;", SQLite_nColumnName},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnType", "(JI)I", SQLite_nColumnType},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnLong", "(JI)J", SQLite_nColumnLong},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnDouble", "(JI)D", SQLite_nColumnDouble},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnText", "(JI)Ljava/lang/String;", SQLite_nColumnText},
    {"Landroid/database/sqlite/SQLiteNative;", "nColumnBlob", "(JI)[B", SQLite_nColumnBlob},
    {"Landroid/database/sqlite/SQLiteNative;", "nLastInsertRowId", "(J)J", SQLite_nLastInsertRowId},
    {"Landroid/database/sqlite/SQLiteNative;", "nChanges", "(J)I", SQLite_nChanges},
    {"Landroid/database/sqlite/SQLiteNative;", "nIsReadOnly", "(J)Z", SQLite_nIsReadOnly},
};

void android_sqlite_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
