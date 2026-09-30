package android.content.res;

import android.util.TypedValue;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Access to the application's assets and the merged (framework + app)
 * resource table. There is exactly one table per process.
 */
public final class AssetManager implements AutoCloseable {
    public static final int ACCESS_UNKNOWN = 0;
    public static final int ACCESS_RANDOM = 1;
    public static final int ACCESS_STREAMING = 2;
    public static final int ACCESS_BUFFER = 3;

    static final int COOKIE_FRAMEWORK = 1;
    static final int COOKIE_APP = 2;

    private static AssetManager sSystem;
    private final HashMap<Integer, Bag> mBags = new HashMap<Integer, Bag>();
    private final HashMap<String, Object[]> mXmlCache = new HashMap<String, Object[]>();

    public AssetManager() {}

    public static AssetManager getSystem() {
        synchronized (AssetManager.class) {
            if (sSystem == null) sSystem = new AssetManager();
            return sSystem;
        }
    }

    public void close() {}

    public int addAssetPath(String path) { return COOKIE_APP; }

    public final String[] getLocales() { return nGetLocales(); }
    public final String[] getNonSystemLocales() { return nGetLocales(); }

    public final InputStream open(String fileName) throws IOException { return open(fileName, ACCESS_STREAMING); }

    public final InputStream open(String fileName, int accessMode) throws IOException {
        byte[] data = nOpenAsset(fileName);
        if (data == null) throw new FileNotFoundException(fileName);
        return new AssetInputStream(data);
    }

    public final AssetFileDescriptor openFd(String fileName) throws IOException {
        long[] info = new long[2];
        if (!nAssetInfo(fileName, info)) throw new FileNotFoundException(fileName);
        return new AssetFileDescriptor(this, fileName, info[0], info[1]);
    }

    public final String[] list(String path) throws IOException {
        String[] r = nList(path == null ? "" : path);
        return r == null ? new String[0] : r;
    }

    public final InputStream openNonAssetFd(String fileName) throws IOException { return openNonAsset(0, fileName); }
    public final InputStream openNonAsset(String fileName) throws IOException { return openNonAsset(0, fileName); }
    public final InputStream openNonAsset(int cookie, String fileName) throws IOException { return openNonAsset(cookie, fileName, ACCESS_STREAMING); }
    public final InputStream openNonAsset(String fileName, int accessMode) throws IOException { return openNonAsset(0, fileName, accessMode); }

    public final InputStream openNonAsset(int cookie, String fileName, int accessMode) throws IOException {
        byte[] data = nOpenNonAsset(cookie, fileName);
        if (data == null) throw new FileNotFoundException(fileName);
        return new AssetInputStream(data);
    }

    public final XmlResourceParser openXmlResourceParser(String fileName) throws IOException { return openXmlResourceParser(0, fileName); }

    public final XmlResourceParser openXmlResourceParser(int cookie, String fileName) throws IOException {
        XmlBlock block = openXmlBlock(cookie, fileName);
        if (block == null) throw new FileNotFoundException(fileName);
        return block.newParser();
    }

    XmlBlock openXmlBlock(int cookie, String fileName) {
        String key = cookie + ":" + fileName;
        Object[] data;
        synchronized (mXmlCache) {
            data = mXmlCache.get(key);
        }
        if (data == null) {
            data = nOpenXml(cookie, fileName);
            if (data == null) return null;
            synchronized (mXmlCache) {
                if (mXmlCache.size() > 256) mXmlCache.clear();
                mXmlCache.put(key, data);
            }
        }
        return new XmlBlock((int[]) data[0], (int[]) data[1], (String[]) data[2]);
    }

    /** Parses a binary (or text) XML document held in memory. */
    public static XmlBlock parseXml(byte[] bytes) {
        Object[] data = nParseXmlBytes(bytes);
        if (data == null) return null;
        return new XmlBlock((int[]) data[0], (int[]) data[1], (String[]) data[2]);
    }

    public final boolean getResourceValue(int resId, int densityDpi, TypedValue outValue, boolean resolveRefs) {
        return nGetValue(resId, outValue, resolveRefs);
    }

    final boolean resolveReference(int resId, TypedValue outValue) { return nResolveReference(resId, outValue); }

    public final String getResourceName(int resId) { return nGetResourceName(resId); }

    public final String getResourcePackageName(int resId) {
        String n = nGetResourceName(resId);
        if (n == null) return null;
        int c = n.indexOf(':');
        return c < 0 ? null : n.substring(0, c);
    }

    public final String getResourceTypeName(int resId) {
        String n = nGetResourceName(resId);
        if (n == null) return null;
        int c = n.indexOf(':'), s = n.indexOf('/');
        return c < 0 || s < 0 ? null : n.substring(c + 1, s);
    }

    public final String getResourceEntryName(int resId) {
        String n = nGetResourceName(resId);
        if (n == null) return null;
        int s = n.indexOf('/');
        return s < 0 ? null : n.substring(s + 1);
    }

    public final int getResourceIdentifier(String name, String defType, String defPackage) {
        return nGetIdentifier(name, defType, defPackage);
    }

    static String appPackageName() { return nGetAppPackageName(); }

    /** A flattened style/array/plurals bag, sorted by attribute id. */
    static final class Bag {
        final int[] attrs;
        final int[] types;
        final int[] datas;
        final String[] strings;

        Bag(int n) {
            attrs = new int[n];
            types = new int[n];
            datas = new int[n];
            strings = new String[n];
        }

        int indexOf(int attr) {
            return Arrays.binarySearch(attrs, attr);
        }

        int size() { return attrs.length; }
    }

    /** Bag entries in declaration order (arrays depend on it). */
    Bag getBagOrdered(int resId) {
        Object[] raw = nGetBag(resId);
        if (raw == null) return null;
        int[] ints = (int[]) raw[0];
        String[] strs = (String[]) raw[1];
        int n = ints.length / 3;
        Bag b = new Bag(n);
        for (int i = 0; i < n; i++) {
            b.attrs[i] = ints[i * 3];
            b.types[i] = ints[i * 3 + 1];
            b.datas[i] = ints[i * 3 + 2];
            b.strings[i] = strs[i];
        }
        return b;
    }

    Bag getStyleBag(int resId) {
        synchronized (mBags) {
            Bag cached = mBags.get(resId);
            if (cached != null || mBags.containsKey(resId)) return cached;
        }
        Bag ordered = getBagOrdered(resId);
        Bag sorted = null;
        if (ordered != null) {
            int n = ordered.size();
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            final int[] a = ordered.attrs;
            Arrays.sort(idx, new java.util.Comparator<Integer>() {
                public int compare(Integer x, Integer y) { return Integer.compare(a[x], a[y]); }
            });
            sorted = new Bag(n);
            for (int i = 0; i < n; i++) {
                int j = idx[i];
                sorted.attrs[i] = ordered.attrs[j];
                sorted.types[i] = ordered.types[j];
                sorted.datas[i] = ordered.datas[j];
                sorted.strings[i] = ordered.strings[j];
            }
        }
        synchronized (mBags) {
            mBags.put(resId, sorted);
        }
        return sorted;
    }

    int getStyleParent(int resId) { return nGetStyleParent(resId); }

    void setConfiguration(int density, int widthDp, int heightDp, int swDp, int orientation, int night, int sdk,
            String lang, String country, int uiModeType) {
        nSetConfiguration(density, widthDp, heightDp, swDp, orientation, night, sdk, lang, country, uiModeType);
        synchronized (mBags) {
            mBags.clear();
        }
    }

    public static String getApkPath() { return nApkPath(); }

    public static final class AssetInputStream extends ByteArrayInputStream {
        AssetInputStream(byte[] data) { super(data); }
        public final int getAssetInt() { return 0; }
        public final long getNativeAsset() { return 0; }
        byte[] buffer() { return buf; }
    }

    static native boolean nGetValue(int id, TypedValue out, boolean resolveRefs);
    static native boolean nResolveReference(int id, TypedValue out);
    static native Object[] nGetBag(int id);
    static native int nGetStyleParent(int id);
    static native String nGetResourceName(int id);
    static native int nGetIdentifier(String name, String type, String pkg);
    static native String nGetAppPackageName();
    static native void nSetConfiguration(int density, int wdp, int hdp, int swdp, int orientation, int night, int sdk,
            String lang, String country, int uiModeType);
    static native byte[] nOpenAsset(String name);
    static native byte[] nOpenNonAsset(int cookie, String path);
    static native boolean nAssetInfo(String name, long[] out);
    static native String[] nList(String dir);
    static native Object[] nOpenXml(int cookie, String path);
    static native Object[] nParseXmlBytes(byte[] data);
    static native String[] nGetLocales();
    static native String nApkPath();
}
