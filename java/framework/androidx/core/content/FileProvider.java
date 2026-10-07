package androidx.core.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Serves files under configured roots as content URIs. This is the AndroidX
 * class: the framework copy is used when an app does not bring its own, and
 * it matches the paths XML and {@code getUriForFile} contract apps compile against.
 */
public class FileProvider extends ContentProvider {
    public static final String META_DATA_FILE_PROVIDER_PATHS = "android.support.FILE_PROVIDER_PATHS";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PATH = "path";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String DISPLAY_NAME = "displayName";
    private static final File DEVICE_ROOT = new File("/");
    private static final HashMap<String, PathStrategy> sCache = new HashMap<String, PathStrategy>();

    private PathStrategy mStrategy;

    public FileProvider() {}

    @Override
    public boolean onCreate() { return true; }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info.exported) throw new SecurityException("Provider must not be exported");
        if (!info.grantUriPermissions) throw new SecurityException("Provider must grant uri permissions");
        String authority = info.authority;
        if (authority != null && authority.indexOf(';') >= 0) authority = authority.split(";")[0].trim();
        mStrategy = getPathStrategy(context, authority);
    }

    public static Uri getUriForFile(Context context, String authority, File file) {
        return getPathStrategy(context, authority).getUriForFile(file);
    }

    public static Uri getUriForFile(Context context, String authority, File file, String displayName) {
        Uri uri = getUriForFile(context, authority, file);
        return uri.buildUpon().appendQueryParameter(DISPLAY_NAME, displayName).build();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = mStrategy.getFileForUri(uri);
        String[] cols = projection != null ? projection
                : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        String display = uri.getQueryParameter(DISPLAY_NAME);
        if (display == null) display = file.getName();
        Object[] values = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) values[i] = display;
            else if (OpenableColumns.SIZE.equals(cols[i])) values[i] = Long.valueOf(file.length());
        }
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = mStrategy.getFileForUri(uri);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external deletes");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = mStrategy.getFileForUri(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    private static PathStrategy getPathStrategy(Context context, String authority) {
        synchronized (sCache) {
            PathStrategy strat = sCache.get(authority);
            if (strat == null) {
                try {
                    strat = parsePathStrategy(context, authority);
                } catch (IOException e) {
                    throw new IllegalArgumentException("Failed to parse paths for " + authority, e);
                } catch (XmlPullParserException e) {
                    throw new IllegalArgumentException("Failed to parse paths for " + authority, e);
                }
                sCache.put(authority, strat);
            }
            return strat;
        }
    }

    private static PathStrategy parsePathStrategy(Context context, String authority) throws IOException, XmlPullParserException {
        SimplePathStrategy strat = new SimplePathStrategy(authority);
        ProviderInfo info = context.getPackageManager().resolveContentProvider(authority, 128);
        if (info == null) throw new IllegalArgumentException("Couldn't find meta-data for provider with authority " + authority);
        XmlResourceParser in = info.loadXmlMetaData(context.getPackageManager(), META_DATA_FILE_PROVIDER_PATHS);
        if (in == null) {
            throw new IllegalArgumentException("Missing " + META_DATA_FILE_PROVIDER_PATHS + " meta-data");
        }
        try {
            int type;
            while ((type = in.next()) != XmlResourceParser.END_DOCUMENT) {
                if (type != XmlResourceParser.START_TAG) continue;
                String tag = in.getName();
                String name = attr(in, ATTR_NAME);
                String path = attr(in, ATTR_PATH);
                File target = null;
                if ("root-path".equals(tag)) target = DEVICE_ROOT;
                else if ("files-path".equals(tag)) target = context.getFilesDir();
                else if ("cache-path".equals(tag)) target = context.getCacheDir();
                else if ("external-path".equals(tag)) target = Environment.getExternalStorageDirectory();
                else if ("external-files-path".equals(tag)) target = context.getExternalFilesDir(null);
                else if ("external-cache-path".equals(tag)) target = context.getExternalCacheDir();
                else if ("external-media-path".equals(tag)) {
                    File[] media = context.getExternalMediaDirs();
                    if (media.length > 0) target = media[0];
                }
                if (target != null) strat.addRoot(name, buildPath(target, path));
            }
        } finally {
            in.close();
        }
        return strat;
    }

    /** name and path are android: attributes in some APKs and bare attributes in others. */
    private static String attr(XmlResourceParser in, String name) {
        String value = in.getAttributeValue(ANDROID_NS, name);
        if (value != null) return value;
        value = in.getAttributeValue(null, name);
        if (value != null) return value;
        value = in.getAttributeValue("", name);
        if (value != null) return value;
        int n = in.getAttributeCount();
        for (int i = 0; i < n; i++) {
            if (name.equals(in.getAttributeName(i))) return in.getAttributeValue(i);
        }
        return null;
    }

    private static File buildPath(File base, String segment) {
        if (segment == null || segment.isEmpty() || ".".equals(segment)) return base;
        return new File(base, segment);
    }

    interface PathStrategy {
        Uri getUriForFile(File file);
        File getFileForUri(Uri uri);
    }

    static class SimplePathStrategy implements PathStrategy {
        private final String mAuthority;
        private final HashMap<String, File> mRoots = new HashMap<String, File>();

        SimplePathStrategy(String authority) { mAuthority = authority; }

        void addRoot(String name, File root) {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("Name must not be empty");
            try {
                mRoots.put(name, root.getCanonicalFile());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + root, e);
            }
        }

        public Uri getUriForFile(File file) {
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + file);
            }
            Map.Entry<String, File> most = null;
            for (Map.Entry<String, File> root : mRoots.entrySet()) {
                String rootPath = root.getValue().getPath();
                if (!contains(rootPath, path)) continue;
                if (most == null || rootPath.length() > most.getValue().getPath().length()) most = root;
            }
            if (most == null) {
                throw new IllegalArgumentException("Failed to find configured root that contains " + path);
            }
            String rootPath = most.getValue().getPath();
            String relative = path.length() > rootPath.length() ? path.substring(rootPath.length() + 1) : "";
            Uri.Builder builder = new Uri.Builder().scheme("content").authority(mAuthority).appendPath(most.getKey());
            if (!relative.isEmpty()) {
                String[] segs = relative.split("/");
                for (int i = 0; i < segs.length; i++) {
                    if (!segs[i].isEmpty()) builder.appendPath(segs[i]);
                }
            }
            return builder.build();
        }

        public File getFileForUri(Uri uri) {
            String path = uri.getEncodedPath();
            if (path == null || path.length() < 2) throw new IllegalArgumentException("Unable to find configured root for " + uri);
            int split = path.indexOf('/', 1);
            String tag = Uri.decode(split < 0 ? path.substring(1) : path.substring(1, split));
            String relative = split < 0 ? "" : Uri.decode(path.substring(split + 1));
            File root = mRoots.get(tag);
            if (root == null) throw new IllegalArgumentException("Unable to find configured root for " + uri);
            File file = relative.isEmpty() ? root : new File(root, relative);
            try {
                file = file.getCanonicalFile();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + file);
            }
            if (!contains(root.getPath(), file.getPath())) {
                throw new SecurityException("Resolved path jumped beyond configured root");
            }
            return file;
        }

        private static boolean contains(String rootPath, String filePath) {
            if ("/".equals(rootPath)) return filePath.startsWith("/");
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + "/");
        }
    }
}
