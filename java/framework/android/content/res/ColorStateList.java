package android.content.res;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.StateSet;
import java.io.IOException;
import java.util.Arrays;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ColorStateList extends ComplexColor implements Parcelable {
    private static final int DEFAULT_COLOR = 0xFFFF0000;
    private static final int[][] EMPTY = new int[][] {new int[0]};
    private static final SparseArray<ColorStateList> sCache = new SparseArray<ColorStateList>();

    private int[][] mStateSpecs;
    private int[] mColors;
    private int mDefaultColor;
    private boolean mIsOpaque;

    private ColorStateList() {}

    public ColorStateList(int[][] states, int[] colors) {
        mStateSpecs = states;
        mColors = colors;
        onColorsChanged();
    }

    public static ColorStateList valueOf(int color) {
        synchronized (sCache) {
            ColorStateList csl = sCache.get(color);
            if (csl != null) return csl;
            csl = new ColorStateList(EMPTY, new int[] {color});
            if (sCache.size() < 256) sCache.put(color, csl);
            return csl;
        }
    }

    @Deprecated
    public static ColorStateList createFromXml(Resources r, XmlPullParser parser) throws XmlPullParserException, IOException {
        return createFromXml(r, parser, null);
    }

    public static ColorStateList createFromXml(Resources r, XmlPullParser parser, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        final AttributeSet attrs = android.util.Xml.asAttributeSet(parser);
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
        if (type != XmlPullParser.START_TAG) throw new XmlPullParserException("No start tag found");
        return createFromXmlInner(r, parser, attrs, theme);
    }

    static ColorStateList createFromXmlInner(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        final String name = parser.getName();
        if (!name.equals("selector")) throw new XmlPullParserException(parser.getPositionDescription() + ": invalid color state list tag " + name);
        final ColorStateList colorStateList = new ColorStateList();
        colorStateList.inflate(r, parser, attrs, theme);
        return colorStateList;
    }

    private static final int[] ITEM_ATTRS = {android.R.attr.color, android.R.attr.alpha, android.R.attr.lStar};

    private void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        final int innerDepth = parser.getDepth() + 1;
        int depth;
        int type;
        int defaultColor = DEFAULT_COLOR;
        boolean hasUnresolvedAttrs = false;
        int[][] stateSpecList = new int[20][];
        int[] colorList = new int[stateSpecList.length];
        int listSize = 0;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth || !parser.getName().equals("item")) continue;
            final TypedArray a = theme != null ? theme.obtainStyledAttributes(attrs, ITEM_ATTRS, 0, 0) : r.obtainAttributes(attrs, ITEM_ATTRS);
            int baseColor = a.getColor(0, 0xFFFF00FF);
            float alphaMod = a.getFloat(1, 1.0f);
            a.recycle();

            final int numAttrs = attrs.getAttributeCount();
            int[] stateSpec = new int[numAttrs];
            int j = 0;
            for (int i = 0; i < numAttrs; i++) {
                final int stateResId = attrs.getAttributeNameResource(i);
                if (stateResId == 0) continue;
                if (stateResId == android.R.attr.alpha || stateResId == android.R.attr.color || stateResId == android.R.attr.lStar) continue;
                stateSpec[j++] = attrs.getAttributeBooleanValue(i, false) ? stateResId : -stateResId;
            }
            stateSpec = StateSet.trimStateSet(stateSpec, j);
            final int color = modulateColorAlpha(baseColor, alphaMod);
            if (listSize == 0 || stateSpec.length == 0) defaultColor = color;
            if (listSize == stateSpecList.length) {
                stateSpecList = Arrays.copyOf(stateSpecList, listSize * 2);
                colorList = Arrays.copyOf(colorList, listSize * 2);
            }
            stateSpecList[listSize] = stateSpec;
            colorList[listSize] = color;
            listSize++;
        }
        mDefaultColor = defaultColor;
        mColors = Arrays.copyOf(colorList, listSize);
        mStateSpecs = Arrays.copyOf(stateSpecList, listSize);
        onColorsChanged();
    }

    static int modulateColorAlpha(int baseColor, float alphaMod) {
        if (alphaMod == 1.0f) return baseColor;
        final int baseAlpha = (baseColor >>> 24);
        final int alpha = Math.max(0, Math.min(255, (int) (baseAlpha * alphaMod + 0.5f)));
        return (baseColor & 0xFFFFFF) | (alpha << 24);
    }

    public ColorStateList withAlpha(int alpha) {
        final int[] colors = new int[mColors.length];
        for (int i = 0; i < colors.length; i++) colors[i] = (mColors[i] & 0xFFFFFF) | (alpha << 24);
        return new ColorStateList(mStateSpecs, colors);
    }

    public ColorStateList withLStar(float lStar) { return this; }

    @Override
    public boolean canApplyTheme() { return false; }

    @Override
    public ColorStateList obtainForTheme(Resources.Theme t) { return this; }

    @Override
    public boolean isStateful() { return mStateSpecs.length >= 1 && mStateSpecs[0].length > 0; }

    public boolean hasFocusStateSpecified() {
        for (int[] spec : mStateSpecs) for (int s : spec) if (s == android.R.attr.state_focused || s == -android.R.attr.state_focused) return true;
        return false;
    }

    public boolean isOpaque() { return mIsOpaque; }

    public int getColorForState(int[] stateSet, int defaultColor) {
        final int setLength = mStateSpecs.length;
        for (int i = 0; i < setLength; i++) {
            if (StateSet.stateSetMatches(mStateSpecs[i], stateSet)) return mColors[i];
        }
        return defaultColor;
    }

    @Override
    public int getDefaultColor() { return mDefaultColor; }

    public int[][] getStates() { return mStateSpecs; }

    /** framework-internal (hidden in AOSP): whether any state spec mentions {@code state} (or its negation). */
    public boolean hasState(int state) {
        final int[][] stateSpecs = mStateSpecs;
        for (int specIndex = 0; specIndex < stateSpecs.length; specIndex++) {
            final int[] states = stateSpecs[specIndex];
            for (int stateIndex = 0; stateIndex < states.length; stateIndex++) {
                if (states[stateIndex] == state || states[stateIndex] == ~state) {
                    return true;
                }
            }
        }
        return false;
    }
    public int[] getColors() { return mColors; }

    private void onColorsChanged() {
        int defaultColor = DEFAULT_COLOR;
        boolean isOpaque = true;
        final int[][] states = mStateSpecs;
        final int[] colors = mColors;
        final int N = states.length;
        if (N > 0) {
            defaultColor = colors[0];
            for (int i = N - 1; i > 0; i--) {
                if (states[i].length == 0) {
                    defaultColor = colors[i];
                    break;
                }
            }
            for (int i = 0; i < N; i++) {
                if ((colors[i] >>> 24) != 0xFF) {
                    isOpaque = false;
                    break;
                }
            }
        }
        mDefaultColor = defaultColor;
        mIsOpaque = isOpaque;
    }

    @Override
    public String toString() {
        return "ColorStateList{mStateSpecs=" + Arrays.deepToString(mStateSpecs) + "mColors=" + Arrays.toString(mColors) + "mDefaultColor=" + mDefaultColor + '}';
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStateSpecs.length);
        for (int[] s : mStateSpecs) dest.writeIntArray(s);
        dest.writeIntArray(mColors);
    }

    public static final Parcelable.Creator<ColorStateList> CREATOR = new Parcelable.Creator<ColorStateList>() {
        public ColorStateList[] newArray(int size) { return new ColorStateList[size]; }
        public ColorStateList createFromParcel(Parcel source) {
            final int N = source.readInt();
            final int[][] stateSpecs = new int[N][];
            for (int i = 0; i < N; i++) stateSpecs[i] = source.createIntArray();
            final int[] colors = source.createIntArray();
            return new ColorStateList(stateSpecs, colors);
        }
    };
}
