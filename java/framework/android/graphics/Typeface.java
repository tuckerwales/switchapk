package android.graphics;

import android.content.res.AssetManager;
import java.io.File;
import java.util.HashMap;

public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    public static final Typeface DEFAULT;
    public static final Typeface DEFAULT_BOLD;
    public static final Typeface SANS_SERIF;
    public static final Typeface SERIF;
    public static final Typeface MONOSPACE;

    private static final long sRegular;
    private static final long sBold;
    private static final HashMap<String, Typeface> sFamilies = new HashMap<String, Typeface>();

    long mNative;
    boolean mFakeBold;
    boolean mItalic;
    private int mStyle;
    private int mWeight;
    private String mFamily;
    private long mRegularNative, mBoldNative;

    static {
        sRegular = nDefault(false);
        long b = nDefault(true);
        sBold = b != sRegular ? b : 0;
        DEFAULT = new Typeface(sRegular, sBold, NORMAL, "sans-serif");
        DEFAULT_BOLD = DEFAULT.withStyle(BOLD);
        SANS_SERIF = DEFAULT;
        SERIF = new Typeface(sRegular, sBold, NORMAL, "serif");
        MONOSPACE = new Typeface(sRegular, sBold, NORMAL, "monospace");
        sFamilies.put("sans-serif", DEFAULT);
        sFamilies.put("serif", SERIF);
        sFamilies.put("monospace", MONOSPACE);
    }

    private Typeface(long regular, long bold, int style, String family) {
        mRegularNative = regular;
        mBoldNative = bold;
        mFamily = family;
        applyStyle(style, (style & BOLD) != 0 ? 700 : 400);
    }

    private void applyStyle(int style, int weight) {
        mStyle = style;
        mWeight = weight;
        boolean bold = weight >= 600;
        if (bold && mBoldNative != 0) {
            mNative = mBoldNative;
            mFakeBold = false;
        } else {
            mNative = mRegularNative;
            mFakeBold = bold;
        }
        mItalic = (style & ITALIC) != 0;
    }

    private Typeface withStyle(int style) {
        Typeface t = new Typeface(mRegularNative, mBoldNative, style, mFamily);
        return t;
    }

    private Typeface withWeight(int weight, boolean italic) {
        Typeface t = new Typeface(mRegularNative, mBoldNative, NORMAL, mFamily);
        t.applyStyle((weight >= 600 ? BOLD : 0) | (italic ? ITALIC : 0), weight);
        return t;
    }

    public int getStyle() { return mStyle; }
    public final boolean isBold() { return mWeight >= 600; }
    public final boolean isItalic() { return mItalic; }
    public int getWeight() { return mWeight; }
    public String getSystemFontFamilyName() { return mFamily; }

    public static Typeface create(String familyName, int style) {
        Typeface base = familyName != null ? sFamilies.get(familyName.toLowerCase()) : null;
        if (base == null) {
            String f = familyName != null ? familyName.toLowerCase() : "";
            if (f.contains("mono")) base = MONOSPACE;
            else if (f.contains("serif") && !f.contains("sans")) base = SERIF;
            else base = DEFAULT;
            if (f.endsWith("-medium") || f.endsWith("-bold") || f.endsWith("-black")) return base.withWeight(700, (style & ITALIC) != 0);
        }
        return create(base, style);
    }

    public static Typeface create(Typeface family, int style) {
        if (family == null) family = DEFAULT;
        if (family.mStyle == style) return family;
        return family.withStyle(style);
    }

    public static Typeface create(Typeface family, int weight, boolean italic) {
        if (family == null) family = DEFAULT;
        return family.withWeight(weight, italic);
    }

    public static Typeface defaultFromStyle(int style) { return create(DEFAULT, style); }

    public static Typeface createFromAsset(AssetManager mgr, String path) {
        try {
            java.io.InputStream in = mgr.open(path);
            byte[] data = readAll(in);
            Typeface t = createFromBytes(data);
            if (t == null) throw new RuntimeException("Font asset not found " + path);
            return t;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Font asset not found " + path);
        }
    }

    public static Typeface createFromFile(File file) { return createFromFile(file.getAbsolutePath()); }

    public static Typeface createFromFile(String path) {
        long h = nLoadFile(path);
        if (h == 0) throw new RuntimeException("Font not found " + path);
        return new Typeface(h, 0, NORMAL, path);
    }

    /** Loads a font from memory (hidden API used by Resources). */
    public static Typeface createFromBytes(byte[] data) {
        long h = nLoad(data);
        if (h == 0) return null;
        return new Typeface(h, 0, NORMAL, "custom");
    }

    static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Typeface)) return false;
        Typeface t = (Typeface) o;
        return t.mNative == mNative && t.mStyle == mStyle && t.mWeight == mWeight && t.mFakeBold == mFakeBold;
    }

    @Override
    public int hashCode() { return (int) mNative * 31 + mStyle * 7 + mWeight; }

    public static final class Builder {
        public static final int NORMAL_WEIGHT = 400;
        public static final int BOLD_WEIGHT = 700;
        private byte[] mData;
        private String mPath;
        private int mWeight = -1;
        private int mItalic = -1;
        private String mFallback;

        public Builder(File path) { mPath = path.getAbsolutePath(); }
        public Builder(java.io.FileDescriptor fd) {}
        public Builder(String path) { mPath = path; }

        public Builder(AssetManager assetManager, String path) {
            try {
                mData = readAll(assetManager.open(path));
            } catch (java.io.IOException e) {
                mData = null;
            }
        }

        public Builder setWeight(int weight) { mWeight = weight; return this; }
        public Builder setItalic(boolean italic) { mItalic = italic ? 1 : 0; return this; }
        public Builder setTtcIndex(int ttcIndex) { return this; }
        public Builder setFontVariationSettings(String variationSettings) { return this; }
        public Builder setFallback(String familyName) { mFallback = familyName; return this; }

        public Typeface build() {
            Typeface t = null;
            try {
                if (mData != null) t = createFromBytes(mData);
                else if (mPath != null) t = createFromFile(mPath);
            } catch (RuntimeException e) {
                t = null;
            }
            if (t == null) t = mFallback != null ? create(mFallback, NORMAL) : null;
            if (t == null) return null;
            if (mWeight > 0 || mItalic >= 0) t = t.withWeight(mWeight > 0 ? mWeight : 400, mItalic == 1);
            return t;
        }
    }

    public static final class CustomFallbackBuilder {
        private Typeface mTf;
        public CustomFallbackBuilder(android.graphics.fonts.FontFamily family) {}
        public CustomFallbackBuilder setSystemFallback(String familyName) { mTf = create(familyName, NORMAL); return this; }
        public CustomFallbackBuilder setStyle(android.graphics.fonts.FontStyle style) { return this; }
        public CustomFallbackBuilder addCustomFallback(android.graphics.fonts.FontFamily family) { return this; }
        public Typeface build() { return mTf != null ? mTf : DEFAULT; }
    }

    static native long nDefault(boolean bold);
    static native long nLoad(byte[] data);
    static native long nLoadFile(String path);
}
