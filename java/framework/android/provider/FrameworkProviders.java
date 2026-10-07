package android.provider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs the in-process settings and media providers. Settings persist under
 * the shared data root so a second host run sees them. Media rows live for the
 * process. framework-internal.
 */
public final class FrameworkProviders {
    private FrameworkProviders() {}

    public static void install(Context context) {
        installOne(context, "settings", new SettingsProvider());
        installOne(context, "media", new MediaProvider());
    }

    private static void installOne(Context context, String authority, ContentProvider provider) {
        ProviderInfo info = new ProviderInfo();
        info.authority = authority;
        provider.attachInfo(context, info);
        ContentResolver.installProvider(authority, provider);
    }

    /** content://settings/{system|secure|global} name/value rows. */
    static final class SettingsProvider extends ContentProvider {
        private static final String DIR = "/data/local/tmp/settings";
        private final HashMap<String, HashMap<String, String>> mTables = new HashMap<String, HashMap<String, String>>();

        @Override public boolean onCreate() { return true; }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            String table = tableOf(uri);
            String name = nameOf(uri, selection, selectionArgs);
            HashMap<String, String> rows = rows(table);
            String[] cols = projection != null && projection.length > 0 ? projection
                    : new String[] { Settings.NameValueTable.NAME, Settings.NameValueTable.VALUE };
            MatrixCursor cursor = new MatrixCursor(cols);
            synchronized (rows) {
                if (name != null) {
                    if (rows.containsKey(name)) add(cursor, cols, name, rows.get(name));
                } else {
                    for (Map.Entry<String, String> e : rows.entrySet()) add(cursor, cols, e.getKey(), e.getValue());
                }
            }
            return cursor;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            if (values == null) return null;
            String name = values.getAsString(Settings.NameValueTable.NAME);
            if (name == null) return null;
            String value = values.getAsString(Settings.NameValueTable.VALUE);
            String table = tableOf(uri);
            HashMap<String, String> rows = rows(table);
            synchronized (rows) {
                if (value == null) rows.remove(name);
                else rows.put(name, value);
                save(table, rows);
            }
            return Uri.withAppendedPath(uri, name);
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            String name = nameOf(uri, selection, selectionArgs);
            if (name == null) return 0;
            HashMap<String, String> rows = rows(tableOf(uri));
            synchronized (rows) {
                if (rows.remove(name) == null) return 0;
                save(tableOf(uri), rows);
                return 1;
            }
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            Uri inserted = insert(uri, values);
            return inserted == null ? 0 : 1;
        }

        @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/setting"; }

        private static void add(MatrixCursor cursor, String[] cols, String name, String value) {
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (Settings.NameValueTable.NAME.equals(cols[i])) row[i] = name;
                else if (Settings.NameValueTable.VALUE.equals(cols[i])) row[i] = value;
                else if (BaseColumns._ID.equals(cols[i])) row[i] = Integer.valueOf(name.hashCode());
            }
            cursor.addRow(row);
        }

        private String tableOf(Uri uri) {
            List<String> segs = uri.getPathSegments();
            if (segs.isEmpty()) return "system";
            String table = segs.get(0);
            if ("secure".equals(table) || "global".equals(table) || "system".equals(table)) return table;
            return "system";
        }

        private String nameOf(Uri uri, String selection, String[] selectionArgs) {
            List<String> segs = uri.getPathSegments();
            if (segs.size() >= 2) return segs.get(1);
            if (selectionArgs != null && selectionArgs.length > 0 && selection != null && selection.indexOf('?') >= 0) {
                return selectionArgs[0];
            }
            return null;
        }

        private HashMap<String, String> rows(String table) {
            synchronized (mTables) {
                HashMap<String, String> map = mTables.get(table);
                if (map == null) {
                    map = load(table);
                    mTables.put(table, map);
                }
                return map;
            }
        }

        private static HashMap<String, String> load(String table) {
            HashMap<String, String> map = new HashMap<String, String>();
            File file = new File(DIR, table);
            if (!file.exists()) return map;
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;
                        map.put(URLDecoder.decode(line.substring(0, eq), "UTF-8"),
                                URLDecoder.decode(line.substring(eq + 1), "UTF-8"));
                    }
                } finally {
                    in.close();
                }
            } catch (Exception ignored) {}
            return map;
        }

        private static void save(String table, HashMap<String, String> rows) {
            try {
                File dir = new File(DIR);
                if (!dir.exists()) dir.mkdirs();
                PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, table)), "UTF-8"));
                try {
                    for (Map.Entry<String, String> e : rows.entrySet()) {
                        out.print(URLEncoder.encode(e.getKey(), "UTF-8"));
                        out.print('=');
                        out.println(URLEncoder.encode(e.getValue(), "UTF-8"));
                    }
                } finally {
                    out.close();
                }
            } catch (Exception ignored) {}
        }
    }

    /** content://media rows for this process. A query with no inserts is empty. */
    static final class MediaProvider extends ContentProvider {
        private final ArrayList<ContentValues> mRows = new ArrayList<ContentValues>();
        private long mNextId = 1;

        @Override public boolean onCreate() { return true; }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            return query(uri, projection, selection, selectionArgs, sortOrder, null);
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder,
                CancellationSignal cancellationSignal) {
            String[] cols = projection != null && projection.length > 0 ? projection
                    : new String[] { BaseColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA,
                            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE };
            MatrixCursor cursor = new MatrixCursor(cols);
            Long only = lastId(uri);
            String collection = collectionOf(uri);
            synchronized (mRows) {
                for (int i = 0; i < mRows.size(); i++) {
                    ContentValues row = mRows.get(i);
                    if (only != null && !only.equals(row.getAsLong(BaseColumns._ID))) continue;
                    if (only == null && collection != null && !collection.equals(row.getAsString("_collection"))) continue;
                    Object[] values = new Object[cols.length];
                    for (int c = 0; c < cols.length; c++) values[c] = row.get(cols[c]);
                    cursor.addRow(values);
                }
            }
            return cursor;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            ContentValues row = values == null ? new ContentValues() : new ContentValues(values);
            long id;
            synchronized (mRows) {
                id = mNextId++;
                row.put(BaseColumns._ID, Long.valueOf(id));
                row.put("_collection", collectionOf(uri));
                mRows.add(row);
            }
            return Uri.withAppendedPath(uri, String.valueOf(id));
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            Long only = lastId(uri);
            if (only == null) return 0;
            synchronized (mRows) {
                for (int i = 0; i < mRows.size(); i++) {
                    if (only.equals(mRows.get(i).getAsLong(BaseColumns._ID))) {
                        mRows.remove(i);
                        return 1;
                    }
                }
            }
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            Long only = lastId(uri);
            if (only == null || values == null) return 0;
            synchronized (mRows) {
                for (int i = 0; i < mRows.size(); i++) {
                    ContentValues row = mRows.get(i);
                    if (!only.equals(row.getAsLong(BaseColumns._ID))) continue;
                    row.putAll(values);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public String getType(Uri uri) {
            String path = uri.getPath();
            if (path != null && path.contains("/images/")) return "vnd.android.cursor.dir/image";
            if (path != null && path.contains("/video/")) return "vnd.android.cursor.dir/video";
            if (path != null && path.contains("/audio/")) return "vnd.android.cursor.dir/audio";
            return "vnd.android.cursor.dir/media";
        }

        private static String collectionOf(Uri uri) {
            String path = uri.getEncodedPath();
            if (path == null) return "";
            Long id = lastId(uri);
            if (id != null && path.endsWith("/" + id)) path = path.substring(0, path.length() - (1 + String.valueOf(id).length()));
            return path;
        }

        private static Long lastId(Uri uri) {
            String last = uri.getLastPathSegment();
            if (last == null) return null;
            try {
                return Long.valueOf(last);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
