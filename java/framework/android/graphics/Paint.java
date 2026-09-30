package android.graphics;

import android.os.LocaleList;
import android.text.TextUtils;
import java.util.Locale;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 0x01;
    public static final int FILTER_BITMAP_FLAG = 0x02;
    public static final int DITHER_FLAG = 0x04;
    public static final int UNDERLINE_TEXT_FLAG = 0x08;
    public static final int STRIKE_THRU_TEXT_FLAG = 0x10;
    public static final int FAKE_BOLD_TEXT_FLAG = 0x20;
    public static final int LINEAR_TEXT_FLAG = 0x40;
    public static final int SUBPIXEL_TEXT_FLAG = 0x80;
    @Deprecated public static final int DEV_KERN_TEXT_FLAG = 0x100;
    public static final int EMBEDDED_BITMAP_TEXT_FLAG = 0x400;
    public static final int HINTING_OFF = 0x0;
    public static final int HINTING_ON = 0x1;
    public static final int BIDI_LTR = 0x0;
    public static final int BIDI_RTL = 0x1;
    public static final int BIDI_DEFAULT_LTR = 0x2;
    public static final int BIDI_DEFAULT_RTL = 0x3;
    public static final int BIDI_FORCE_LTR = 0x4;
    public static final int BIDI_FORCE_RTL = 0x5;
    public static final int DIRECTION_LTR = 0;
    public static final int DIRECTION_RTL = 1;
    public static final int CURSOR_AFTER = 0;
    public static final int CURSOR_AT_OR_AFTER = 1;
    public static final int CURSOR_BEFORE = 2;
    public static final int CURSOR_AT_OR_BEFORE = 3;
    public static final int CURSOR_AT = 4;
    public static final int START_HYPHEN_EDIT_NO_EDIT = 0;
    public static final int END_HYPHEN_EDIT_NO_EDIT = 0;
    public static final int TEXT_RUN_FLAG_LEFT_EDGE = 0x2000;
    public static final int TEXT_RUN_FLAG_RIGHT_EDGE = 0x4000;
    static final int HIDDEN_DEFAULT_PAINT_FLAGS = DEV_KERN_TEXT_FLAG | EMBEDDED_BITMAP_TEXT_FLAG | FILTER_BITMAP_FLAG;

    // ---- fields read by the renderer natives ----
    int mColor = 0xFF000000;
    int mFlags;
    int mStyle;
    float mStrokeWidth;
    int mCap;
    int mJoin;
    float mMiter = 4.0f;
    int mXfer = PorterDuff.Mode.SRC_OVER.nativeInt;
    int mCfMode = -1;
    int mCfColor;
    Shader mShader;
    float mTextSize = 12;
    Typeface mTypeface;
    float mTextSkewX;
    float mTextScaleX = 1;

    // ---- Java side state ----
    private Style mStyleEnum = Style.FILL;
    private Cap mCapEnum = Cap.BUTT;
    private Join mJoinEnum = Join.MITER;
    private Align mAlign = Align.LEFT;
    private ColorFilter mColorFilter;
    private Xfermode mXfermode;
    private BlendMode mBlendMode;
    private PathEffect mPathEffect;
    private MaskFilter mMaskFilter;
    private float mShadowRadius, mShadowDx, mShadowDy;
    private int mShadowColor;
    private float mLetterSpacing;
    private float mWordSpacing;
    private LocaleList mLocales = LocaleList.getDefault();
    private String mFontFeatureSettings;
    private String mFontVariationSettings;
    private boolean mElegantTextHeight;
    private int mHinting = HINTING_ON;
    private int mStartHyphenEdit, mEndHyphenEdit;
    public int mBidiFlags = BIDI_DEFAULT_LTR;

    public enum Style {
        FILL(0), STROKE(1), FILL_AND_STROKE(2);
        Style(int nativeInt) { this.nativeInt = nativeInt; }
        final int nativeInt;
    }

    public enum Cap {
        BUTT(0), ROUND(1), SQUARE(2);
        Cap(int nativeInt) { this.nativeInt = nativeInt; }
        final int nativeInt;
    }

    public enum Join {
        MITER(0), ROUND(1), BEVEL(2);
        Join(int nativeInt) { this.nativeInt = nativeInt; }
        final int nativeInt;
    }

    public enum Align {
        LEFT(0), CENTER(1), RIGHT(2);
        Align(int nativeInt) { this.nativeInt = nativeInt; }
        final int nativeInt;
    }

    public Paint() { this(0); }

    public Paint(int flags) {
        setFlags(flags | HIDDEN_DEFAULT_PAINT_FLAGS);
    }

    public Paint(Paint paint) { set(paint); }

    public void reset() {
        mColor = 0xFF000000;
        setFlags(HIDDEN_DEFAULT_PAINT_FLAGS);
        mStyle = 0;
        mStyleEnum = Style.FILL;
        mStrokeWidth = 0;
        mCap = 0;
        mCapEnum = Cap.BUTT;
        mJoin = 0;
        mJoinEnum = Join.MITER;
        mMiter = 4;
        mXfer = PorterDuff.Mode.SRC_OVER.nativeInt;
        mXfermode = null;
        mBlendMode = null;
        mCfMode = -1;
        mColorFilter = null;
        mShader = null;
        mTextSize = 12;
        mTypeface = null;
        mTextSkewX = 0;
        mTextScaleX = 1;
        mAlign = Align.LEFT;
        mPathEffect = null;
        mMaskFilter = null;
        clearShadowLayer();
        mLetterSpacing = 0;
        mWordSpacing = 0;
        mLocales = LocaleList.getDefault();
        mFontFeatureSettings = null;
        mElegantTextHeight = false;
        mHinting = HINTING_ON;
    }

    public void set(Paint src) {
        if (this == src) return;
        mColor = src.mColor;
        mFlags = src.mFlags;
        mStyle = src.mStyle;
        mStyleEnum = src.mStyleEnum;
        mStrokeWidth = src.mStrokeWidth;
        mCap = src.mCap;
        mCapEnum = src.mCapEnum;
        mJoin = src.mJoin;
        mJoinEnum = src.mJoinEnum;
        mMiter = src.mMiter;
        mXfer = src.mXfer;
        mXfermode = src.mXfermode;
        mBlendMode = src.mBlendMode;
        mCfMode = src.mCfMode;
        mCfColor = src.mCfColor;
        mColorFilter = src.mColorFilter;
        mShader = src.mShader;
        mTextSize = src.mTextSize;
        mTypeface = src.mTypeface;
        mTextSkewX = src.mTextSkewX;
        mTextScaleX = src.mTextScaleX;
        mAlign = src.mAlign;
        mPathEffect = src.mPathEffect;
        mMaskFilter = src.mMaskFilter;
        mShadowRadius = src.mShadowRadius;
        mShadowDx = src.mShadowDx;
        mShadowDy = src.mShadowDy;
        mShadowColor = src.mShadowColor;
        mLetterSpacing = src.mLetterSpacing;
        mWordSpacing = src.mWordSpacing;
        mLocales = src.mLocales;
        mFontFeatureSettings = src.mFontFeatureSettings;
        mFontVariationSettings = src.mFontVariationSettings;
        mElegantTextHeight = src.mElegantTextHeight;
        mHinting = src.mHinting;
        mBidiFlags = src.mBidiFlags;
    }

    public int getFlags() { return mFlags; }
    public void setFlags(int flags) { mFlags = flags; }
    public int getHinting() { return mHinting; }
    public void setHinting(int mode) { mHinting = mode; }
    public final boolean isAntiAlias() { return (mFlags & ANTI_ALIAS_FLAG) != 0; }
    public void setAntiAlias(boolean aa) { setFlag(ANTI_ALIAS_FLAG, aa); }
    public final boolean isDither() { return (mFlags & DITHER_FLAG) != 0; }
    public void setDither(boolean dither) { setFlag(DITHER_FLAG, dither); }
    public final boolean isLinearText() { return (mFlags & LINEAR_TEXT_FLAG) != 0; }
    public void setLinearText(boolean linearText) { setFlag(LINEAR_TEXT_FLAG, linearText); }
    public final boolean isSubpixelText() { return (mFlags & SUBPIXEL_TEXT_FLAG) != 0; }
    public void setSubpixelText(boolean subpixelText) { setFlag(SUBPIXEL_TEXT_FLAG, subpixelText); }
    public final boolean isUnderlineText() { return (mFlags & UNDERLINE_TEXT_FLAG) != 0; }
    public float getUnderlinePosition() { return mTextSize * 0.11f; }
    public float getUnderlineThickness() { return Math.max(1f, mTextSize / 18f); }
    public void setUnderlineText(boolean underlineText) { setFlag(UNDERLINE_TEXT_FLAG, underlineText); }
    public final boolean isStrikeThruText() { return (mFlags & STRIKE_THRU_TEXT_FLAG) != 0; }
    public float getStrikeThruPosition() { return -mTextSize * 0.28f; }
    public float getStrikeThruThickness() { return getUnderlineThickness(); }
    public void setStrikeThruText(boolean strikeThruText) { setFlag(STRIKE_THRU_TEXT_FLAG, strikeThruText); }
    public final boolean isFakeBoldText() { return (mFlags & FAKE_BOLD_TEXT_FLAG) != 0; }
    public void setFakeBoldText(boolean fakeBoldText) { setFlag(FAKE_BOLD_TEXT_FLAG, fakeBoldText); }
    public final boolean isFilterBitmap() { return (mFlags & FILTER_BITMAP_FLAG) != 0; }
    public void setFilterBitmap(boolean filter) { setFlag(FILTER_BITMAP_FLAG, filter); }

    private void setFlag(int flag, boolean on) {
        if (on) mFlags |= flag;
        else mFlags &= ~flag;
    }

    public Style getStyle() { return mStyleEnum; }

    public void setStyle(Style style) {
        mStyleEnum = style;
        mStyle = style.nativeInt;
    }

    public int getColor() { return mColor; }
    public long getColorLong() { return Color.pack(mColor); }
    public void setColor(int color) { mColor = color; }
    public void setColor(long color) { mColor = Color.toArgb(color); }
    public int getAlpha() { return mColor >>> 24; }
    public void setAlpha(int a) { mColor = (mColor & 0x00FFFFFF) | ((a & 0xff) << 24); }
    public void setARGB(int a, int r, int g, int b) { setColor((a << 24) | (r << 16) | (g << 8) | b); }
    public float getStrokeWidth() { return mStrokeWidth; }
    public void setStrokeWidth(float width) { if (width >= 0) mStrokeWidth = width; }
    public float getStrokeMiter() { return mMiter; }
    public void setStrokeMiter(float miter) { if (miter >= 0) mMiter = miter; }
    public Cap getStrokeCap() { return mCapEnum; }

    public void setStrokeCap(Cap cap) {
        mCapEnum = cap;
        mCap = cap.nativeInt;
    }

    public Join getStrokeJoin() { return mJoinEnum; }

    public void setStrokeJoin(Join join) {
        mJoinEnum = join;
        mJoin = join.nativeInt;
    }

    public boolean getFillPath(Path src, Path dst) {
        dst.set(src);
        return true;
    }

    public Shader getShader() { return mShader; }

    public Shader setShader(Shader shader) {
        mShader = shader;
        return shader;
    }

    public ColorFilter getColorFilter() { return mColorFilter; }

    public ColorFilter setColorFilter(ColorFilter filter) {
        mColorFilter = filter;
        if (filter != null && filter.mMode >= 0) {
            mCfMode = filter.mMode;
            mCfColor = filter.mColor;
        } else {
            mCfMode = -1;
        }
        return filter;
    }

    /** Color filter that the renderer cannot apply (matrix/lighting); applied by Canvas. */
    ColorFilter javaColorFilter() { return mColorFilter != null && mColorFilter.mMode < 0 ? mColorFilter : null; }

    public Xfermode getXfermode() { return mXfermode; }

    public Xfermode setXfermode(Xfermode xfermode) {
        mXfermode = xfermode;
        mBlendMode = null;
        mXfer = xfermode != null ? xfermode.porterDuffMode : PorterDuff.Mode.SRC_OVER.nativeInt;
        return xfermode;
    }

    public BlendMode getBlendMode() { return mBlendMode; }

    public void setBlendMode(BlendMode blendmode) {
        mBlendMode = blendmode;
        mXfermode = null;
        mXfer = blendmode != null ? blendmode.toPorterDuff() : PorterDuff.Mode.SRC_OVER.nativeInt;
    }

    public PathEffect getPathEffect() { return mPathEffect; }

    public PathEffect setPathEffect(PathEffect effect) {
        mPathEffect = effect;
        return effect;
    }

    public MaskFilter getMaskFilter() { return mMaskFilter; }

    public MaskFilter setMaskFilter(MaskFilter maskfilter) {
        mMaskFilter = maskfilter;
        return maskfilter;
    }

    public Typeface getTypeface() { return mTypeface; }

    public Typeface setTypeface(Typeface typeface) {
        mTypeface = typeface;
        return typeface;
    }

    public void setShadowLayer(float radius, float dx, float dy, int shadowColor) {
        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = shadowColor;
    }

    public void setShadowLayer(float radius, float dx, float dy, long shadowColor) { setShadowLayer(radius, dx, dy, Color.toArgb(shadowColor)); }
    public void clearShadowLayer() { setShadowLayer(0, 0, 0, 0); }
    public boolean hasShadowLayer() { return mShadowRadius > 0 && (mShadowColor >>> 24) != 0; }
    public float getShadowLayerRadius() { return mShadowRadius; }
    public float getShadowLayerDx() { return mShadowDx; }
    public float getShadowLayerDy() { return mShadowDy; }
    public int getShadowLayerColor() { return mShadowColor; }
    public long getShadowLayerColorLong() { return Color.pack(mShadowColor); }
    public Align getTextAlign() { return mAlign; }
    public void setTextAlign(Align align) { mAlign = align; }
    public Locale getTextLocale() { return mLocales.get(0); }
    public LocaleList getTextLocales() { return mLocales; }
    public void setTextLocale(Locale locale) { mLocales = new LocaleList(locale); }
    public void setTextLocales(LocaleList locales) { mLocales = locales; }
    public boolean isElegantTextHeight() { return mElegantTextHeight; }
    public void setElegantTextHeight(boolean elegant) { mElegantTextHeight = elegant; }
    public float getTextSize() { return mTextSize; }
    public void setTextSize(float textSize) { if (textSize > 0) mTextSize = textSize; }
    public float getTextScaleX() { return mTextScaleX; }
    public void setTextScaleX(float scaleX) { mTextScaleX = scaleX; }
    public float getTextSkewX() { return mTextSkewX; }
    public void setTextSkewX(float skewX) { mTextSkewX = skewX; }
    public float getLetterSpacing() { return mLetterSpacing; }
    public void setLetterSpacing(float letterSpacing) { mLetterSpacing = letterSpacing; }
    public float getWordSpacing() { return mWordSpacing; }
    public void setWordSpacing(float wordSpacing) { mWordSpacing = wordSpacing; }
    public String getFontFeatureSettings() { return mFontFeatureSettings; }
    public void setFontFeatureSettings(String settings) { mFontFeatureSettings = settings; }
    public String getFontVariationSettings() { return mFontVariationSettings; }
    public boolean setFontVariationSettings(String fontVariationSettings) { mFontVariationSettings = fontVariationSettings; return true; }
    public int getStartHyphenEdit() { return mStartHyphenEdit; }
    public int getEndHyphenEdit() { return mEndHyphenEdit; }
    public void setStartHyphenEdit(int startHyphen) { mStartHyphenEdit = startHyphen; }
    public void setEndHyphenEdit(int endHyphen) { mEndHyphenEdit = endHyphen; }
    public int getBidiFlags() { return mBidiFlags; }
    public void setBidiFlags(int flags) { mBidiFlags = flags; }

    long fontHandle() { return mTypeface != null ? mTypeface.mNative : Typeface.DEFAULT.mNative; }

    /** Skew actually used when drawing (fake italic for italic typefaces). */
    float effectiveSkew() { return mTextSkewX + (mTypeface != null && mTypeface.mItalic ? -0.25f : 0f); }

    // ---- font metrics ----

    public static class FontMetrics {
        public float top;
        public float ascent;
        public float descent;
        public float bottom;
        public float leading;
    }

    public static class FontMetricsInt {
        public int top;
        public int ascent;
        public int descent;
        public int bottom;
        public int leading;

        public void set(FontMetricsInt fmi) {
            top = fmi.top;
            ascent = fmi.ascent;
            descent = fmi.descent;
            bottom = fmi.bottom;
            leading = fmi.leading;
        }

        @Override
        public String toString() { return "FontMetricsInt: top=" + top + " ascent=" + ascent + " descent=" + descent + " bottom=" + bottom + " leading=" + leading; }
    }

    private final float[] mMetricsTmp = new float[5];

    private float[] metrics() {
        nGetFontMetrics(fontHandle(), mTextSize, mMetricsTmp);
        return mMetricsTmp;
    }

    public float ascent() { return metrics()[0]; }
    public float descent() { return metrics()[1]; }

    public float getFontMetrics(FontMetrics metrics) {
        float[] m = metrics();
        if (metrics != null) {
            metrics.ascent = m[0];
            metrics.descent = m[1];
            metrics.leading = m[2];
            metrics.top = m[3];
            metrics.bottom = m[4];
        }
        return -m[0] + m[1] + m[2];
    }

    public FontMetrics getFontMetrics() {
        FontMetrics fm = new FontMetrics();
        getFontMetrics(fm);
        return fm;
    }

    public int getFontMetricsInt(FontMetricsInt fmi) {
        float[] m = metrics();
        if (fmi != null) {
            fmi.ascent = (int) Math.floor(m[0]);
            fmi.descent = (int) Math.ceil(m[1]);
            fmi.leading = (int) Math.ceil(m[2]);
            fmi.top = (int) Math.floor(m[3]);
            fmi.bottom = (int) Math.ceil(m[4]);
        }
        return (int) Math.ceil(-m[0] + m[1] + m[2]);
    }

    public FontMetricsInt getFontMetricsInt() {
        FontMetricsInt fm = new FontMetricsInt();
        getFontMetricsInt(fm);
        return fm;
    }

    public void getFontMetricsInt(char[] text, int start, int count, int contextStart, int contextCount, boolean isRtl, FontMetricsInt outMetrics) { getFontMetricsInt(outMetrics); }
    public void getFontMetricsInt(CharSequence text, int start, int count, int contextStart, int contextCount, boolean isRtl, FontMetricsInt outMetrics) { getFontMetricsInt(outMetrics); }
    public float getFontSpacing() { return getFontMetrics(null); }

    // ---- measurement ----

    static char[] chars(CharSequence text, int start, int end) {
        char[] buf = new char[end - start];
        TextUtils.getChars(text, start, end, buf, 0);
        return buf;
    }

    float measureChars(char[] text, int index, int count, float[] widths, int wOff) {
        if (count <= 0) return 0;
        float w = nMeasureText(fontHandle(), mTextSize, text, index, count, widths, wOff);
        float sx = mTextScaleX;
        if (mLetterSpacing != 0) {
            float extra = mLetterSpacing * mTextSize;
            w += extra * count;
            if (widths != null) for (int i = 0; i < count; i++) if (widths[wOff + i] != 0) widths[wOff + i] += extra;
        }
        if (mWordSpacing != 0) {
            for (int i = 0; i < count; i++) {
                if (text[index + i] == ' ') {
                    w += mWordSpacing;
                    if (widths != null) widths[wOff + i] += mWordSpacing;
                }
            }
        }
        if (sx != 1f) {
            w *= sx;
            if (widths != null) for (int i = 0; i < count; i++) widths[wOff + i] *= sx;
        }
        if (isFakeBoldText() || (mTypeface != null && mTypeface.mFakeBold)) w += mTextSize / 24f;
        return w;
    }

    public float measureText(char[] text, int index, int count) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((index | count) < 0 || index + count > text.length) throw new ArrayIndexOutOfBoundsException();
        return measureChars(text, index, count, null, 0);
    }

    public float measureText(String text, int start, int end) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        return measureChars(text.toCharArray(), start, end - start, null, 0);
    }

    public float measureText(String text) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        return measureChars(text.toCharArray(), 0, text.length(), null, 0);
    }

    public float measureText(CharSequence text, int start, int end) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        if (text instanceof String) return measureText((String) text, start, end);
        char[] buf = chars(text, start, end);
        return measureChars(buf, 0, buf.length, null, 0);
    }

    public int breakText(char[] text, int index, int count, float maxWidth, float[] measuredWidth) {
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        boolean backwards = count < 0;
        int n = Math.abs(count);
        int start = backwards ? index - n + 1 : index;
        if (start < 0) start = 0;
        float[] widths = new float[n];
        measureChars(text, start, n, widths, 0);
        float total = 0;
        int i = 0;
        if (!backwards) {
            for (; i < n; i++) {
                if (total + widths[i] > maxWidth) break;
                total += widths[i];
            }
        } else {
            for (; i < n; i++) {
                float w = widths[n - 1 - i];
                if (total + w > maxWidth) break;
                total += w;
            }
        }
        if (measuredWidth != null) measuredWidth[0] = total;
        return i;
    }

    public int breakText(CharSequence text, int start, int end, boolean measureForwards, float maxWidth, float[] measuredWidth) {
        char[] buf = chars(text, start, end);
        return breakText(buf, measureForwards ? 0 : buf.length - 1, measureForwards ? buf.length : -buf.length, maxWidth, measuredWidth);
    }

    public int breakText(String text, boolean measureForwards, float maxWidth, float[] measuredWidth) {
        return breakText(text, 0, text.length(), measureForwards, maxWidth, measuredWidth);
    }

    public int getTextWidths(char[] text, int index, int count, float[] widths) {
        if ((index | count) < 0 || index + count > text.length || count > widths.length) throw new ArrayIndexOutOfBoundsException();
        measureChars(text, index, count, widths, 0);
        return count;
    }

    public int getTextWidths(CharSequence text, int start, int end, float[] widths) {
        char[] buf = chars(text, start, end);
        return getTextWidths(buf, 0, buf.length, widths);
    }

    public int getTextWidths(String text, int start, int end, float[] widths) { return getTextWidths((CharSequence) text, start, end, widths); }
    public int getTextWidths(String text, float[] widths) { return getTextWidths(text, 0, text.length(), widths); }

    public float getTextRunAdvances(char[] chars, int index, int count, int contextIndex, int contextCount, boolean isRtl, float[] advances, int advancesIndex) {
        return measureChars(chars, index, count, advances, advancesIndex);
    }

    public float getTextRunAdvances(CharSequence text, int start, int end, int contextStart, int contextEnd, boolean isRtl, float[] advances, int advancesIndex) {
        char[] buf = chars(text, start, end);
        return measureChars(buf, 0, buf.length, advances, advancesIndex);
    }

    public int getTextRunCursor(char[] text, int contextStart, int contextLength, boolean isRtl, int offset, int cursorOpt) {
        return getTextRunCursor(new String(text, contextStart, contextLength), 0, contextLength, isRtl, offset - contextStart, cursorOpt) + contextStart;
    }

    public int getTextRunCursor(CharSequence text, int contextStart, int contextEnd, boolean isRtl, int offset, int cursorOpt) {
        switch (cursorOpt) {
            case CURSOR_AFTER: return Math.min(offset + 1, contextEnd);
            case CURSOR_BEFORE: return Math.max(offset - 1, contextStart);
            case CURSOR_AT_OR_AFTER: case CURSOR_AT_OR_BEFORE: case CURSOR_AT: return offset;
        }
        return -1;
    }

    public float getRunAdvance(char[] text, int start, int end, int contextStart, int contextEnd, boolean isRtl, int offset) {
        return measureChars(text, start, offset - start, null, 0);
    }

    public float getRunAdvance(CharSequence text, int start, int end, int contextStart, int contextEnd, boolean isRtl, int offset) {
        return measureText(text, start, offset);
    }

    public int getOffsetForAdvance(char[] text, int start, int end, int contextStart, int contextEnd, boolean isRtl, float advance) {
        int n = end - start;
        float[] w = new float[n];
        measureChars(text, start, n, w, 0);
        float x = 0;
        for (int i = 0; i < n; i++) {
            if (x + w[i] / 2 > advance) return start + i;
            x += w[i];
        }
        return end;
    }

    public int getOffsetForAdvance(CharSequence text, int start, int end, int contextStart, int contextEnd, boolean isRtl, float advance) {
        char[] buf = chars(text, start, end);
        return start + getOffsetForAdvance(buf, 0, buf.length, 0, buf.length, isRtl, advance);
    }

    public void getTextPath(char[] text, int index, int count, float x, float y, Path path) {
        Object[] r = nGetTextPath(fontHandle(), mTextSize, text, index, count, x, y);
        path.reset();
        if (r == null) return;
        byte[] verbs = (byte[]) r[0];
        float[] pts = (float[]) r[1];
        Path tmp = new Path();
        tmp.mVerbs = verbs.length > 0 ? verbs : new byte[16];
        tmp.mVerbCount = verbs.length;
        tmp.mPts = pts.length > 0 ? pts : new float[32];
        tmp.mPtCount = pts.length;
        path.addPath(tmp);
    }

    public void getTextPath(String text, int start, int end, float x, float y, Path path) {
        getTextPath(text.toCharArray(), start, end - start, x, y, path);
    }

    public void getTextBounds(String text, int start, int end, Rect bounds) { getTextBounds((CharSequence) text, start, end, bounds); }

    public void getTextBounds(CharSequence text, int start, int end, Rect bounds) {
        char[] buf = chars(text, start, end);
        getTextBounds(buf, 0, buf.length, bounds);
    }

    public void getTextBounds(char[] text, int index, int count, Rect bounds) {
        float[] out = new float[4];
        nGetTextBounds(fontHandle(), mTextSize, text, index, count, out);
        bounds.set((int) Math.floor(out[0] * mTextScaleX), (int) Math.floor(out[1]), (int) Math.ceil(out[2] * mTextScaleX), (int) Math.ceil(out[3]));
    }

    public boolean hasGlyph(String string) {
        if (string == null || string.isEmpty()) return false;
        return nHasGlyph(fontHandle(), string.codePointAt(0));
    }

    public boolean equalsForTextMeasurement(Paint other) {
        return mTextSize == other.mTextSize && mTypeface == other.mTypeface && mTextScaleX == other.mTextScaleX && mLetterSpacing == other.mLetterSpacing;
    }

    static native float nMeasureText(long font, float size, char[] text, int start, int count, float[] widths, int widthsOff);
    static native void nGetFontMetrics(long font, float size, float[] out);
    static native void nGetTextBounds(long font, float size, char[] text, int start, int count, float[] out);
    static native Object[] nGetTextPath(long font, float size, char[] text, int start, int count, float x, float y);
    static native boolean nHasGlyph(long font, int codepoint);
}
