package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.style.ParagraphStyle;

/**
 * A single-line layout for text that fits without wrapping and has no
 * paragraph styles (port of AOSP BoringLayout). Ellipsizing keeps the
 * original text and records the elided range, which TextLine draws as an
 * ellipsis.
 */
public class BoringLayout extends Layout implements TextUtils.EllipsizeCallback {
    private static final char FIRST_RIGHT_TO_LEFT = '֐';

    public static class Metrics extends Paint.FontMetricsInt {
        public int width;
        private RectF mDrawingBoundingBox;

        public Metrics() {}

        public RectF getDrawingBoundingBox() { return mDrawingBoundingBox; }

        void reset() {
            top = 0;
            bottom = 0;
            ascent = 0;
            descent = 0;
            width = 0;
            leading = 0;
            mDrawingBoundingBox = null;
        }

        @Override
        public String toString() { return super.toString() + " width=" + width; }
    }

    int mBottom;
    int mDesc;
    private String mDirect;
    private Paint mPaint;
    private int mTopPadding;
    private int mBottomPadding;
    private float mMax;
    private int mEllipsizedWidth;
    private int mEllipsizedStart;
    private int mEllipsizedCount;

    public static BoringLayout make(CharSequence source, TextPaint paint, int outerWidth, Alignment align,
            float spacingMult, float spacingAdd, Metrics metrics, boolean includePad) {
        return new BoringLayout(source, paint, outerWidth, align, spacingMult, spacingAdd, metrics, includePad);
    }

    public static BoringLayout make(CharSequence source, TextPaint paint, int outerWidth, Alignment align,
            float spacingmult, float spacingadd, Metrics metrics, boolean includePad,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        return new BoringLayout(source, paint, outerWidth, align, spacingmult, spacingadd, metrics, includePad,
                ellipsize, ellipsizedWidth);
    }

    public static BoringLayout make(CharSequence source, TextPaint paint, int outerWidth, Alignment align,
            Metrics metrics, boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth,
            boolean useFallbackLineSpacing) {
        return new BoringLayout(source, paint, outerWidth, align, 1f, 0f, metrics, includePad, ellipsize,
                ellipsizedWidth, useFallbackLineSpacing);
    }

    public BoringLayout replaceOrMake(CharSequence source, TextPaint paint, int outerwidth, Alignment align,
            float spacingMult, float spacingAdd, Metrics metrics, boolean includePad) {
        replaceWith(source, paint, outerwidth, align, spacingMult, spacingAdd);
        mEllipsizedWidth = outerwidth;
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;
        init(source, paint, align, metrics, includePad, true);
        return this;
    }

    public BoringLayout replaceOrMake(CharSequence source, TextPaint paint, int outerWidth, Alignment align,
            Metrics metrics, boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth,
            boolean useFallbackLineSpacing) {
        return replaceOrMake(source, paint, outerWidth, align, 1f, 0f, metrics, includePad, ellipsize,
                ellipsizedWidth);
    }

    public BoringLayout replaceOrMake(CharSequence source, TextPaint paint, int outerWidth, Alignment align,
            float spacingMult, float spacingAdd, Metrics metrics, boolean includePad,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        replaceWith(source, paint, outerWidth, align, spacingMult, spacingAdd);
        applyEllipsize(source, paint, outerWidth, metrics, ellipsize, ellipsizedWidth);
        init(source, paint, align, metrics, includePad, true);
        return this;
    }

    public BoringLayout(CharSequence source, TextPaint paint, int outerwidth, Alignment align, float spacingMult,
            float spacingAdd, Metrics metrics, boolean includePad) {
        super(source, paint, outerwidth, align, spacingMult, spacingAdd);
        mEllipsizedWidth = outerwidth;
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;
        init(source, paint, align, metrics, includePad, true);
    }

    public BoringLayout(CharSequence source, TextPaint paint, int outerWidth, Alignment align, float spacingMult,
            float spacingAdd, Metrics metrics, boolean includePad, TextUtils.TruncateAt ellipsize,
            int ellipsizedWidth) {
        this(source, paint, outerWidth, align, spacingMult, spacingAdd, metrics, includePad, ellipsize,
                ellipsizedWidth, false);
    }

    public BoringLayout(CharSequence source, TextPaint paint, int outerWidth, Alignment align, float spacingMult,
            float spacingAdd, Metrics metrics, boolean includePad, TextUtils.TruncateAt ellipsize,
            int ellipsizedWidth, boolean useFallbackLineSpacing) {
        super(source, paint, outerWidth, align, spacingMult, spacingAdd);
        setLayoutParams(includePad, useFallbackLineSpacing, ellipsizedWidth, ellipsize, 1, 0, 0, null, null, 0,
                null, false, false, null);
        applyEllipsize(source, paint, outerWidth, metrics, ellipsize, ellipsizedWidth);
        init(source, paint, align, metrics, includePad, true);
    }

    private void applyEllipsize(CharSequence source, TextPaint paint, int outerWidth, Metrics metrics,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;
        if (ellipsize == null || ellipsize == TextUtils.TruncateAt.MARQUEE) {
            mEllipsizedWidth = outerWidth;
        } else {
            mEllipsizedWidth = ellipsizedWidth;
            if (metrics.width > ellipsizedWidth) {
                TextUtils.ellipsize(source, paint, ellipsizedWidth, ellipsize, true, this);
            }
        }
    }

    void init(CharSequence source, TextPaint paint, Alignment align, Metrics metrics, boolean includePad,
            boolean trustWidth) {
        int spacing;
        if (source instanceof String && align == Alignment.ALIGN_NORMAL) mDirect = source.toString();
        else mDirect = null;
        mPaint = paint;
        if (includePad) {
            spacing = metrics.bottom - metrics.top;
            mDesc = metrics.bottom;
        } else {
            spacing = metrics.descent - metrics.ascent;
            mDesc = metrics.descent;
        }
        mBottom = spacing;
        if (trustWidth && mEllipsizedCount == 0) {
            mMax = metrics.width;
        } else {
            TextLine line = TextLine.obtain();
            line.set(paint, source, 0, source.length(), false, null, mEllipsizedStart,
                    mEllipsizedStart + mEllipsizedCount);
            mMax = (int) Math.ceil(line.metrics(null));
            TextLine.recycle(line);
        }
        if (includePad) {
            mTopPadding = metrics.top - metrics.ascent;
            mBottomPadding = metrics.bottom - metrics.descent;
        }
    }

    public static Metrics isBoring(CharSequence text, TextPaint paint) {
        return isBoring(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, null);
    }

    public static Metrics isBoring(CharSequence text, TextPaint paint, Metrics metrics) {
        return isBoring(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, metrics);
    }

    /** Hidden AOSP API. */
    public static Metrics isBoring(CharSequence text, TextPaint paint, TextDirectionHeuristic textDir, Metrics metrics) {
        return isBoring(text, paint, textDir, false, metrics);
    }

    public static Metrics isBoring(CharSequence text, TextPaint paint, TextDirectionHeuristic textDir,
            boolean useFallbackLineSpacing, Metrics metrics) {
        final int textLength = text.length();
        if (hasAnyInterestingChars(text, textLength)) return null;
        if (textDir != null && textDir.isRtl(text, 0, textLength)) return null;
        if (text instanceof Spanned) {
            Spanned sp = (Spanned) text;
            Object[] styles = sp.getSpans(0, textLength, ParagraphStyle.class);
            if (styles.length > 0) return null;
        }
        Metrics fm = metrics;
        if (fm == null) fm = new Metrics();
        else fm.reset();
        TextLine line = TextLine.obtain();
        line.set(paint, text, 0, textLength, false, null, 0, 0);
        fm.width = (int) Math.ceil(line.metrics(fm));
        TextLine.recycle(line);
        return fm;
    }

    private static boolean hasAnyInterestingChars(CharSequence text, int textLength) {
        for (int i = 0; i < textLength; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t') return true;
            if (c >= FIRST_RIGHT_TO_LEFT && ((c <= 0x08FF) || c == 0x200F || (c >= 0x202A && c <= 0x202E)
                    || (c >= 0x2066 && c <= 0x2069) || (c >= 0xFB1D && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFE))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getHeight() { return mBottom; }

    @Override
    public int getLineCount() { return 1; }

    @Override
    public int getLineTop(int line) {
        if (line == 0) return 0;
        return mBottom;
    }

    @Override
    public int getLineDescent(int line) { return mDesc; }

    @Override
    public int getLineStart(int line) {
        if (line == 0) return 0;
        return getText().length();
    }

    @Override
    public int getParagraphDirection(int line) { return DIR_LEFT_TO_RIGHT; }

    @Override
    public boolean getLineContainsTab(int line) { return false; }

    @Override
    public float getLineMax(int line) { return mMax; }

    @Override
    public float getLineWidth(int line) { return (line == 0 ? mMax : 0); }

    @Override
    public final Directions getLineDirections(int line) { return Layout.DIRS_ALL_LEFT_TO_RIGHT; }

    @Override
    public int getTopPadding() { return mTopPadding; }

    @Override
    public int getBottomPadding() { return mBottomPadding; }

    @Override
    public int getEllipsisCount(int line) { return mEllipsizedCount; }

    @Override
    public int getEllipsisStart(int line) { return mEllipsizedStart; }

    @Override
    public int getEllipsizedWidth() { return mEllipsizedWidth; }

    @Override
    public boolean isFallbackLineSpacingEnabled() { return super.isFallbackLineSpacingEnabled(); }

    @Override
    public RectF computeDrawingBoundingBox() { return super.computeDrawingBoundingBox(); }

    @Override
    public void draw(Canvas c, Path highlight, Paint highlightpaint, int cursorOffset) {
        if (mDirect != null && highlight == null && mEllipsizedCount == 0 && !(getText() instanceof Spanned)) {
            c.drawText(mDirect, 0, mBottom - mDesc, mPaint);
        } else {
            super.draw(c, highlight, highlightpaint, cursorOffset);
        }
    }

    public void ellipsized(int start, int end) {
        mEllipsizedStart = start;
        mEllipsizedCount = end - start;
    }
}
