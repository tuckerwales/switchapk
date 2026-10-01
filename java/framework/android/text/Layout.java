package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.text.LineBreakConfig;
import android.text.style.AlignmentSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.ParagraphStyle;
import android.text.style.ReplacementSpan;
import android.text.style.TabStopSpan;
import java.util.List;

/**
 * Base class for text layouts (port of AOSP android.text.Layout). Text is laid
 * out left to right only; paragraph direction is always LTR.
 */
public abstract class Layout {
    public static final int BREAK_STRATEGY_SIMPLE = 0;
    public static final int BREAK_STRATEGY_HIGH_QUALITY = 1;
    public static final int BREAK_STRATEGY_BALANCED = 2;
    public static final int HYPHENATION_FREQUENCY_NONE = 0;
    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;
    public static final int HYPHENATION_FREQUENCY_FULL = 2;
    public static final int HYPHENATION_FREQUENCY_NORMAL_FAST = 3;
    public static final int HYPHENATION_FREQUENCY_FULL_FAST = 4;
    public static final int JUSTIFICATION_MODE_NONE = 0;
    public static final int JUSTIFICATION_MODE_INTER_WORD = 1;
    public static final int JUSTIFICATION_MODE_INTER_CHARACTER = 2;
    public static final float DEFAULT_LINESPACING_MULTIPLIER = 1.0f;
    public static final float DEFAULT_LINESPACING_ADDITION = 0.0f;
    public static final int DIR_LEFT_TO_RIGHT = 1;
    public static final int DIR_RIGHT_TO_LEFT = -1;

    static final int DIR_REQUEST_LTR = 1;
    static final int DIR_REQUEST_RTL = -1;
    static final int DIR_REQUEST_DEFAULT_LTR = 2;
    static final int DIR_REQUEST_DEFAULT_RTL = -2;
    static final int RUN_LENGTH_MASK = 0x03ffffff;
    static final int RUN_LEVEL_SHIFT = 26;
    static final int RUN_LEVEL_MASK = 0x3f;
    static final int RUN_RTL_FLAG = 1 << RUN_LEVEL_SHIFT;

    private static final float TAB_INCREMENT = 20;

    public interface TextInclusionStrategy {
        boolean isSegmentInside(RectF segmentBounds, RectF area);
    }

    public static final TextInclusionStrategy INCLUSION_STRATEGY_ANY_OVERLAP = new TextInclusionStrategy() {
        public boolean isSegmentInside(RectF segmentBounds, RectF area) { return RectF.intersects(segmentBounds, area); }
    };

    public static final TextInclusionStrategy INCLUSION_STRATEGY_CONTAINS_CENTER = new TextInclusionStrategy() {
        public boolean isSegmentInside(RectF segmentBounds, RectF area) {
            return area.contains(segmentBounds.centerX(), segmentBounds.centerY());
        }
    };

    public static final TextInclusionStrategy INCLUSION_STRATEGY_CONTAINS_ALL = new TextInclusionStrategy() {
        public boolean isSegmentInside(RectF segmentBounds, RectF area) { return area.contains(segmentBounds); }
    };

    public enum Alignment {
        ALIGN_NORMAL,
        ALIGN_OPPOSITE,
        ALIGN_CENTER,
        /** @hide */
        ALIGN_LEFT,
        /** @hide */
        ALIGN_RIGHT,
    }

    /** Bidi run information for a line. Every line here is a single LTR run. */
    public static class Directions {
        public int[] mDirections;

        Directions() {}

        /** Hidden AOSP constructor. */
        public Directions(int[] dirs) { mDirections = dirs; }

        public int getRunCount() { return mDirections.length / 2; }

        public int getRunStart(int runIndex) { return mDirections[runIndex * 2]; }

        public int getRunLength(int runIndex) { return mDirections[runIndex * 2 + 1] & RUN_LENGTH_MASK; }

        public boolean isRunRtl(int runIndex) { return (mDirections[runIndex * 2 + 1] & RUN_RTL_FLAG) != 0; }
    }

    /** Hidden AOSP constants. */
    public static final Directions DIRS_ALL_LEFT_TO_RIGHT = new Directions(new int[] {0, RUN_LENGTH_MASK});
    public static final Directions DIRS_ALL_RIGHT_TO_LEFT = new Directions(new int[] {0, RUN_LENGTH_MASK | RUN_RTL_FLAG});

    private CharSequence mText;
    private TextPaint mPaint;
    private TextPaint mWorkPaint = new TextPaint();
    private int mWidth;
    private Alignment mAlignment = Alignment.ALIGN_NORMAL;
    private float mSpacingMult;
    private float mSpacingAdd;
    private boolean mSpannedText;
    private TextDirectionHeuristic mTextDir;
    private boolean mIncludePad = true;
    private boolean mFallbackLineSpacing;
    private int mEllipsizedWidth;
    private TextUtils.TruncateAt mEllipsize;
    private int mMaxLines = Integer.MAX_VALUE;
    private int mBreakStrategy;
    private int mHyphenationFrequency;
    private int[] mLeftIndents;
    private int[] mRightIndents;
    private int mJustificationMode;
    private LineBreakConfig mLineBreakConfig;
    private boolean mUseBoundsForWidth;
    private boolean mShiftDrawingOffsetForStartOverhang;
    private Paint.FontMetrics mMinimumFontMetrics;

    protected Layout(CharSequence text, TextPaint paint, int width, Alignment align, float spacingMult,
            float spacingAdd) {
        this(text, paint, width, align, TextDirectionHeuristics.FIRSTSTRONG_LTR, spacingMult, spacingAdd);
    }

    /** Hidden AOSP constructor. */
    protected Layout(CharSequence text, TextPaint paint, int width, Alignment align, TextDirectionHeuristic textDir,
            float spacingMult, float spacingAdd) {
        if (width < 0) throw new IllegalArgumentException("Layout: " + width + " < 0");
        if (paint != null) {
            paint.bgColor = 0;
            paint.baselineShift = 0;
        }
        mText = text;
        mPaint = paint;
        mWidth = width;
        mAlignment = align;
        mSpacingMult = spacingMult;
        mSpacingAdd = spacingAdd;
        mSpannedText = text instanceof Spanned;
        mTextDir = textDir;
        mEllipsizedWidth = width;
    }

    /** Package-private setters used by subclass builders (AOSP sets these through its long constructor). */
    void setLayoutParams(boolean includePad, boolean fallbackLineSpacing, int ellipsizedWidth,
            TextUtils.TruncateAt ellipsize, int maxLines, int breakStrategy, int hyphenationFrequency,
            int[] leftIndents, int[] rightIndents, int justificationMode, LineBreakConfig lineBreakConfig,
            boolean useBoundsForWidth, boolean shiftDrawingOffsetForStartOverhang, Paint.FontMetrics minimumFontMetrics) {
        mIncludePad = includePad;
        mFallbackLineSpacing = fallbackLineSpacing;
        mEllipsizedWidth = ellipsizedWidth;
        mEllipsize = ellipsize;
        mMaxLines = maxLines;
        mBreakStrategy = breakStrategy;
        mHyphenationFrequency = hyphenationFrequency;
        mLeftIndents = leftIndents;
        mRightIndents = rightIndents;
        mJustificationMode = justificationMode;
        mLineBreakConfig = lineBreakConfig;
        mUseBoundsForWidth = useBoundsForWidth;
        mShiftDrawingOffsetForStartOverhang = shiftDrawingOffsetForStartOverhang;
        mMinimumFontMetrics = minimumFontMetrics;
    }

    void replaceWith(CharSequence text, TextPaint paint, int width, Alignment align, float spacingmult,
            float spacingadd) {
        if (width < 0) throw new IllegalArgumentException("Layout: " + width + " < 0");
        mText = text;
        mPaint = paint;
        mWidth = width;
        mAlignment = align;
        mSpacingMult = spacingmult;
        mSpacingAdd = spacingadd;
        mSpannedText = text instanceof Spanned;
    }

    // ---- desired width ----

    public static float getDesiredWidth(CharSequence source, TextPaint paint) {
        return getDesiredWidth(source, 0, source.length(), paint);
    }

    public static float getDesiredWidth(CharSequence source, int start, int end, TextPaint paint) {
        return getDesiredWidth(source, start, end, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR);
    }

    /** Hidden AOSP API. */
    public static float getDesiredWidth(CharSequence source, int start, int end, TextPaint paint,
            TextDirectionHeuristic textDir) {
        return getDesiredWidthWithLimit(source, start, end, paint, textDir, Float.MAX_VALUE, false);
    }

    /** Hidden AOSP API: widest paragraph, stopping early once {@code upperLimit} is exceeded. */
    public static float getDesiredWidthWithLimit(CharSequence source, int start, int end, TextPaint paint,
            TextDirectionHeuristic textDir, float upperLimit, boolean useBoundsForWidth) {
        float need = 0;
        int next;
        for (int i = start; i <= end; i = next) {
            next = TextUtils.indexOf(source, '\n', i, end);
            if (next < 0) next = end;
            float w = measurePara(paint, source, i, next);
            if (w > upperLimit) return upperLimit;
            if (w > need) need = w;
            next++;
        }
        return need;
    }

    private static float measurePara(TextPaint paint, CharSequence text, int start, int end) {
        TextLine tl = TextLine.obtain();
        try {
            boolean hasTabs = false;
            TextLine.TabStops tabStops = null;
            int margin = 0;
            if (text instanceof Spanned) {
                Spanned spanned = (Spanned) text;
                LeadingMarginSpan[] spans = getParagraphSpans(spanned, start, end, LeadingMarginSpan.class);
                for (LeadingMarginSpan lms : spans) margin += lms.getLeadingMargin(true);
            }
            for (int i = start; i < end; i++) {
                if (text.charAt(i) == '\t') {
                    hasTabs = true;
                    if (text instanceof Spanned) {
                        Spanned spanned = (Spanned) text;
                        int spanEnd = spanned.nextSpanTransition(start, end, TabStopSpan.class);
                        TabStopSpan[] spans = getParagraphSpans(spanned, start, spanEnd, TabStopSpan.class);
                        if (spans.length > 0) tabStops = new TextLine.TabStops(TAB_INCREMENT, spans);
                    }
                    break;
                }
            }
            tl.set(paint, text, start, end, hasTabs, tabStops, 0, 0);
            return margin + Math.abs(tl.metrics(null));
        } finally {
            TextLine.recycle(tl);
        }
    }

    // ---- drawing ----

    public void draw(Canvas c) { draw(c, (Path) null, (Paint) null, 0); }

    public void draw(Canvas canvas, Path selectionHighlight, Paint selectionHighlightPaint, int cursorOffsetVertical) {
        draw(canvas, null, null, selectionHighlight, selectionHighlightPaint, cursorOffsetVertical);
    }

    public void draw(Canvas canvas, List<Path> highlightPaths, List<Paint> highlightPaints, Path selectionPath,
            Paint selectionPaint, int cursorOffsetVertical) {
        final long lineRange = getLineRangeForDraw(canvas);
        int firstLine = (int) (lineRange >>> 32);
        int lastLine = (int) (lineRange & 0xFFFFFFFFL);
        if (lastLine < 0) return;
        drawWithoutText(canvas, highlightPaths, highlightPaints, selectionPath, selectionPaint, cursorOffsetVertical,
                firstLine, lastLine);
        drawText(canvas, firstLine, lastLine);
    }

    public void drawText(Canvas canvas) {
        final long lineRange = getLineRangeForDraw(canvas);
        int firstLine = (int) (lineRange >>> 32);
        int lastLine = (int) (lineRange & 0xFFFFFFFFL);
        if (lastLine < 0) return;
        drawText(canvas, firstLine, lastLine);
    }

    public void drawBackground(Canvas canvas) {
        final long lineRange = getLineRangeForDraw(canvas);
        int firstLine = (int) (lineRange >>> 32);
        int lastLine = (int) (lineRange & 0xFFFFFFFFL);
        if (lastLine < 0) return;
        drawBackground(canvas, firstLine, lastLine);
    }

    /** Hidden AOSP API. */
    public void drawWithoutText(Canvas canvas, List<Path> highlightPaths, List<Paint> highlightPaints,
            Path selectionPath, Paint selectionPaint, int cursorOffsetVertical, int firstLine, int lastLine) {
        drawBackground(canvas, firstLine, lastLine);
        if (highlightPaths == null && highlightPaints == null && selectionPath == null) return;
        if (cursorOffsetVertical != 0) canvas.translate(0, cursorOffsetVertical);
        if (highlightPaths != null) {
            for (int i = 0; i < highlightPaths.size(); i++) {
                final Path highlight = highlightPaths.get(i);
                final Paint highlightPaint = highlightPaints.get(i);
                if (highlight != null) canvas.drawPath(highlight, highlightPaint);
            }
        }
        if (selectionPath != null) canvas.drawPath(selectionPath, selectionPaint);
        if (cursorOffsetVertical != 0) canvas.translate(0, -cursorOffsetVertical);
    }

    /** Hidden AOSP API. */
    public void drawBackground(Canvas canvas, int firstLine, int lastLine) {
        if (!mSpannedText) return;
        Spanned buffer = (Spanned) mText;
        int textLength = buffer.length();
        LineBackgroundSpan[] spans = buffer.getSpans(0, textLength, LineBackgroundSpan.class);
        if (spans.length == 0) return;
        final TextPaint paint = mPaint;
        int previousLineBottom = getLineTop(firstLine);
        int previousLineEnd = getLineStart(firstLine);
        for (int i = firstLine; i <= lastLine; i++) {
            int start = previousLineEnd;
            int end = getLineStart(i + 1);
            previousLineEnd = end;
            int ltop = previousLineBottom;
            int lbottom = getLineTop(i + 1);
            previousLineBottom = lbottom;
            int lbaseline = lbottom - getLineDescent(i);
            for (LineBackgroundSpan span : spans) {
                int ss = buffer.getSpanStart(span);
                int se = buffer.getSpanEnd(span);
                if (ss >= end || se <= start) {
                    if (!(ss == se && ss == start)) continue;
                }
                span.drawBackground(canvas, paint, 0, mWidth, ltop, lbaseline, lbottom, buffer, start, end, i);
            }
        }
    }

    /** Hidden AOSP API. */
    public void drawText(Canvas canvas, int firstLine, int lastLine) {
        int previousLineBottom = getLineTop(firstLine);
        int previousLineEnd = getLineStart(firstLine);
        ParagraphStyle[] spans = NO_PARA_SPANS;
        int spanEnd = 0;
        final TextPaint paint = mWorkPaint;
        paint.set(mPaint);
        CharSequence buf = mText;
        Alignment paraAlign = mAlignment;
        TextLine.TabStops tabStops = null;
        boolean tabStopsIsInitialized = false;
        TextLine tl = TextLine.obtain();
        for (int lineNum = firstLine; lineNum <= lastLine; lineNum++) {
            int start = previousLineEnd;
            previousLineEnd = getLineStart(lineNum + 1);
            final int end = getLineVisibleEnd(lineNum, start, previousLineEnd, true);
            int ltop = previousLineBottom;
            int lbottom = getLineTop(lineNum + 1);
            previousLineBottom = lbottom;
            int lbaseline = lbottom - getLineDescent(lineNum);
            int left = 0;
            int right = mWidth;
            boolean hasTab = getLineContainsTab(lineNum);
            if (mSpannedText) {
                Spanned sp = (Spanned) buf;
                int textLength = buf.length();
                boolean isFirstParaLine = (start == 0 || buf.charAt(start - 1) == '\n');
                if (start >= spanEnd && (lineNum == firstLine || isFirstParaLine)) {
                    spanEnd = sp.nextSpanTransition(start, textLength, ParagraphStyle.class);
                    spans = getParagraphSpans(sp, start, spanEnd, ParagraphStyle.class);
                    paraAlign = mAlignment;
                    for (int n = spans.length - 1; n >= 0; n--) {
                        if (spans[n] instanceof AlignmentSpan) {
                            paraAlign = ((AlignmentSpan) spans[n]).getAlignment();
                            break;
                        }
                    }
                    tabStopsIsInitialized = false;
                }
                final int length = spans.length;
                boolean useFirstLineMargin = isFirstParaLine;
                for (int n = 0; n < length; n++) {
                    if (spans[n] instanceof LeadingMarginSpan.LeadingMarginSpan2) {
                        int count = ((LeadingMarginSpan.LeadingMarginSpan2) spans[n]).getLeadingMarginLineCount();
                        int startLine = getLineForOffset(sp.getSpanStart(spans[n]));
                        if (lineNum < startLine + count) {
                            useFirstLineMargin = true;
                            break;
                        }
                    }
                }
                for (int n = 0; n < length; n++) {
                    if (spans[n] instanceof LeadingMarginSpan) {
                        LeadingMarginSpan margin = (LeadingMarginSpan) spans[n];
                        margin.drawLeadingMargin(canvas, paint, left, DIR_LEFT_TO_RIGHT, ltop, lbaseline, lbottom, buf,
                                start, end, isFirstParaLine, this);
                        left += margin.getLeadingMargin(useFirstLineMargin);
                    }
                }
            }
            if (hasTab && !tabStopsIsInitialized) {
                if (tabStops == null) tabStops = new TextLine.TabStops(TAB_INCREMENT, spans);
                else tabStops.reset(TAB_INCREMENT, spans);
                tabStopsIsInitialized = true;
            }
            left += getIndentAdjust(lineNum, Alignment.ALIGN_LEFT);
            right -= getIndentAdjust(lineNum, Alignment.ALIGN_RIGHT);
            int x;
            if (paraAlign == Alignment.ALIGN_NORMAL || paraAlign == Alignment.ALIGN_LEFT) {
                x = left;
            } else {
                int max = (int) getLineExtent(lineNum, tabStops, false);
                if (paraAlign == Alignment.ALIGN_OPPOSITE || paraAlign == Alignment.ALIGN_RIGHT) {
                    x = right - max;
                } else {
                    max = max & ~1;
                    x = (right + left - max) >> 1;
                }
            }
            int ellipsisStart = getEllipsisStart(lineNum);
            int ellipsisCount = getEllipsisCount(lineNum);
            tl.set(paint, buf, start, end, hasTab, tabStops, ellipsisStart, ellipsisStart + ellipsisCount);
            tl.draw(canvas, x, ltop, lbaseline, lbottom);
        }
        TextLine.recycle(tl);
    }

    private static final ParagraphStyle[] NO_PARA_SPANS = new ParagraphStyle[0];

    /** Hidden AOSP API: first and last line intersecting the canvas clip, packed in a long. */
    public long getLineRangeForDraw(Canvas canvas) {
        int dtop, dbottom;
        Rect r = new Rect();
        if (!canvas.getClipBounds(r)) return ((long) 0 << 32) | (0xFFFFFFFFL & -1);
        dtop = r.top;
        dbottom = r.bottom;
        final int top = Math.max(dtop, 0);
        final int bottom = Math.min(getLineTop(getLineCount()), dbottom);
        if (top >= bottom) return ((long) 0 << 32) | (0xFFFFFFFFL & -1);
        return ((long) getLineForVertical(top) << 32) | (0xFFFFFFFFL & getLineForVertical(bottom));
    }

    // ---- line geometry ----

    public final void increaseWidthTo(int wid) {
        if (wid < mWidth) throw new RuntimeException("attempted to reduce Layout width");
        mWidth = wid;
    }

    public int getHeight() { return getLineTop(getLineCount()); }

    /** Hidden AOSP API. */
    public int getHeight(boolean cap) { return getHeight(); }

    public abstract int getLineCount();

    public RectF computeDrawingBoundingBox() {
        float left = 0;
        float right = 0;
        for (int line = 0; line < getLineCount(); ++line) {
            float l = getLineLeft(line);
            float r = getLineRight(line);
            if (line == 0 || l < left) left = l;
            if (line == 0 || r > right) right = r;
        }
        return new RectF(left, 0, right, getHeight());
    }

    public int getLineBounds(int line, Rect bounds) {
        if (bounds != null) {
            bounds.left = 0;
            bounds.top = getLineTop(line);
            bounds.right = mWidth;
            bounds.bottom = getLineTop(line + 1);
        }
        return getLineBaseline(line);
    }

    public abstract int getLineTop(int line);

    public abstract int getLineDescent(int line);

    public abstract int getLineStart(int line);

    public abstract int getParagraphDirection(int line);

    public abstract boolean getLineContainsTab(int line);

    public abstract Directions getLineDirections(int line);

    public abstract int getTopPadding();

    public abstract int getBottomPadding();

    /** Hidden AOSP API. */
    public int getHyphen(int line) { return 0; }

    /** Hidden AOSP API. */
    public int getIndentAdjust(int line, Alignment alignment) {
        if (alignment == Alignment.ALIGN_LEFT && mLeftIndents != null && mLeftIndents.length > 0) {
            return mLeftIndents[Math.min(line, mLeftIndents.length - 1)];
        }
        if (alignment == Alignment.ALIGN_RIGHT && mRightIndents != null && mRightIndents.length > 0) {
            return mRightIndents[Math.min(line, mRightIndents.length - 1)];
        }
        return 0;
    }

    /** Hidden AOSP API. */
    public boolean isLevelBoundary(int offset) { return false; }

    public boolean isRtlCharAt(int offset) { return false; }

    /** Hidden AOSP API. */
    public long getRunRange(int offset) { return ((long) 0 << 32) | getText().length(); }

    /** Hidden AOSP API. */
    public boolean primaryIsTrailingPrevious(int offset) { return false; }

    public float getPrimaryHorizontal(int offset) { return getHorizontal(offset, false); }

    /** Hidden AOSP API. */
    public float getPrimaryHorizontal(int offset, boolean clamped) { return getHorizontal(offset, clamped); }

    public float getSecondaryHorizontal(int offset) { return getHorizontal(offset, false); }

    /** Hidden AOSP API. */
    public float getSecondaryHorizontal(int offset, boolean clamped) { return getHorizontal(offset, clamped); }

    private float getHorizontal(int offset, boolean clamped) {
        int line = getLineForOffset(offset);
        return getHorizontal(offset, line, clamped);
    }

    private float getHorizontal(int offset, int line, boolean clamped) {
        int start = getLineStart(line);
        int end = getLineEnd(line);
        boolean hasTab = getLineContainsTab(line);
        TextLine.TabStops tabStops = null;
        if (hasTab && mText instanceof Spanned) {
            TabStopSpan[] tabs = getParagraphSpans((Spanned) mText, start, end, TabStopSpan.class);
            if (tabs.length > 0) tabStops = new TextLine.TabStops(TAB_INCREMENT, tabs);
        }
        TextLine tl = TextLine.obtain();
        int ellipsisStart = getEllipsisStart(line);
        tl.set(mPaint, mText, start, end, hasTab, tabStops, ellipsisStart, ellipsisStart + getEllipsisCount(line));
        float wid = tl.measure(offset - start);
        TextLine.recycle(tl);
        if (clamped && wid > mWidth) wid = mWidth;
        int left = getParagraphLeft(line);
        int right = getParagraphRight(line);
        return getLineStartPos(line, left, right) + wid;
    }

    public void fillCharacterBounds(int start, int end, float[] bounds, int boundsStart) {
        if (start < 0 || end < start || end > mText.length()) throw new IndexOutOfBoundsException();
        if (bounds == null) throw new IllegalArgumentException("bounds can't be null.");
        for (int i = start; i < end; i++) {
            int line = getLineForOffset(i);
            float l = getPrimaryHorizontal(i);
            float r = i + 1 <= getLineEnd(line) ? getHorizontal(i + 1, line, false) : l;
            int idx = boundsStart + (i - start) * 4;
            bounds[idx] = l;
            bounds[idx + 1] = getLineTop(line);
            bounds[idx + 2] = r;
            bounds[idx + 3] = getLineBottom(line);
        }
    }

    public float getLineLeft(int line) {
        final Alignment align = getParagraphAlignment(line);
        if (align == Alignment.ALIGN_NORMAL || align == Alignment.ALIGN_LEFT) return getParagraphLeft(line);
        float max = getLineMax(line);
        if (align == Alignment.ALIGN_OPPOSITE || align == Alignment.ALIGN_RIGHT) return mWidth - max;
        int left = getParagraphLeft(line);
        int right = getParagraphRight(line);
        int imax = ((int) max) & ~1;
        return left + ((right - left) - imax) / 2;
    }

    public float getLineRight(int line) {
        final Alignment align = getParagraphAlignment(line);
        if (align == Alignment.ALIGN_NORMAL || align == Alignment.ALIGN_LEFT) {
            return getParagraphLeft(line) + getLineMax(line);
        }
        if (align == Alignment.ALIGN_OPPOSITE || align == Alignment.ALIGN_RIGHT) return mWidth;
        int left = getParagraphLeft(line);
        int right = getParagraphRight(line);
        int imax = ((int) getLineMax(line)) & ~1;
        return right - ((right - left) - imax) / 2;
    }

    public float getLineMax(int line) {
        float margin = getParagraphLeadingMargin(line) + getIndentAdjust(line, Alignment.ALIGN_LEFT);
        float signedExtent = getLineExtent(line, false);
        return margin + (signedExtent >= 0 ? signedExtent : -signedExtent);
    }

    public float getLineWidth(int line) {
        float margin = getParagraphLeadingMargin(line) + getIndentAdjust(line, Alignment.ALIGN_LEFT);
        float signedExtent = getLineExtent(line, true);
        return margin + (signedExtent >= 0 ? signedExtent : -signedExtent);
    }

    public int getLineLetterSpacingUnitCount(int line, boolean includeTrailingWhitespace) {
        final int start = getLineStart(line);
        final int end = includeTrailingWhitespace ? getLineEnd(line) : getLineVisibleEnd(line);
        int count = 0;
        for (int i = start; i < end;) {
            int cp = Character.codePointAt(mText, i);
            count++;
            i += Character.charCount(cp);
        }
        return count;
    }

    private float getLineExtent(int line, boolean full) {
        final int start = getLineStart(line);
        final int end = full ? getLineEnd(line) : getLineVisibleEnd(line);
        final boolean hasTabs = getLineContainsTab(line);
        TextLine.TabStops tabStops = null;
        if (hasTabs && mText instanceof Spanned) {
            TabStopSpan[] tabs = getParagraphSpans((Spanned) mText, start, end, TabStopSpan.class);
            if (tabs.length > 0) tabStops = new TextLine.TabStops(TAB_INCREMENT, tabs);
        }
        return lineWidth(line, start, end, hasTabs, tabStops);
    }

    private float getLineExtent(int line, TextLine.TabStops tabStops, boolean full) {
        final int start = getLineStart(line);
        final int end = full ? getLineEnd(line) : getLineVisibleEnd(line);
        return lineWidth(line, start, end, getLineContainsTab(line), tabStops);
    }

    private float lineWidth(int line, int start, int end, boolean hasTabs, TextLine.TabStops tabStops) {
        TextLine tl = TextLine.obtain();
        int ellipsisStart = getEllipsisStart(line);
        int ellipsisEnd = Math.min(ellipsisStart + getEllipsisCount(line), end - start);
        tl.set(mPaint, mText, start, end, hasTabs, tabStops, ellipsisStart, ellipsisEnd);
        float width = tl.metrics(null);
        TextLine.recycle(tl);
        return width;
    }

    private int getLineStartPos(int line, int left, int right) {
        Alignment align = getParagraphAlignment(line);
        int x;
        if (align == Alignment.ALIGN_NORMAL || align == Alignment.ALIGN_LEFT) {
            x = left + getIndentAdjust(line, Alignment.ALIGN_LEFT);
        } else {
            TextLine.TabStops tabStops = null;
            if (mSpannedText && getLineContainsTab(line)) {
                Spanned spanned = (Spanned) mText;
                int start = getLineStart(line);
                int spanEnd = spanned.nextSpanTransition(start, spanned.length(), TabStopSpan.class);
                TabStopSpan[] tabSpans = getParagraphSpans(spanned, start, spanEnd, TabStopSpan.class);
                if (tabSpans.length > 0) tabStops = new TextLine.TabStops(TAB_INCREMENT, tabSpans);
            }
            int max = (int) getLineExtent(line, tabStops, false);
            if (align == Alignment.ALIGN_OPPOSITE || align == Alignment.ALIGN_RIGHT) {
                x = right - max - getIndentAdjust(line, Alignment.ALIGN_RIGHT);
            } else {
                left += getIndentAdjust(line, Alignment.ALIGN_LEFT);
                right -= getIndentAdjust(line, Alignment.ALIGN_RIGHT);
                max = max & ~1;
                x = (left + right - max) >> 1;
            }
        }
        return x;
    }

    public int getLineForVertical(int vertical) {
        int high = getLineCount(), low = -1, guess;
        while (high - low > 1) {
            guess = (high + low) / 2;
            if (getLineTop(guess) > vertical) high = guess;
            else low = guess;
        }
        if (low < 0) return 0;
        return low;
    }

    public int getLineForOffset(int offset) {
        int high = getLineCount(), low = -1, guess;
        while (high - low > 1) {
            guess = (high + low) / 2;
            if (getLineStart(guess) > offset) high = guess;
            else low = guess;
        }
        if (low < 0) return 0;
        return low;
    }

    public int getOffsetForHorizontal(int line, float horiz) { return getOffsetForHorizontal(line, horiz, true); }

    /** Hidden AOSP API. */
    public int getOffsetForHorizontal(int line, float horiz, boolean primary) {
        final int lineEndOffset = getLineEnd(line);
        final int lineStartOffset = getLineStart(line);
        final boolean hasTab = getLineContainsTab(line);
        TextLine.TabStops tabStops = null;
        if (hasTab && mText instanceof Spanned) {
            TabStopSpan[] tabs = getParagraphSpans((Spanned) mText, lineStartOffset, lineEndOffset, TabStopSpan.class);
            if (tabs.length > 0) tabStops = new TextLine.TabStops(TAB_INCREMENT, tabs);
        }
        final int visibleEnd = getLineVisibleEnd(line);
        TextLine tl = TextLine.obtain();
        int ellipsisStart = getEllipsisStart(line);
        tl.set(mPaint, mText, lineStartOffset, visibleEnd, hasTab, tabStops, ellipsisStart,
                Math.min(ellipsisStart + getEllipsisCount(line), visibleEnd - lineStartOffset));
        float start = getLineStartPos(line, getParagraphLeft(line), getParagraphRight(line));
        int off = lineStartOffset + tl.getOffsetForAdvance(horiz - start);
        TextLine.recycle(tl);
        if (off > visibleEnd) off = visibleEnd;
        if (line < getLineCount() - 1 && off >= lineEndOffset) off = Math.max(lineStartOffset, lineEndOffset - 1);
        return off;
    }

    public int[] getRangeForRect(RectF area, SegmentFinder segmentFinder, TextInclusionStrategy inclusionStrategy) {
        int startLine = getLineForVertical((int) area.top);
        if (area.top > getLineBottom(startLine, false)) {
            startLine++;
            if (startLine >= getLineCount()) return null;
        }
        int endLine = getLineForVertical((int) area.bottom);
        if (endLine == 0 && area.bottom < getLineTop(0)) return null;
        if (endLine < startLine) return null;
        int start = -1;
        int end = -1;
        RectF bounds = new RectF();
        for (int line = startLine; line <= endLine; line++) {
            int ls = getLineStart(line);
            int le = getLineVisibleEnd(line);
            for (int seg = segmentFinder.nextStartBoundary(ls - 1); seg != SegmentFinder.DONE && seg < le;
                    seg = segmentFinder.nextStartBoundary(seg)) {
                int segEnd = segmentFinder.nextEndBoundary(seg);
                if (segEnd == SegmentFinder.DONE) break;
                bounds.set(getPrimaryHorizontal(seg), getLineTop(line),
                        segEnd <= le ? getHorizontal(segEnd, line, false) : getLineRight(line), getLineBottom(line));
                if (inclusionStrategy.isSegmentInside(bounds, area)) {
                    if (start < 0) start = seg;
                    end = segEnd;
                }
            }
        }
        if (start < 0) return null;
        return new int[] {start, end};
    }

    public final int getLineEnd(int line) { return getLineStart(line + 1); }

    public int getLineVisibleEnd(int line) {
        return getLineVisibleEnd(line, getLineStart(line), getLineStart(line + 1), false);
    }

    private int getLineVisibleEnd(int line, int start, int end, boolean trailingSpaceAtLastLineIsVisible) {
        CharSequence text = mText;
        char ch;
        if (trailingSpaceAtLastLineIsVisible && line == getLineCount() - 1) return end;
        for (; end > start; end--) {
            ch = text.charAt(end - 1);
            if (ch == '\n') return end - 1;
            if (!TextLine.isLineEndSpace(ch)) break;
        }
        return end;
    }

    public final int getLineBottom(int line) { return getLineTop(line + 1); }

    public int getLineBottom(int line, boolean includeLineSpacing) {
        if (includeLineSpacing) return getLineTop(line + 1);
        return getLineTop(line + 1) - getLineExtra(line);
    }

    public final int getLineBaseline(int line) { return getLineTop(line + 1) - getLineDescent(line); }

    public final int getLineAscent(int line) { return getLineTop(line) - (getLineTop(line + 1) - getLineDescent(line)); }

    /** Hidden AOSP API: extra space added below the line by line spacing. */
    public int getLineExtra(int line) { return 0; }

    public int getOffsetToLeftOf(int offset) { return getOffsetAtStartOf(TextUtils.getOffsetBefore(mText, offset), offset, false); }

    public int getOffsetToRightOf(int offset) { return getOffsetAtStartOf(TextUtils.getOffsetAfter(mText, offset), offset, true); }

    private int getOffsetAtStartOf(int candidate, int offset, boolean toRight) {
        int line = getLineForOffset(offset);
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        if (!toRight && offset == lineStart && line > 0) {
            int prevEnd = getLineVisibleEnd(line - 1);
            return prevEnd;
        }
        if (toRight && offset >= getLineVisibleEnd(line) && line < getLineCount() - 1) return lineEnd;
        return candidate;
    }

    public void getCursorPath(int point, Path dest, CharSequence editingBuffer) {
        dest.reset();
        int line = getLineForOffset(point);
        int top = getLineTop(line);
        int bottom = getLineBottom(line, false);
        float h1 = getPrimaryHorizontal(point, true) - 0.5f;
        dest.moveTo(h1, top);
        dest.lineTo(h1, bottom);
    }

    private void addSelection(int line, int start, int end, int top, int bottom, Path dest) {
        int linestart = getLineStart(line);
        int lineend = getLineEnd(line);
        if (lineend > linestart && mText.charAt(lineend - 1) == '\n') lineend--;
        int here = Math.max(start, linestart);
        int there = Math.min(end, lineend);
        if (here != there || here == linestart && lineend == linestart) {
            float h1 = getHorizontal(here, line, false);
            float h2 = getHorizontal(there, line, false);
            float left = Math.min(h1, h2);
            float right = Math.max(h1, h2);
            dest.addRect(left, top, right, bottom, Path.Direction.CW);
        }
    }

    public void getSelectionPath(int start, int end, Path dest) {
        dest.reset();
        getSelection(start, end, dest);
    }

    private void getSelection(int start, int end, Path dest) {
        if (start == end) return;
        if (end < start) {
            int temp = end;
            end = start;
            start = temp;
        }
        final int startline = getLineForOffset(start);
        final int endline = getLineForOffset(end);
        int top = getLineTop(startline);
        int bottom = getLineBottom(endline, false);
        if (startline == endline) {
            addSelection(startline, start, end, top, bottom, dest);
        } else {
            final float width = mWidth;
            addSelection(startline, start, getLineEnd(startline), top, getLineBottom(startline), dest);
            dest.addRect(getLineRight(startline), top, width, getLineBottom(startline), Path.Direction.CW);
            for (int i = startline + 1; i < endline; i++) {
                top = getLineTop(i);
                bottom = getLineBottom(i);
                dest.addRect(0, top, width, bottom, Path.Direction.CW);
            }
            top = getLineTop(endline);
            bottom = getLineBottom(endline, false);
            addSelection(endline, getLineStart(endline), end, top, bottom, dest);
            dest.addRect(0, top, getLineLeft(endline), bottom, Path.Direction.CW);
        }
    }

    public final Alignment getParagraphAlignment(int line) {
        Alignment align = mAlignment;
        if (mSpannedText) {
            Spanned sp = (Spanned) mText;
            AlignmentSpan[] spans = getParagraphSpans(sp, getLineStart(line), getLineEnd(line), AlignmentSpan.class);
            int spanLength = spans.length;
            if (spanLength > 0) align = spans[spanLength - 1].getAlignment();
        }
        return align;
    }

    public final int getParagraphLeft(int line) {
        int left = 0;
        int dir = getParagraphDirection(line);
        if (dir == DIR_RIGHT_TO_LEFT || !mSpannedText) return left;
        return getParagraphLeadingMargin(line);
    }

    public final int getParagraphRight(int line) { return mWidth; }

    private int getParagraphLeadingMargin(int line) {
        if (!mSpannedText) return 0;
        Spanned spanned = (Spanned) mText;
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        int spanEnd = spanned.nextSpanTransition(lineStart, lineEnd, LeadingMarginSpan.class);
        LeadingMarginSpan[] spans = getParagraphSpans(spanned, lineStart, spanEnd, LeadingMarginSpan.class);
        if (spans.length == 0) return 0;
        int margin = 0;
        boolean useFirstLineMargin = lineStart == 0 || spanned.charAt(lineStart - 1) == '\n';
        for (int i = 0; i < spans.length; i++) {
            if (spans[i] instanceof LeadingMarginSpan.LeadingMarginSpan2) {
                int spStart = spanned.getSpanStart(spans[i]);
                int spanLine = getLineForOffset(spStart);
                int count = ((LeadingMarginSpan.LeadingMarginSpan2) spans[i]).getLeadingMarginLineCount();
                useFirstLineMargin |= line < spanLine + count;
            }
        }
        for (int i = 0; i < spans.length; i++) margin += spans[i].getLeadingMargin(useFirstLineMargin);
        return margin;
    }

    /**
     * Same as text.getSpans() except that an empty range not at the very start
     * of the text returns no spans (AOSP getParagraphSpans).
     */
    static <T> T[] getParagraphSpans(Spanned text, int start, int end, Class<T> type) {
        if (start == end && start > 0) return (T[]) java.lang.reflect.Array.newInstance(type, 0);
        return text.getSpans(start, end, type);
    }

    protected final boolean isSpanned() { return mSpannedText; }

    public abstract int getEllipsisStart(int line);

    public abstract int getEllipsisCount(int line);

    public final CharSequence getText() { return mText; }

    public final TextPaint getPaint() { return mPaint; }

    public final int getWidth() { return mWidth; }

    public final Alignment getAlignment() { return mAlignment; }

    public final TextDirectionHeuristic getTextDirectionHeuristic() { return mTextDir; }

    public final float getSpacingMultiplier() { return mSpacingMult; }

    public final float getLineSpacingMultiplier() { return mSpacingMult; }

    public final float getSpacingAdd() { return mSpacingAdd; }

    public final float getLineSpacingAmount() { return mSpacingAdd; }

    public final boolean isFontPaddingIncluded() { return mIncludePad; }

    public boolean isFallbackLineSpacingEnabled() { return mFallbackLineSpacing; }

    public int getEllipsizedWidth() { return mEllipsizedWidth; }

    public final TextUtils.TruncateAt getEllipsize() { return mEllipsize; }

    public final int getMaxLines() { return mMaxLines; }

    public final int getBreakStrategy() { return mBreakStrategy; }

    public final int getHyphenationFrequency() { return mHyphenationFrequency; }

    public final int[] getLeftIndents() { return mLeftIndents; }

    public final int[] getRightIndents() { return mRightIndents; }

    public final int getJustificationMode() { return mJustificationMode; }

    public LineBreakConfig getLineBreakConfig() { return mLineBreakConfig; }

    public boolean getUseBoundsForWidth() { return mUseBoundsForWidth; }

    public boolean getShiftDrawingOffsetForStartOverhang() { return mShiftDrawingOffsetForStartOverhang; }

    public Paint.FontMetrics getMinimumFontMetrics() { return mMinimumFontMetrics; }

    /** Hidden AOSP API: whether the text contains a ReplacementSpan covering offset. */
    static boolean isInsideReplacement(CharSequence text, int offset) {
        if (!(text instanceof Spanned)) return false;
        Spanned sp = (Spanned) text;
        ReplacementSpan[] spans = sp.getSpans(offset, offset, ReplacementSpan.class);
        for (ReplacementSpan s : spans) {
            if (sp.getSpanStart(s) < offset && sp.getSpanEnd(s) > offset) return true;
        }
        return false;
    }
}
