package android.content.res;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

public class TypedArray implements AutoCloseable {
    static final int STYLE_NUM_ENTRIES = 6;
    static final int STYLE_TYPE = 0;
    static final int STYLE_DATA = 1;
    static final int STYLE_ASSET_COOKIE = 2;
    static final int STYLE_RESOURCE_ID = 3;
    static final int STYLE_CHANGING_CONFIGURATIONS = 4;
    static final int STYLE_DENSITY = 5;

    private final Resources mResources;
    private final DisplayMetrics mMetrics;
    int[] mData;
    CharSequence[] mStrings;
    int mLength;
    XmlBlock.Parser mXml;
    Resources.Theme mTheme;
    private boolean mRecycled;
    private int[] mIndices;

    static TypedArray obtain(Resources res, int len) {
        TypedArray a = new TypedArray(res);
        a.mLength = len;
        a.mData = new int[len * STYLE_NUM_ENTRIES];
        a.mStrings = new CharSequence[len];
        return a;
    }

    protected TypedArray(Resources resources) {
        mResources = resources;
        mMetrics = resources.getDisplayMetrics();
    }

    void set(int i, TypedValue v) {
        int b = i * STYLE_NUM_ENTRIES;
        mData[b + STYLE_TYPE] = v.type;
        mData[b + STYLE_DATA] = v.data;
        mData[b + STYLE_ASSET_COOKIE] = v.assetCookie;
        mData[b + STYLE_RESOURCE_ID] = v.resourceId;
        mData[b + STYLE_CHANGING_CONFIGURATIONS] = 0;
        mData[b + STYLE_DENSITY] = v.density;
        mStrings[i] = v.string;
        mIndices = null;
    }

    public int length() {
        checkRecycled();
        return mLength;
    }

    public int getIndexCount() {
        buildIndices();
        return mIndices.length;
    }

    public int getIndex(int at) {
        buildIndices();
        return mIndices[at];
    }

    private void buildIndices() {
        if (mIndices != null) return;
        int n = 0;
        for (int i = 0; i < mLength; i++) if (hasValueOrEmpty(i)) n++;
        mIndices = new int[n];
        n = 0;
        for (int i = 0; i < mLength; i++) if (hasValueOrEmpty(i)) mIndices[n++] = i;
    }

    public Resources getResources() { return mResources; }

    private int type(int index) { return mData[index * STYLE_NUM_ENTRIES + STYLE_TYPE]; }
    private int data(int index) { return mData[index * STYLE_NUM_ENTRIES + STYLE_DATA]; }

    private void checkRecycled() {
        if (mRecycled) throw new RuntimeException("Cannot make calls to a recycled instance!");
    }

    public CharSequence getText(int index) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return null;
        if (t == TypedValue.TYPE_STRING) return mStrings[index];
        return TypedValue.coerceToString(t, data(index));
    }

    public String getString(int index) {
        CharSequence cs = getText(index);
        return cs != null ? cs.toString() : null;
    }

    public String getNonResourceString(int index) {
        checkRecycled();
        if (type(index) == TypedValue.TYPE_STRING && mData[index * STYLE_NUM_ENTRIES + STYLE_RESOURCE_ID] == 0) {
            return mStrings[index] != null ? mStrings[index].toString() : null;
        }
        return null;
    }

    public String getNonConfigurationString(int index, int allowedChangingConfigs) { return getString(index); }

    public boolean getBoolean(int index, boolean defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index) != 0;
        if (t == TypedValue.TYPE_STRING && mStrings[index] != null) return Boolean.parseBoolean(mStrings[index].toString());
        return defValue;
    }

    public int getInt(int index, int defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_FLOAT) return (int) Float.intBitsToFloat(data(index));
        if (t == TypedValue.TYPE_STRING && mStrings[index] != null) {
            try {
                return Integer.decode(mStrings[index].toString().trim());
            } catch (NumberFormatException e) {
                return defValue;
            }
        }
        return defValue;
    }

    public float getFloat(int index, float defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(data(index));
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_DIMENSION || t == TypedValue.TYPE_FRACTION) return TypedValue.complexToFloat(data(index));
        if (t == TypedValue.TYPE_STRING && mStrings[index] != null) {
            try {
                return Float.parseFloat(mStrings[index].toString());
            } catch (NumberFormatException e) {
                return defValue;
            }
        }
        return defValue;
    }

    public int getColor(int index, int defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_STRING) {
            ColorStateList csl = getColorStateList(index);
            return csl != null ? csl.getDefaultColor() : defValue;
        }
        return defValue;
    }

    public ComplexColor getComplexColor(int index) { return getColorStateList(index); }

    public ColorStateList getColorStateList(int index) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return null;
        TypedValue value = new TypedValue();
        getValueAt(index, value);
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return ColorStateList.valueOf(value.data);
        if (t == TypedValue.TYPE_STRING) {
            String s = value.string != null ? value.string.toString() : "";
            if (s.startsWith("#")) {
                Resources.parseTextValue(s, value);
                return ColorStateList.valueOf(value.data);
            }
            return mResources.loadColorStateList(value, value.resourceId, mTheme);
        }
        return null;
    }

    public int getInteger(int index, int defValue) { return getInt(index, defValue); }

    public float getDimension(int index, float defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimension(data(index), mMetrics);
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(data(index));
        return defValue;
    }

    public int getDimensionPixelOffset(int index, int defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimensionPixelOffset(data(index), mMetrics);
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_FLOAT) return (int) Float.intBitsToFloat(data(index));
        return defValue;
    }

    public int getDimensionPixelSize(int index, int defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimensionPixelSize(data(index), mMetrics);
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_FLOAT) return Math.round(Float.intBitsToFloat(data(index)));
        return defValue;
    }

    public int getLayoutDimension(int index, String name) {
        checkRecycled();
        int t = type(index);
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimensionPixelSize(data(index), mMetrics);
        throw new UnsupportedOperationException(getPositionDescription() + ": You must supply a " + name + " attribute.");
    }

    public int getLayoutDimension(int index, int defValue) {
        checkRecycled();
        int t = type(index);
        if (t >= TypedValue.TYPE_FIRST_INT && t <= TypedValue.TYPE_LAST_INT) return data(index);
        if (t == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimensionPixelSize(data(index), mMetrics);
        return defValue;
    }

    public float getFraction(int index, int base, int pbase, float defValue) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return defValue;
        if (t == TypedValue.TYPE_FRACTION) return TypedValue.complexToFraction(data(index), base, pbase);
        if (t == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(data(index));
        return defValue;
    }

    public int getResourceId(int index, int defValue) {
        checkRecycled();
        int b = index * STYLE_NUM_ENTRIES;
        if (mData[b + STYLE_TYPE] != TypedValue.TYPE_NULL) {
            int resid = mData[b + STYLE_RESOURCE_ID];
            if (resid != 0) return resid;
            if (mData[b + STYLE_TYPE] == TypedValue.TYPE_REFERENCE) return mData[b + STYLE_DATA];
        }
        return defValue;
    }

    public int getThemeAttributeId(int index, int defValue) {
        checkRecycled();
        if (type(index) == TypedValue.TYPE_ATTRIBUTE) return data(index);
        return defValue;
    }

    public Drawable getDrawable(int index) { return getDrawableForDensity(index, 0); }

    public Drawable getDrawableForDensity(int index, int density) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return null;
        TypedValue value = new TypedValue();
        getValueAt(index, value);
        if (t == TypedValue.TYPE_STRING && value.string != null && value.string.toString().startsWith("#")) {
            Resources.parseTextValue(value.string.toString(), value);
        }
        return mResources.loadDrawable(value, value.resourceId, density, mTheme);
    }

    public Typeface getFont(int index) {
        checkRecycled();
        int t = type(index);
        if (t == TypedValue.TYPE_NULL) return null;
        TypedValue value = new TypedValue();
        getValueAt(index, value);
        if (value.resourceId != 0 && value.string != null && value.string.toString().startsWith("res/")) {
            try {
                return mResources.getFont(value.resourceId);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
        if (value.string != null) return Typeface.create(value.string.toString(), Typeface.NORMAL);
        return null;
    }

    public CharSequence[] getTextArray(int index) {
        checkRecycled();
        int id = getResourceId(index, 0);
        if (id == 0) return null;
        try {
            return mResources.getTextArray(id);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    public boolean getValue(int index, TypedValue outValue) {
        checkRecycled();
        getValueAt(index, outValue);
        return outValue.type != TypedValue.TYPE_NULL;
    }

    private void getValueAt(int index, TypedValue outValue) {
        int b = index * STYLE_NUM_ENTRIES;
        outValue.type = mData[b + STYLE_TYPE];
        outValue.data = mData[b + STYLE_DATA];
        outValue.assetCookie = mData[b + STYLE_ASSET_COOKIE];
        outValue.resourceId = mData[b + STYLE_RESOURCE_ID];
        outValue.changingConfigurations = mData[b + STYLE_CHANGING_CONFIGURATIONS];
        outValue.density = mData[b + STYLE_DENSITY];
        outValue.string = mStrings[index];
        if (outValue.assetCookie == 0 && outValue.resourceId != 0) outValue.assetCookie = (outValue.resourceId >>> 24) == 1 ? 1 : 2;
    }

    public int getType(int index) {
        checkRecycled();
        return type(index);
    }

    public int getSourceResourceId(int index, int defaultValue) { return defaultValue; }

    public boolean hasValue(int index) {
        checkRecycled();
        return type(index) != TypedValue.TYPE_NULL;
    }

    public boolean hasValueOrEmpty(int index) {
        checkRecycled();
        int b = index * STYLE_NUM_ENTRIES;
        return mData[b + STYLE_TYPE] != TypedValue.TYPE_NULL || mData[b + STYLE_DATA] == TypedValue.DATA_NULL_EMPTY;
    }

    public TypedValue peekValue(int index) {
        checkRecycled();
        TypedValue value = new TypedValue();
        if (getValue(index, value)) return value;
        return null;
    }

    public String getPositionDescription() {
        checkRecycled();
        return mXml != null ? mXml.getPositionDescription() : "<internal>";
    }

    public void recycle() {
        mRecycled = true;
        mXml = null;
        mTheme = null;
    }

    public void close() { recycle(); }

    public int[] extractThemeAttrs() {
        int[] attrs = null;
        for (int i = 0; i < mLength; i++) {
            if (type(i) == TypedValue.TYPE_ATTRIBUTE) {
                if (attrs == null) attrs = new int[mLength];
                attrs[i] = data(i);
            }
        }
        return attrs;
    }

    public int getChangingConfigurations() { return 0; }

    @Override
    public String toString() { return java.util.Arrays.toString(mData); }
}
