package android.content.res;

import android.graphics.Movie;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Resources {
    static final String TAG = "Resources";
    public static final int ID_NULL = 0;

    // plurals quantity attribute ids (^other, ^zero, ^one, ^two, ^few, ^many)
    private static final int ATTR_OTHER = 0x01000004;
    private static final int ATTR_ZERO = 0x01000005;
    private static final int ATTR_ONE = 0x01000006;
    private static final int ATTR_TWO = 0x01000007;
    private static final int ATTR_FEW = 0x01000008;
    private static final int ATTR_MANY = 0x01000009;

    private static Resources sSystem;
    private static final Object sSync = new Object();

    final AssetManager mAssets;
    final DisplayMetrics mMetrics = new DisplayMetrics();
    final Configuration mConfiguration = new Configuration();
    private final TypedValue mTmpValue = new TypedValue();
    private final HashMap<Integer, Drawable.ConstantState> mDrawableCache = new HashMap<Integer, Drawable.ConstantState>();
    private final HashMap<Integer, ColorStateList> mColorCache = new HashMap<Integer, ColorStateList>();
    private final HashMap<Integer, Typeface> mFontCache = new HashMap<Integer, Typeface>();

    public static Resources getSystem() {
        synchronized (sSync) {
            if (sSystem == null) {
                DisplayMetrics dm = new DisplayMetrics();
                dm.setToDefaults();
                Configuration c = new Configuration();
                c.setToDefaults();
                sSystem = new Resources(AssetManager.getSystem(), dm, c);
            }
            return sSystem;
        }
    }

    /** Called by ActivityThread once the display is known. */
    public static void setSystem(Resources r) {
        synchronized (sSync) { sSystem = r; }
    }

    public Resources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        mAssets = assets != null ? assets : AssetManager.getSystem();
        updateConfiguration(config, metrics);
    }

    public Resources(ClassLoader classLoader) { this(AssetManager.getSystem(), getSystem().mMetrics, getSystem().mConfiguration); }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() {}
        public NotFoundException(String name) { super(name); }
        public NotFoundException(String name, Exception cause) { super(name, cause); }
    }

    public final AssetManager getAssets() { return mAssets; }
    public DisplayMetrics getDisplayMetrics() { return mMetrics; }
    public Configuration getConfiguration() { return mConfiguration; }
    public ClassLoader getClassLoader() { return Resources.class.getClassLoader(); }

    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {
        synchronized (sSync) {
            if (metrics != null) mMetrics.setTo(metrics);
            if (config != null) mConfiguration.setTo(config);
            if (mConfiguration.densityDpi != 0) {
                mMetrics.densityDpi = mConfiguration.densityDpi;
                mMetrics.density = mConfiguration.densityDpi * 0.00625f;
            }
            float fs = mConfiguration.fontScale > 0 ? mConfiguration.fontScale : 1f;
            mMetrics.scaledDensity = mMetrics.density * fs;
            Locale l = mConfiguration.getLocales().get(0);
            int night = (mConfiguration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? 2 : 1;
            int w = mConfiguration.screenWidthDp, h = mConfiguration.screenHeightDp;
            mAssets.setConfiguration(mMetrics.densityDpi, w, h, mConfiguration.smallestScreenWidthDp,
                    mConfiguration.orientation, night, android.os.Build.VERSION.SDK_INT,
                    l != null ? l.getLanguage() : "en", l != null ? l.getCountry() : "US",
                    mConfiguration.uiMode & Configuration.UI_MODE_TYPE_MASK);
            mDrawableCache.clear();
            mColorCache.clear();
        }
    }

    public void updateConfiguration(Configuration config, DisplayMetrics metrics, Object compat) { updateConfiguration(config, metrics); }

    // ---- values ---------------------------------------------------------------------------------

    public void getValue(int id, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        if (!mAssets.getResourceValue(id, 0, outValue, resolveRefs))
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id));
        resolveThemeless(outValue);
    }

    /** A value that is a theme attribute reference cannot be resolved without a theme: leave it. */
    private void resolveThemeless(TypedValue v) {}

    public void getValueForDensity(int id, int density, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        getValue(id, outValue, resolveRefs);
    }

    public void getValue(String name, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        int id = getIdentifier(name, "string", null);
        if (id != 0) {
            getValue(id, outValue, resolveRefs);
            return;
        }
        throw new NotFoundException("String resource name " + name);
    }

    public CharSequence getText(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_STRING) return value.string;
            CharSequence cs = value.coerceToString();
            if (cs != null && value.type != TypedValue.TYPE_REFERENCE) return cs;
            throw new NotFoundException("String resource ID #0x" + Integer.toHexString(id));
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public CharSequence getText(int id, CharSequence def) {
        try {
            return id != 0 ? getText(id) : def;
        } catch (NotFoundException e) {
            return def;
        }
    }

    public String getString(int id) throws NotFoundException { return getText(id).toString(); }

    public String getString(int id, Object... formatArgs) throws NotFoundException {
        final String raw = getString(id);
        Locale l = mConfiguration.getLocales().get(0);
        return String.format(l != null ? l : Locale.getDefault(), raw, formatArgs);
    }

    public Typeface getFont(int id) throws NotFoundException {
        synchronized (mFontCache) {
            Typeface cached = mFontCache.get(id);
            if (cached != null) return cached;
        }
        TypedValue value = new TypedValue();
        getValue(id, value, true);
        Typeface tf = loadFont(value, id);
        if (tf == null) throw new NotFoundException("Font resource ID #0x" + Integer.toHexString(id));
        synchronized (mFontCache) { mFontCache.put(id, tf); }
        return tf;
    }

    Typeface loadFont(TypedValue value, int id) {
        if (value.string == null) return null;
        String file = value.string.toString();
        try {
            if (file.endsWith(".xml")) {
                XmlResourceParser p = mAssets.openXmlResourceParser(value.assetCookie, file);
                int type;
                while ((type = p.next()) != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG || !"font".equals(p.getName())) continue;
                    int n = p.getAttributeCount();
                    for (int i = 0; i < n; i++) {
                        if ("font".equals(p.getAttributeName(i))) {
                            int ref = p.getAttributeResourceValue(i, 0);
                            if (ref != 0) return getFont(ref);
                        }
                    }
                }
                return null;
            }
            InputStream in = mAssets.openNonAsset(value.assetCookie, file);
            byte[] data = readAll(in);
            return Typeface.createFromBytes(data);
        } catch (Exception e) {
            Log.w(TAG, "cannot load font " + file, e);
            return null;
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        if (in instanceof AssetManager.AssetInputStream) {
            AssetManager.AssetInputStream a = (AssetManager.AssetInputStream) in;
            return a.buffer();
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    private int quantityAttr(int quantity) {
        Locale l = mConfiguration.getLocales().get(0);
        String lang = l != null ? l.getLanguage() : "en";
        switch (lang) {
            case "ja": case "zh": case "ko": case "th": case "vi": case "id":
                return ATTR_OTHER;
            case "fr": case "pt":
                return quantity == 0 || quantity == 1 ? ATTR_ONE : ATTR_OTHER;
            case "ru": case "uk": case "be": {
                int m10 = quantity % 10, m100 = quantity % 100;
                if (m10 == 1 && m100 != 11) return ATTR_ONE;
                if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return ATTR_FEW;
                return ATTR_MANY;
            }
            case "pl": {
                int m10 = quantity % 10, m100 = quantity % 100;
                if (quantity == 1) return ATTR_ONE;
                if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return ATTR_FEW;
                return ATTR_MANY;
            }
            default:
                return quantity == 1 ? ATTR_ONE : ATTR_OTHER;
        }
    }

    public CharSequence getQuantityText(int id, int quantity) throws NotFoundException {
        AssetManager.Bag bag = mAssets.getBagOrdered(id);
        if (bag == null) throw new NotFoundException("Plural resource ID #0x" + Integer.toHexString(id));
        int want = quantityAttr(quantity);
        int pick = -1, other = -1;
        for (int i = 0; i < bag.size(); i++) {
            if (bag.attrs[i] == want) pick = i;
            if (bag.attrs[i] == ATTR_OTHER) other = i;
        }
        if (pick < 0) pick = other;
        if (pick < 0 && bag.size() > 0) pick = 0;
        if (pick < 0) throw new NotFoundException("Plural resource ID #0x" + Integer.toHexString(id));
        return bagItemText(bag, pick);
    }

    private CharSequence bagItemText(AssetManager.Bag bag, int i) {
        int type = bag.types[i];
        if (type == TypedValue.TYPE_STRING) return bag.strings[i];
        if (type == TypedValue.TYPE_REFERENCE && bag.datas[i] != 0) return getText(bag.datas[i]);
        return TypedValue.coerceToString(type, bag.datas[i]);
    }

    public String getQuantityString(int id, int quantity, Object... formatArgs) throws NotFoundException {
        String raw = getQuantityText(id, quantity).toString();
        Locale l = mConfiguration.getLocales().get(0);
        return String.format(l != null ? l : Locale.getDefault(), raw, formatArgs);
    }

    public String getQuantityString(int id, int quantity) throws NotFoundException { return getQuantityText(id, quantity).toString(); }

    public CharSequence[] getTextArray(int id) throws NotFoundException {
        AssetManager.Bag bag = mAssets.getBagOrdered(id);
        if (bag == null) throw new NotFoundException("Text array resource ID #0x" + Integer.toHexString(id));
        CharSequence[] out = new CharSequence[bag.size()];
        for (int i = 0; i < out.length; i++) out[i] = bagItemText(bag, i);
        return out;
    }

    public String[] getStringArray(int id) throws NotFoundException {
        CharSequence[] cs = getTextArray(id);
        String[] out = new String[cs.length];
        for (int i = 0; i < out.length; i++) out[i] = cs[i] == null ? null : cs[i].toString();
        return out;
    }

    public int[] getIntArray(int id) throws NotFoundException {
        AssetManager.Bag bag = mAssets.getBagOrdered(id);
        if (bag == null) throw new NotFoundException("Int array resource ID #0x" + Integer.toHexString(id));
        int[] out = new int[bag.size()];
        TypedValue v = new TypedValue();
        for (int i = 0; i < out.length; i++) {
            if (bag.types[i] == TypedValue.TYPE_REFERENCE && bag.datas[i] != 0 && mAssets.resolveReference(bag.datas[i], v)) out[i] = v.data;
            else out[i] = bag.datas[i];
        }
        return out;
    }

    public TypedArray obtainTypedArray(int id) throws NotFoundException {
        AssetManager.Bag bag = mAssets.getBagOrdered(id);
        if (bag == null) throw new NotFoundException("Array resource ID #0x" + Integer.toHexString(id));
        TypedArray array = TypedArray.obtain(this, bag.size());
        TypedValue v = new TypedValue();
        for (int i = 0; i < bag.size(); i++) {
            v.type = bag.types[i];
            v.data = bag.datas[i];
            v.string = bag.strings[i];
            v.resourceId = 0;
            v.assetCookie = 0;
            v.density = 0;
            resolveValue(v, null);
            array.set(i, v);
        }
        return array;
    }

    /** Follows attribute references (with a theme) and resource references. */
    void resolveValue(TypedValue v, Theme theme) {
        for (int guard = 0; guard < 20; guard++) {
            if (v.type == TypedValue.TYPE_ATTRIBUTE) {
                if (theme == null || !theme.lookup(v.data, v)) {
                    if (theme != null) {
                        v.type = TypedValue.TYPE_NULL;
                        v.data = 0;
                        v.string = null;
                    }
                    return;
                }
                continue;
            }
            if (v.type == TypedValue.TYPE_REFERENCE && v.data != 0) {
                int ref = v.data;
                if (!mAssets.resolveReference(ref, v)) {
                    v.resourceId = ref;
                    return;
                }
                if (v.type == TypedValue.TYPE_REFERENCE && v.data == ref) return; // complex (style / array / plurals)
                continue;
            }
            return;
        }
    }

    public float getDimension(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimension(value.data, mMetrics);
            if (value.type == TypedValue.TYPE_FLOAT) return value.getFloat();
            if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data;
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public int getDimensionPixelOffset(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimensionPixelOffset(value.data, mMetrics);
            if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data;
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public int getDimensionPixelSize(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimensionPixelSize(value.data, mMetrics);
            if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data;
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public float getFraction(int id, int base, int pbase) {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_FRACTION) return TypedValue.complexToFraction(value.data, base, pbase);
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    @Deprecated
    public Drawable getDrawable(int id) throws NotFoundException { return getDrawable(id, null); }

    public Drawable getDrawable(int id, Theme theme) throws NotFoundException { return getDrawableForDensity(id, 0, theme); }

    @Deprecated
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException { return getDrawableForDensity(id, density, null); }

    public Drawable getDrawableForDensity(int id, int density, Theme theme) {
        TypedValue value = new TypedValue();
        getValue(id, value, true);
        if (value.type == TypedValue.TYPE_ATTRIBUTE && theme != null) resolveValue(value, theme);
        return loadDrawable(value, id, density, theme);
    }

    Drawable loadDrawable(TypedValue value, int id, int density, Theme theme) throws NotFoundException {
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return new ColorDrawable(value.data);
        }
        if (value.type == TypedValue.TYPE_NULL) return null;
        if (value.string == null) {
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not a drawable");
        }
        final String file = value.string.toString();
        final int key = value.resourceId != 0 ? value.resourceId : id;
        final boolean isXml = file.endsWith(".xml");
        if (!isXml && key != 0) {
            Drawable.ConstantState cs;
            synchronized (mDrawableCache) { cs = mDrawableCache.get(key); }
            if (cs != null) return cs.newDrawable(this, theme);
        }
        try {
            Drawable dr;
            if (isXml) {
                XmlResourceParser rp = mAssets.openXmlResourceParser(value.assetCookie, file);
                dr = Drawable.createFromXmlForDensity(this, rp, density, theme);
                rp.close();
            } else {
                InputStream is = mAssets.openNonAsset(value.assetCookie, file, AssetManager.ACCESS_STREAMING);
                dr = Drawable.createFromResourceStream(this, value, is, file, null);
                is.close();
                if (dr != null && key != 0) {
                    Drawable.ConstantState cs = dr.getConstantState();
                    if (cs != null) synchronized (mDrawableCache) { mDrawableCache.put(key, cs); }
                }
            }
            if (dr == null) throw new NotFoundException("File " + file + " from drawable resource ID #0x" + Integer.toHexString(id));
            return dr;
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            NotFoundException rnf = new NotFoundException("File " + file + " from drawable resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        }
    }

    public Movie getMovie(int id) throws NotFoundException { return null; }

    @Deprecated
    public int getColor(int id) throws NotFoundException { return getColor(id, null); }

    public int getColor(int id, Theme theme) throws NotFoundException {
        TypedValue value = new TypedValue();
        getValue(id, value, true);
        if (value.type == TypedValue.TYPE_ATTRIBUTE) resolveValue(value, theme);
        if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data;
        if (value.type != TypedValue.TYPE_STRING) {
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        }
        ColorStateList csl = loadColorStateList(value, id, theme);
        return csl.getDefaultColor();
    }

    @Deprecated
    public ColorStateList getColorStateList(int id) throws NotFoundException { return getColorStateList(id, null); }

    public ColorStateList getColorStateList(int id, Theme theme) throws NotFoundException {
        TypedValue value = new TypedValue();
        getValue(id, value, true);
        if (value.type == TypedValue.TYPE_ATTRIBUTE) resolveValue(value, theme);
        return loadColorStateList(value, id, theme);
    }

    ColorStateList loadColorStateList(TypedValue value, int id, Theme theme) throws NotFoundException {
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return ColorStateList.valueOf(value.data);
        }
        if (value.type == TypedValue.TYPE_NULL) return null;
        if (value.string == null) throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " is not a color");
        String file = value.string.toString();
        int key = value.resourceId != 0 ? value.resourceId : id;
        if (theme == null && key != 0) {
            synchronized (mColorCache) {
                ColorStateList c = mColorCache.get(key);
                if (c != null) return c;
            }
        }
        if (!file.endsWith(".xml")) throw new NotFoundException("File " + file + " from color state list resource ID #0x" + Integer.toHexString(id));
        try {
            XmlResourceParser rp = mAssets.openXmlResourceParser(value.assetCookie, file);
            ColorStateList csl = ColorStateList.createFromXml(this, rp, theme);
            rp.close();
            if (theme == null && key != 0) synchronized (mColorCache) { mColorCache.put(key, csl); }
            return csl;
        } catch (Exception e) {
            NotFoundException rnf = new NotFoundException("File " + file + " from color state list resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        }
    }

    public boolean getBoolean(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data != 0;
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public int getInteger(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data;
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public float getFloat(int id) {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_FLOAT) return value.getFloat();
            if (value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) return value.data;
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public XmlResourceParser getLayout(int id) throws NotFoundException { return loadXmlResourceParser(id, "layout"); }
    public XmlResourceParser getAnimation(int id) throws NotFoundException { return loadXmlResourceParser(id, "anim"); }
    public XmlResourceParser getXml(int id) throws NotFoundException { return loadXmlResourceParser(id, "xml"); }

    XmlResourceParser loadXmlResourceParser(int id, String type) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            getValue(id, value, true);
            if (value.type == TypedValue.TYPE_STRING) return loadXmlResourceParser(value.string.toString(), id, value.assetCookie, type);
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x" + Integer.toHexString(value.type) + " is not valid");
        } finally {
            releaseTempTypedValue(value);
        }
    }

    XmlResourceParser loadXmlResourceParser(String file, int id, int assetCookie, String type) throws NotFoundException {
        if (!file.endsWith(".xml")) throw new NotFoundException("File " + file + " from xml type " + type + " resource ID #0x" + Integer.toHexString(id));
        XmlBlock block = mAssets.openXmlBlock(assetCookie, file);
        if (block == null) throw new NotFoundException("File " + file + " from xml type " + type + " resource ID #0x" + Integer.toHexString(id));
        return block.newParser();
    }

    public InputStream openRawResource(int id) throws NotFoundException {
        TypedValue value = obtainTempTypedValue();
        try {
            return openRawResource(id, value);
        } finally {
            releaseTempTypedValue(value);
        }
    }

    public InputStream openRawResource(int id, TypedValue value) throws NotFoundException {
        getValue(id, value, true);
        try {
            return mAssets.openNonAsset(value.assetCookie, value.string.toString(), AssetManager.ACCESS_STREAMING);
        } catch (Exception e) {
            NotFoundException rnf = new NotFoundException("File " + (value.string == null ? "(null)" : value.string.toString())
                    + " from resource ID #0x" + Integer.toHexString(id));
            rnf.initCause(e);
            throw rnf;
        }
    }

    public AssetFileDescriptor openRawResourceFd(int id) throws NotFoundException {
        TypedValue value = new TypedValue();
        getValue(id, value, true);
        return new RawResourceFd(mAssets, value.assetCookie, value.string.toString());
    }

    /** AssetFileDescriptor over a raw resource (res/raw/...), read through the AssetManager. */
    public static final class RawResourceFd extends AssetFileDescriptor {
        public final int cookie;
        public final String path;
        final AssetManager assets;

        RawResourceFd(AssetManager am, int cookie, String path) {
            super(am, null, 0, -1);
            this.assets = am;
            this.cookie = cookie;
            this.path = path;
        }

        @Override
        public java.io.FileInputStream createInputStream() throws IOException {
            return new AutoCloseInputStream(assets.openNonAsset(cookie, path));
        }

        @Override
        public long getLength() {
            try {
                return assets.openNonAsset(cookie, path).available();
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public long getDeclaredLength() { return getLength(); }
    }

    public int getIdentifier(String name, String defType, String defPackage) {
        if (name == null) throw new NullPointerException("name is null");
        String pkg = defPackage, type = defType, entry = name;
        if (entry.startsWith("@")) entry = entry.substring(1);
        if (entry.startsWith("+")) entry = entry.substring(1);
        int colon = entry.indexOf(':');
        if (colon >= 0) {
            pkg = entry.substring(0, colon);
            entry = entry.substring(colon + 1);
        }
        int slash = entry.indexOf('/');
        if (slash >= 0) {
            type = entry.substring(0, slash);
            entry = entry.substring(slash + 1);
        }
        if (type == null) return 0;
        try {
            return Integer.parseInt(name);
        } catch (Exception ignored) {}
        if (pkg == null || pkg.isEmpty()) {
            int id = mAssets.getResourceIdentifier(entry, type, AssetManager.appPackageName());
            if (id != 0) return id;
            return mAssets.getResourceIdentifier(entry, type, "android");
        }
        int id = mAssets.getResourceIdentifier(entry, type, pkg);
        if (id == 0 && !"android".equals(pkg)) id = mAssets.getResourceIdentifier(entry, type, null);
        return id;
    }

    public static boolean resourceHasPackage(int resid) { return (resid >>> 24) != 0; }

    public String getResourceName(int resid) throws NotFoundException {
        String str = mAssets.getResourceName(resid);
        if (str != null) return str;
        throw new NotFoundException("Unable to find resource ID #0x" + Integer.toHexString(resid));
    }

    public String getResourcePackageName(int resid) throws NotFoundException {
        String str = mAssets.getResourcePackageName(resid);
        if (str != null) return str;
        throw new NotFoundException("Unable to find resource ID #0x" + Integer.toHexString(resid));
    }

    public String getResourceTypeName(int resid) throws NotFoundException {
        String str = mAssets.getResourceTypeName(resid);
        if (str != null) return str;
        throw new NotFoundException("Unable to find resource ID #0x" + Integer.toHexString(resid));
    }

    public String getResourceEntryName(int resid) throws NotFoundException {
        String str = mAssets.getResourceEntryName(resid);
        if (str != null) return str;
        throw new NotFoundException("Unable to find resource ID #0x" + Integer.toHexString(resid));
    }

    public void parseBundleExtras(XmlResourceParser parser, Bundle outBundle) throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue;
            if ("extra".equals(parser.getName())) parseBundleExtra("extra", parser, outBundle);
        }
    }

    public void parseBundleExtra(String tagName, AttributeSet attrs, Bundle outBundle) throws XmlPullParserException {
        String name = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
        String value = attrs.getAttributeValue("http://schemas.android.com/apk/res/android", "value");
        if (name != null && value != null) outBundle.putString(name, value);
    }

    public final void flushLayoutCache() {}
    public final void finishPreloading() {}

    public static int getAttributeSetSourceResId(AttributeSet set) { return ID_NULL; }

    public TypedArray obtainAttributes(AttributeSet set, int[] attrs) {
        return Theme.obtain(this, null, set, attrs, 0, 0);
    }

    public final Theme newTheme() { return new Theme(this); }

    private TypedValue obtainTempTypedValue() { return new TypedValue(); }
    private void releaseTempTypedValue(TypedValue value) {}

    // ---- Theme -------------------------------------------------------------------------------------

    public final class Theme {
        private final Resources mRes;
        private final SparseArray<TypedValue> mValues = new SparseArray<TypedValue>();
        private final java.util.ArrayList<int[]> mApplied = new java.util.ArrayList<int[]>();

        Theme(Resources res) { mRes = res; }

        public void applyStyle(int resId, boolean force) {
            if (resId == 0) return;
            AssetManager.Bag bag = mAssets.getStyleBag(resId);
            if (bag == null) {
                Log.w(TAG, "Theme style 0x" + Integer.toHexString(resId) + " not found");
                return;
            }
            synchronized (mValues) {
                mApplied.add(new int[] {resId, force ? 1 : 0});
                for (int i = 0; i < bag.size(); i++) {
                    int attr = bag.attrs[i];
                    if (!force && mValues.indexOfKey(attr) >= 0) continue;
                    TypedValue v = new TypedValue();
                    v.type = bag.types[i];
                    v.data = bag.datas[i];
                    v.string = bag.strings[i];
                    v.sourceResourceId = resId;
                    mValues.put(attr, v);
                }
            }
        }

        public void setTo(Theme other) {
            synchronized (mValues) {
                mValues.clear();
                mApplied.clear();
                synchronized (other.mValues) {
                    for (int i = 0; i < other.mValues.size(); i++) mValues.put(other.mValues.keyAt(i), other.mValues.valueAt(i));
                    mApplied.addAll(other.mApplied);
                }
            }
        }

        public void rebase() {
            java.util.ArrayList<int[]> applied;
            synchronized (mValues) {
                applied = new java.util.ArrayList<int[]>(mApplied);
                mValues.clear();
                mApplied.clear();
            }
            for (int[] a : applied) applyStyle(a[0], a[1] != 0);
        }

        /** Raw theme lookup: copies the stored value (without resolving). */
        boolean lookup(int attr, TypedValue out) {
            TypedValue v;
            synchronized (mValues) { v = mValues.get(attr); }
            if (v == null) return false;
            out.type = v.type;
            out.data = v.data;
            out.string = v.string;
            out.resourceId = 0;
            out.assetCookie = 0;
            out.density = 0;
            out.sourceResourceId = v.sourceResourceId;
            return true;
        }

        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) {
            if (!lookup(resid, outValue)) return false;
            for (int guard = 0; guard < 20 && outValue.type == TypedValue.TYPE_ATTRIBUTE; guard++) {
                if (!lookup(outValue.data, outValue)) return false;
            }
            if (resolveRefs && outValue.type == TypedValue.TYPE_REFERENCE) {
                resolveValue(outValue, this);
            } else if (outValue.type == TypedValue.TYPE_STRING || outValue.type == TypedValue.TYPE_REFERENCE) {
                outValue.assetCookie = (outValue.data >>> 24) == 1 ? 1 : 2;
            }
            return true;
        }

        public TypedArray obtainStyledAttributes(int[] attrs) { return obtain(mRes, this, null, attrs, 0, 0); }

        public TypedArray obtainStyledAttributes(int resId, int[] attrs) throws NotFoundException {
            return obtain(mRes, this, null, attrs, 0, resId);
        }

        public TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
            return obtain(mRes, this, set, attrs, defStyleAttr, defStyleRes);
        }

        public TypedArray resolveAttributes(int[] values, int[] attrs) {
            TypedArray array = TypedArray.obtain(mRes, attrs.length);
            TypedValue v = new TypedValue();
            for (int i = 0; i < attrs.length; i++) {
                if (!resolveAttribute(attrs[i], v, true)) v.type = TypedValue.TYPE_NULL;
                array.set(i, v);
            }
            return array;
        }

        public Resources getResources() { return mRes; }
        public Drawable getDrawable(int id) throws NotFoundException { return mRes.getDrawable(id, this); }
        public int getChangingConfigurations() { return 0; }
        public void dump(int priority, String tag, String prefix) {}

        public int getExplicitStyle(AttributeSet set) {
            if (set == null) return 0;
            int styleAttr = set.getStyleAttribute();
            if (styleAttr == 0) return 0;
            if (set instanceof XmlBlock.Parser && ((XmlBlock.Parser) set).isStyleAttributeThemeRef()) {
                TypedValue v = new TypedValue();
                if (resolveAttribute(styleAttr, v, true) && v.type == TypedValue.TYPE_REFERENCE) return v.data;
                return 0;
            }
            return styleAttr;
        }

        public int[] getAttributeResolutionStack(int defStyleAttr, int defStyleRes, int explicitStyleRes) { return new int[0]; }

        /** Identity of the applied styles (used for caches). */
        public int hashCode() {
            int h = 1;
            synchronized (mValues) {
                for (int[] a : mApplied) h = h * 31 + a[0] * 2 + a[1];
            }
            return h;
        }

        public boolean equals(Object o) { return o == this; }

        public String toString() { return "Theme{applied=" + mApplied.size() + "}"; }
    }

    /**
     * Core attribute resolution (AOSP ApplyStyle): XML attribute, then style="",
     * then the default style (from defStyleAttr, else defStyleRes), then the theme.
     */
    static TypedArray obtain(Resources res, Theme theme, AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
        final int n = attrs.length;
        TypedArray array = TypedArray.obtain(res, n);
        AssetManager am = res.mAssets;
        XmlBlock.Parser parser = set instanceof XmlBlock.Parser ? (XmlBlock.Parser) set : null;
        array.mXml = parser;
        array.mTheme = theme;

        AssetManager.Bag defStyle = null;
        if (theme != null && defStyleAttr != 0) {
            TypedValue v = new TypedValue();
            if (theme.resolveAttribute(defStyleAttr, v, true) && v.type == TypedValue.TYPE_REFERENCE && v.data != 0) {
                defStyle = am.getStyleBag(v.data);
            }
        }
        if (defStyle == null && defStyleRes != 0) defStyle = am.getStyleBag(defStyleRes);

        AssetManager.Bag xmlStyle = null;
        if (set != null) {
            int style = set.getStyleAttribute();
            if (style != 0) {
                if (parser != null && parser.isStyleAttributeThemeRef()) {
                    TypedValue v = new TypedValue();
                    if (theme != null && theme.resolveAttribute(style, v, true) && v.type == TypedValue.TYPE_REFERENCE) {
                        xmlStyle = am.getStyleBag(v.data);
                    }
                } else {
                    xmlStyle = am.getStyleBag(style);
                }
            }
        }

        // map attr resource id -> xml attribute index
        HashMap<Integer, Integer> xmlIndex = null;
        if (parser != null) {
            int count = parser.getAttributeCount();
            if (count > 0) {
                xmlIndex = new HashMap<Integer, Integer>(count * 2);
                for (int i = 0; i < count; i++) {
                    int rid = parser.getAttributeNameResource(i);
                    if (rid != 0) xmlIndex.put(rid, i);
                }
            }
        }

        TypedValue v = new TypedValue();
        for (int i = 0; i < n; i++) {
            int attr = attrs[i];
            v.type = TypedValue.TYPE_NULL;
            v.data = 0;
            v.string = null;
            v.resourceId = 0;
            v.assetCookie = 0;
            v.density = 0;
            boolean found = false;
            if (parser != null && xmlIndex != null) {
                Integer xi = xmlIndex.get(attr);
                if (xi != null) {
                    int idx = xi;
                    v.type = parser.getAttributeDataType(idx);
                    v.data = parser.getAttributeData(idx);
                    if (v.type == TypedValue.TYPE_STRING) v.string = parser.getAttributeStringValue(idx);
                    found = true;
                }
            } else if (set != null && parser == null) {
                String name = am.getResourceEntryName(attr);
                if (name != null) {
                    String val = set.getAttributeValue("http://schemas.android.com/apk/res/android", name);
                    if (val == null) val = set.getAttributeValue(null, name);
                    if (val != null) {
                        parseTextValue(val, v);
                        found = true;
                    }
                }
            }
            if (!found && xmlStyle != null) found = fromBag(xmlStyle, attr, v);
            if (!found && defStyle != null) found = fromBag(defStyle, attr, v);
            if (!found && theme != null) found = theme.lookup(attr, v);
            if (found) {
                if (v.type == TypedValue.TYPE_NULL && v.data == TypedValue.DATA_NULL_EMPTY) {
                    array.set(i, v);
                    continue;
                }
                res.resolveValue(v, theme);
                if (v.type == TypedValue.TYPE_STRING && v.assetCookie == 0) {
                    v.assetCookie = v.resourceId != 0 ? ((v.resourceId >>> 24) == 1 ? 1 : 2) : 0;
                }
            }
            array.set(i, v);
        }
        return array;
    }

    private static boolean fromBag(AssetManager.Bag bag, int attr, TypedValue v) {
        int j = bag.indexOf(attr);
        if (j < 0) return false;
        v.type = bag.types[j];
        v.data = bag.datas[j];
        v.string = bag.strings[j];
        return true;
    }

    /** Best-effort typing of a textual attribute value (uncompiled XML). */
    static void parseTextValue(String s, TypedValue v) {
        v.type = TypedValue.TYPE_STRING;
        v.string = s;
        v.data = 0;
        if (s.isEmpty()) return;
        try {
            if (s.charAt(0) == '#') {
                String h = s.substring(1);
                long c = Long.parseLong(h, 16);
                if (h.length() == 3) c = 0xff000000L | ((c & 0xf00) * 0x1100) | ((c & 0xf0) * 0x110) | ((c & 0xf) * 0x11);
                else if (h.length() == 6) c |= 0xff000000L;
                v.type = TypedValue.TYPE_INT_COLOR_ARGB8;
                v.data = (int) c;
                return;
            }
            if (s.equals("true") || s.equals("false")) {
                v.type = TypedValue.TYPE_INT_BOOLEAN;
                v.data = s.equals("true") ? -1 : 0;
                return;
            }
            String[] units = {"px", "dip", "dp", "sp", "pt", "in", "mm"};
            int[] codes = {TypedValue.COMPLEX_UNIT_PX, TypedValue.COMPLEX_UNIT_DIP, TypedValue.COMPLEX_UNIT_DIP,
                    TypedValue.COMPLEX_UNIT_SP, TypedValue.COMPLEX_UNIT_PT, TypedValue.COMPLEX_UNIT_IN, TypedValue.COMPLEX_UNIT_MM};
            for (int u = 0; u < units.length; u++) {
                if (s.endsWith(units[u])) {
                    float f = Float.parseFloat(s.substring(0, s.length() - units[u].length()));
                    v.type = TypedValue.TYPE_DIMENSION;
                    v.data = floatToComplex(f) | codes[u];
                    return;
                }
            }
            if (s.indexOf('.') >= 0) {
                v.type = TypedValue.TYPE_FLOAT;
                v.data = Float.floatToIntBits(Float.parseFloat(s));
                return;
            }
            v.data = Integer.decode(s);
            v.type = TypedValue.TYPE_INT_DEC;
        } catch (NumberFormatException e) {
            v.type = TypedValue.TYPE_STRING;
        }
    }

    /** Encodes a float into the TypedValue complex format (mantissa and radix), unit bits left 0. */
    static int floatToComplex(float value) {
        boolean neg = value < 0;
        if (neg) value = -value;
        long bits = (long) (value * (1 << 23) + .5f);
        int radix, shift;
        if ((bits & 0x7fffff) == 0) {
            radix = TypedValue.COMPLEX_RADIX_23p0;
            shift = 23;
        } else if ((bits & 0xffffffffff800000L) == 0) {
            radix = TypedValue.COMPLEX_RADIX_0p23;
            shift = 0;
        } else if ((bits & 0xffffffff80000000L) == 0) {
            radix = TypedValue.COMPLEX_RADIX_8p15;
            shift = 8;
        } else if ((bits & 0xffffff8000000000L) == 0) {
            radix = TypedValue.COMPLEX_RADIX_16p7;
            shift = 16;
        } else {
            radix = TypedValue.COMPLEX_RADIX_23p0;
            shift = 23;
        }
        int mantissa = (int) ((bits >> shift) & TypedValue.COMPLEX_MANTISSA_MASK);
        if (neg) mantissa = (-mantissa) & TypedValue.COMPLEX_MANTISSA_MASK;
        return (radix << TypedValue.COMPLEX_RADIX_SHIFT) | (mantissa << TypedValue.COMPLEX_MANTISSA_SHIFT);
    }
}
