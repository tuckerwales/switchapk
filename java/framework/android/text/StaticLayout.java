package android.text;

import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.text.LineBreakConfig;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.text.style.TabStopSpan;

/**
 * A layout for text that does not change after it is laid out (port of AOSP
 * StaticLayout). Lines are broken greedily at word boundaries (spaces,
 * hyphens, slashes, CJK ideographs) instead of Minikin's optimal breaker.
 */
public class StaticLayout extends Layout {
    private static final char CHAR_NEW_LINE = '\n';
    private static final double EXTRA_ROUNDING = 0.5;
    private static final float TAB_INCREMENT = 20;
    private static final int DEFAULT_MAX_LINE_HEIGHT = -1;

    public static final class Builder {
        private CharSequence mText;
        private int mStart;
        private int mEnd;
        private TextPaint mPaint;
        private int mWidth;
        private Alignment mAlignment;
        private TextDirectionHeuristic mTextDir;
        private float mSpacingMult;
        private float mSpacingAdd;
        private boolean mIncludePad;
        private boolean mFallbackLineSpacing;
        private int mEllipsizedWidth;
        private TextUtils.TruncateAt mEllipsize;
        private int mMaxLines;
        private int mBreakStrategy;
        private int mHyphenationFrequency;
        private int[] mLeftIndents;
        private int[] mRightIndents;
        private int mJustificationMode;
        private boolean mAddLastLineLineSpacing;
        private LineBreakConfig mLineBreakConfig = LineBreakConfig.NONE;
        private boolean mUseBoundsForWidth;
        private boolean mShiftDrawingOffsetForStartOverhang;
        private Paint.FontMetrics mMinimumFontMetrics;

        Builder() {}

        public static Builder obtain(CharSequence source, int start, int end, TextPaint paint, int width) {
            Builder b = new Builder();
            b.mText = source;
            b.mStart = start;
            b.mEnd = end;
            b.mPaint = paint;
            b.mWidth = width;
            b.mAlignment = Alignment.ALIGN_NORMAL;
            b.mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
            b.mSpacingMult = DEFAULT_LINESPACING_MULTIPLIER;
            b.mSpacingAdd = DEFAULT_LINESPACING_ADDITION;
            b.mIncludePad = true;
            b.mFallbackLineSpacing = false;
            b.mEllipsizedWidth = width;
            b.mEllipsize = null;
            b.mMaxLines = Integer.MAX_VALUE;
            b.mBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
            b.mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE;
            b.mJustificationMode = Layout.JUSTIFICATION_MODE_NONE;
            return b;
        }

        public Builder setText(CharSequence source) { return setText(source, 0, source.length()); }

        /** Hidden AOSP API. */
        public Builder setText(CharSequence source, int start, int end) {
            mText = source;
            mStart = start;
            mEnd = end;
            return this;
        }

        /** Hidden AOSP API. */
        public Builder setPaint(TextPaint paint) { mPaint = paint; return this; }

        /** Hidden AOSP API. */
        public Builder setWidth(int width) {
            mWidth = width;
            if (mEllipsize == null) mEllipsizedWidth = width;
            return this;
        }

        public Builder setAlignment(Alignment alignment) { mAlignment = alignment; return this; }

        public Builder setTextDirection(TextDirectionHeuristic textDir) { mTextDir = textDir; return this; }

        public Builder setLineSpacing(float spacingAdd, float spacingMult) {
            mSpacingAdd = spacingAdd;
            mSpacingMult = spacingMult;
            return this;
        }

        public Builder setIncludePad(boolean includePad) { mIncludePad = includePad; return this; }

        public Builder setUseLineSpacingFromFallbacks(boolean useLineSpacingFromFallbacks) {
            mFallbackLineSpacing = useLineSpacingFromFallbacks;
            return this;
        }

        public Builder setEllipsizedWidth(int ellipsizedWidth) { mEllipsizedWidth = ellipsizedWidth; return this; }

        public Builder setEllipsize(TextUtils.TruncateAt ellipsize) { mEllipsize = ellipsize; return this; }

        public Builder setMaxLines(int maxLines) { mMaxLines = maxLines; return this; }

        public Builder setBreakStrategy(int breakStrategy) { mBreakStrategy = breakStrategy; return this; }

        public Builder setHyphenationFrequency(int hyphenationFrequency) {
            mHyphenationFrequency = hyphenationFrequency;
            return this;
        }

        public Builder setIndents(int[] leftIndents, int[] rightIndents) {
            mLeftIndents = leftIndents;
            mRightIndents = rightIndents;
            return this;
        }

        public Builder setJustificationMode(int justificationMode) { mJustificationMode = justificationMode; return this; }

        /** Hidden AOSP API. */
        public Builder setAddLastLineLineSpacing(boolean value) { mAddLastLineLineSpacing = value; return this; }

        public Builder setLineBreakConfig(LineBreakConfig lineBreakConfig) { mLineBreakConfig = lineBreakConfig; return this; }

        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) { mUseBoundsForWidth = useBoundsForWidth; return this; }

        public Builder setShiftDrawingOffsetForStartOverhang(boolean shiftDrawingOffsetForStartOverhang) {
            mShiftDrawingOffsetForStartOverhang = shiftDrawingOffsetForStartOverhang;
            return this;
        }

        public Builder setMinimumFontMetrics(Paint.FontMetrics minimumFontMetrics) {
            mMinimumFontMetrics = minimumFontMetrics;
            return this;
        }

        public StaticLayout build() {
            StaticLayout result = new StaticLayout(this, true);
            return result;
        }
    }

    // Per-line data.
    private int mLineCount;
    private int mTopPadding;
    private int mBottomPadding;
    private int[] mLineStarts = new int[8];
    private int[] mLineTops = new int[9];
    private int[] mLineDescents = new int[8];
    private int[] mLineExtras = new int[8];
    private boolean[] mLineTabs = new boolean[8];
    private int[] mEllipsisStarts = new int[8];
    private int[] mEllipsisCounts = new int[8];
    private int mMaximumVisibleLineCount = Integer.MAX_VALUE;
    private boolean mEllipsized;

    @Deprecated
    public StaticLayout(CharSequence source, TextPaint paint, int width, Alignment align, float spacingmult,
            float spacingadd, boolean includepad) {
        this(source, 0, source.length(), paint, width, align, spacingmult, spacingadd, includepad);
    }

    @Deprecated
    public StaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth,
            Alignment align, float spacingmult, float spacingadd, boolean includepad) {
        this(source, bufstart, bufend, paint, outerwidth, align, spacingmult, spacingadd, includepad, null, 0);
    }

    @Deprecated
    public StaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth,
            Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize,
            int ellipsizedWidth) {
        this(source, bufstart, bufend, paint, outerwidth, align, TextDirectionHeuristics.FIRSTSTRONG_LTR, spacingmult,
                spacingadd, includepad, ellipsize, ellipsizedWidth, Integer.MAX_VALUE);
    }

    /** Hidden AOSP constructor. */
    @Deprecated
    public StaticLayout(CharSequence source, int bufstart, int bufend, TextPaint paint, int outerwidth,
            Alignment align, TextDirectionHeuristic textDir, float spacingmult, float spacingadd, boolean includepad,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth, int maxLines) {
        this(Builder.obtain(source, bufstart, bufend, paint, outerwidth).setAlignment(align).setTextDirection(textDir)
                .setLineSpacing(spacingadd, spacingmult).setIncludePad(includepad).setEllipsizedWidth(ellipsizedWidth)
                .setEllipsize(ellipsize).setMaxLines(maxLines), true);
    }

    private StaticLayout(Builder b, boolean unused) {
        super(b.mText, b.mPaint, b.mWidth, b.mAlignment, b.mTextDir, b.mSpacingMult, b.mSpacingAdd);
        setLayoutParams(b.mIncludePad, b.mFallbackLineSpacing, b.mEllipsizedWidth, b.mEllipsize, b.mMaxLines,
                b.mBreakStrategy, b.mHyphenationFrequency, b.mLeftIndents, b.mRightIndents, b.mJustificationMode,
                b.mLineBreakConfig, b.mUseBoundsForWidth, b.mShiftDrawingOffsetForStartOverhang,
                b.mMinimumFontMetrics);
        mMaximumVisibleLineCount = b.mMaxLines;
        generate(b, b.mIncludePad, b.mIncludePad);
    }

    // ---- measurement of one paragraph ----

    /** Styled per-character widths and metrics of a paragraph. */
    private static final class Measured {
        float[] widths = new float[16];
        boolean[] noBreak = new boolean[16];
        int[] runStarts = new int[4];
        int[] runAscent = new int[4];
        int[] runDescent = new int[4];
        int[] runTop = new int[4];
        int[] runBottom = new int[4];
        int runCount;
        char[] chars = new char[16];
        int start;
        int len;
        final TextPaint wp = new TextPaint();
        final Paint.FontMetricsInt fm = new Paint.FontMetricsInt();

        void measure(TextPaint paint, CharSequence text, int start, int end) {
            this.start = start;
            len = end - start;
            if (widths.length < len) {
                int n = Math.max(len, widths.length * 2);
                widths = new float[n];
                noBreak = new boolean[n];
                chars = new char[n];
            }
            TextUtils.getChars(text, start, end, chars, 0);
            java.util.Arrays.fill(noBreak, 0, len, false);
            runCount = 0;
            Spanned sp = text instanceof Spanned ? (Spanned) text : null;
            if (len == 0) {
                addRun(start, paint, 0);
                return;
            }
            int next;
            for (int i = start; i < end; i = next) {
                next = sp == null ? end : sp.nextSpanTransition(i, end, MetricAffectingSpan.class);
                if (next <= i) next = end;
                ReplacementSpan replacement = null;
                wp.set(paint);
                if (sp != null) {
                    MetricAffectingSpan[] spans = sp.getSpans(i, next, MetricAffectingSpan.class);
                    for (MetricAffectingSpan span : spans) {
                        int ss = sp.getSpanStart(span), se = sp.getSpanEnd(span);
                        if (ss >= next || se <= i) continue;
                        if (span instanceof ReplacementSpan) replacement = (ReplacementSpan) span;
                        else span.updateMeasureState(wp);
                    }
                }
                int off = i - start;
                int n = next - i;
                if (replacement != null) {
                    int ss = Math.max(sp.getSpanStart(replacement), start);
                    int se = Math.min(sp.getSpanEnd(replacement), end);
                    wp.getFontMetricsInt(fm);
                    int w = replacement.getSize(wp, text, ss, se, fm);
                    widths[off] = w;
                    for (int k = 1; k < n; k++) {
                        widths[off + k] = 0;
                        noBreak[off + k] = true;
                    }
                    addRun(i, fm);
                } else {
                    wp.getTextRunAdvances(chars, off, n, off, n, false, widths, off);
                    addRun(i, wp, wp.baselineShift);
                }
            }
            for (int k = 0; k < len; k++) {
                char c = chars[k];
                if (c == '\n' || c == '\r') widths[k] = 0;
                if (k > 0 && Character.isLowSurrogate(c) && Character.isHighSurrogate(chars[k - 1])) noBreak[k] = true;
            }
        }

        private void addRun(int at, TextPaint p, int shift) {
            p.getFontMetricsInt(fm);
            if (shift < 0) {
                fm.ascent += shift;
                fm.top += shift;
            } else {
                fm.descent += shift;
                fm.bottom += shift;
            }
            addRun(at, fm);
        }

        private void addRun(int at, Paint.FontMetricsInt m) {
            if (runCount == runStarts.length) {
                int n = runCount * 2;
                runStarts = java.util.Arrays.copyOf(runStarts, n);
                runAscent = java.util.Arrays.copyOf(runAscent, n);
                runDescent = java.util.Arrays.copyOf(runDescent, n);
                runTop = java.util.Arrays.copyOf(runTop, n);
                runBottom = java.util.Arrays.copyOf(runBottom, n);
            }
            runStarts[runCount] = at;
            runAscent[runCount] = m.ascent;
            runDescent[runCount] = m.descent;
            runTop[runCount] = m.top;
            runBottom[runCount] = m.bottom;
            runCount++;
        }

        /** Union of the metrics of runs overlapping [s, e). */
        void metrics(int s, int e, Paint.FontMetricsInt out) {
            boolean any = false;
            for (int r = 0; r < runCount; r++) {
                int rs = runStarts[r];
                int re = r + 1 < runCount ? runStarts[r + 1] : start + len;
                boolean overlaps = (rs < e && re > s) || (s == e && rs <= s && (s < re || r == runCount - 1));
                if (!overlaps) continue;
                if (!any) {
                    out.ascent = runAscent[r];
                    out.descent = runDescent[r];
                    out.top = runTop[r];
                    out.bottom = runBottom[r];
                    any = true;
                } else {
                    out.ascent = Math.min(out.ascent, runAscent[r]);
                    out.descent = Math.max(out.descent, runDescent[r]);
                    out.top = Math.min(out.top, runTop[r]);
                    out.bottom = Math.max(out.bottom, runBottom[r]);
                }
            }
            if (!any && runCount > 0) {
                out.ascent = runAscent[0];
                out.descent = runDescent[0];
                out.top = runTop[0];
                out.bottom = runBottom[0];
            }
        }
    }

    private static boolean isIdeographic(char c) {
        return (c >= 0x2E80 && c <= 0x2FFF) || (c >= 0x3040 && c <= 0x30FF) || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xF900 && c <= 0xFAFF) || (c >= 0xAC00 && c <= 0xD7AF)
                || (c >= 0xFF00 && c <= 0xFFEF);
    }

    /** Whether a line may break between chars[i] and chars[i + 1]. */
    private static boolean canBreakAfter(char[] chars, int i, int len) {
        char c = chars[i];
        if (c == ' ' || c == '\t' || c == '​') return true;
        if (i + 1 >= len) return true;
        char n = chars[i + 1];
        if ((c == '-' || c == '/' || c == '‐' || c == '–' || c == '—') && Character.isLetterOrDigit(n)
                && i > 0 && Character.isLetterOrDigit(chars[i - 1])) {
            return true;
        }
        if (isIdeographic(c) || isIdeographic(n)) {
            // Do not start a line with closing punctuation.
            return !(n == '、' || n == '。' || n == '，' || n == '．' || n == '」' || n == '』'
                    || n == '）' || n == 'ー');
        }
        return false;
    }

    // ---- line generation ----

    /** Hidden AOSP API. */
    void generate(Builder b, boolean includepad, boolean trackpad) {
        final CharSequence source = b.mText;
        final int bufStart = b.mStart;
        final int bufEnd = b.mEnd;
        TextPaint paint = b.mPaint;
        int outerWidth = b.mWidth;
        float spacingmult = b.mSpacingMult;
        float spacingadd = b.mSpacingAdd;
        float ellipsizedWidth = b.mEllipsizedWidth;
        TextUtils.TruncateAt ellipsize = b.mEllipsize;
        final boolean addLastLineSpacing = b.mAddLastLineLineSpacing;
        final boolean noBreakStyle = b.mLineBreakConfig != null
                && b.mLineBreakConfig.getLineBreakStyle() == LineBreakConfig.LINE_BREAK_STYLE_NO_BREAK;

        mLineCount = 0;
        mEllipsized = false;
        int v = 0;
        boolean needMultiply = (spacingmult != 1 || spacingadd != 0);
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        int[] chooseHtv = null;
        final int[] indents;
        if (b.mLeftIndents != null || b.mRightIndents != null) {
            final int leftLen = b.mLeftIndents == null ? 0 : b.mLeftIndents.length;
            final int rightLen = b.mRightIndents == null ? 0 : b.mRightIndents.length;
            final int indentsLen = Math.max(leftLen, rightLen);
            indents = new int[indentsLen];
            for (int i = 0; i < leftLen; i++) indents[i] = b.mLeftIndents[i];
            for (int i = 0; i < rightLen; i++) indents[i] += b.mRightIndents[i];
        } else {
            indents = null;
        }

        final boolean ellipsisMayBeApplied = ellipsize != null && (ellipsize == TextUtils.TruncateAt.END
                || (mMaximumVisibleLineCount == 1 && ellipsize != TextUtils.TruncateAt.MARQUEE));

        Spanned spanned = source instanceof Spanned ? (Spanned) source : null;
        Measured measured = new Measured();
        int paraEnd;
        for (int paraStart = bufStart; paraStart <= bufEnd; paraStart = paraEnd) {
            paraEnd = TextUtils.indexOf(source, CHAR_NEW_LINE, paraStart, bufEnd);
            if (paraEnd < 0) paraEnd = bufEnd;
            else paraEnd++;

            int firstWidthLineCount = 1;
            int firstWidth = outerWidth;
            int restWidth = outerWidth;
            LineHeightSpan[] chooseHt = null;
            TextLine.TabStops tabStops = null;
            if (spanned != null) {
                LeadingMarginSpan[] sp = getParagraphSpans(spanned, paraStart, paraEnd, LeadingMarginSpan.class);
                for (int i = 0; i < sp.length; i++) {
                    LeadingMarginSpan lms = sp[i];
                    firstWidth -= sp[i].getLeadingMargin(true);
                    restWidth -= sp[i].getLeadingMargin(false);
                    if (lms instanceof LeadingMarginSpan.LeadingMarginSpan2) {
                        LeadingMarginSpan.LeadingMarginSpan2 lms2 = (LeadingMarginSpan.LeadingMarginSpan2) lms;
                        firstWidthLineCount = Math.max(firstWidthLineCount, lms2.getLeadingMarginLineCount());
                    }
                }
                chooseHt = getParagraphSpans(spanned, paraStart, paraEnd, LineHeightSpan.class);
                if (chooseHt.length == 0) {
                    chooseHt = null;
                } else {
                    if (chooseHtv == null || chooseHtv.length < chooseHt.length) chooseHtv = new int[chooseHt.length];
                    for (int i = 0; i < chooseHt.length; i++) {
                        int o = spanned.getSpanStart(chooseHt[i]);
                        if (o < paraStart) chooseHtv[i] = getLineTop(getLineForOffset(o));
                        else chooseHtv[i] = v;
                    }
                }
                TabStopSpan[] spans = getParagraphSpans(spanned, paraStart, paraEnd, TabStopSpan.class);
                if (spans.length > 0) tabStops = new TextLine.TabStops(TAB_INCREMENT, spans);
            }

            if (paraStart == bufEnd) break;

            measured.measure(paint, source, paraStart, paraEnd);
            final float[] widths = measured.widths;
            final char[] chars = measured.chars;
            final int len = paraEnd - paraStart;

            // Greedy break into [lineStart, lineEnd) ranges.
            int[] breaks = new int[8];
            float[] lineWidths = new float[8];
            boolean[] lineTabs = new boolean[8];
            int breakCount = 0;
            int here = 0;
            while (here < len) {
                final int lineIndex = mLineCount + breakCount;
                int lineWidth = (lineIndex < mLineCount + firstWidthLineCount && breakCount < firstWidthLineCount)
                        ? firstWidth : restWidth;
                if (indents != null) lineWidth -= indents[Math.min(lineIndex, indents.length - 1)];
                float w = 0;
                float visibleW = 0;
                int ok = -1;
                float okWidth = 0;
                boolean hasTab = false;
                int j = here;
                for (; j < len; j++) {
                    char c = chars[j];
                    if (c == CHAR_NEW_LINE) {
                        j++;
                        break;
                    }
                    float cw;
                    if (c == '\t') {
                        hasTab = true;
                        float next = tabStops != null ? tabStops.nextTab(w)
                                : TextLine.TabStops.nextDefaultStop(w, TAB_INCREMENT);
                        cw = next - w;
                    } else {
                        cw = widths[j];
                    }
                    boolean space = TextLine.isLineEndSpace(c) && c != '\t';
                    if (!space && !noBreakStyle && w + cw > lineWidth && j > here && !measured.noBreak[j]) {
                        break;
                    }
                    w += cw;
                    if (!space) visibleW = w;
                    if (j + 1 < len && !measured.noBreak[j + 1] && canBreakAfter(chars, j, len)) {
                        ok = j + 1;
                        okWidth = visibleW;
                    }
                }
                int lineEnd;
                float lineW;
                if (j >= len || chars[j - 1] == CHAR_NEW_LINE) {
                    lineEnd = j;
                    lineW = visibleW;
                } else if (ok > here) {
                    lineEnd = ok;
                    lineW = okWidth;
                    // Trailing spaces after a break opportunity stay on this line.
                    while (lineEnd < len && TextLine.isLineEndSpace(chars[lineEnd]) && chars[lineEnd] != '\t') lineEnd++;
                    if (lineEnd < len && chars[lineEnd] == CHAR_NEW_LINE) lineEnd++;
                } else {
                    lineEnd = j;
                    while (lineEnd > here + 1 && measured.noBreak[lineEnd]) lineEnd--;
                    lineW = 0;
                    for (int k = here; k < lineEnd; k++) lineW += widths[k];
                }
                if (lineEnd <= here) lineEnd = here + 1;
                if (breakCount == breaks.length) {
                    breaks = java.util.Arrays.copyOf(breaks, breakCount * 2);
                    lineWidths = java.util.Arrays.copyOf(lineWidths, breakCount * 2);
                    lineTabs = java.util.Arrays.copyOf(lineTabs, breakCount * 2);
                }
                breaks[breakCount] = lineEnd;
                lineWidths[breakCount] = lineW;
                boolean tabInLine = false;
                for (int k = here; k < lineEnd; k++) {
                    if (chars[k] == '\t') {
                        tabInLine = true;
                        break;
                    }
                }
                lineTabs[breakCount] = tabInLine || hasTab;
                breakCount++;
                here = lineEnd;
            }

            final int remainingLineCount = mMaximumVisibleLineCount - mLineCount;
            if (0 < remainingLineCount && remainingLineCount < breakCount && ellipsisMayBeApplied) {
                float width = 0;
                boolean tab = false;
                for (int i = remainingLineCount - 1; i < breakCount; i++) {
                    if (i == breakCount - 1) {
                        width += lineWidths[i];
                    } else {
                        for (int k = (i == 0 ? 0 : breaks[i - 1]); k < breaks[i]; k++) width += widths[k];
                    }
                    tab |= lineTabs[i];
                }
                breaks[remainingLineCount - 1] = breaks[breakCount - 1];
                lineWidths[remainingLineCount - 1] = width;
                lineTabs[remainingLineCount - 1] = tab;
                breakCount = remainingLineCount;
            }

            here = paraStart;
            for (int breakIndex = 0; breakIndex < breakCount; breakIndex++) {
                final int endPos = paraStart + breaks[breakIndex];
                measured.metrics(here, endPos, fm);
                final boolean moreChars = endPos < bufEnd;
                int lineWidthLimit = (breakIndex < firstWidthLineCount) ? firstWidth : restWidth;
                v = out(source, here, endPos, fm.ascent, fm.descent, fm.top, fm.bottom, v, spacingmult, spacingadd,
                        chooseHt, chooseHtv, fm, lineTabs[breakIndex], needMultiply, measured, bufEnd, includepad,
                        trackpad, addLastLineSpacing, ellipsize, ellipsizedWidth, lineWidths[breakIndex], paint,
                        moreChars, lineWidthLimit);
                here = endPos;
                if (mLineCount >= mMaximumVisibleLineCount) return;
            }
            if (paraEnd == bufEnd) break;
        }

        if ((bufEnd == bufStart || source.charAt(bufEnd - 1) == CHAR_NEW_LINE)
                && mLineCount < mMaximumVisibleLineCount) {
            paint.getFontMetricsInt(fm);
            v = out(source, bufEnd, bufEnd, fm.ascent, fm.descent, fm.top, fm.bottom, v, spacingmult, spacingadd,
                    null, null, fm, false, needMultiply, null, bufEnd, includepad, trackpad, addLastLineSpacing,
                    null, ellipsizedWidth, 0, paint, false, outerWidth);
        }
    }

    private int out(final CharSequence text, final int start, final int end, int above, int below, int top,
            int bottom, int v, final float spacingmult, final float spacingadd, final LineHeightSpan[] chooseHt,
            final int[] chooseHtv, final Paint.FontMetricsInt fm, final boolean hasTab, final boolean needMultiply,
            final Measured measured, final int bufEnd, final boolean includePad, final boolean trackPad,
            final boolean addLastLineLineSpacing, final TextUtils.TruncateAt ellipsize, final float ellipsisWidth,
            final float textWidth, final TextPaint paint, final boolean moreChars, final int lineWidthLimit) {
        final int j = mLineCount;
        ensureCapacity(j + 1);
        mLineStarts[j] = start;
        mLineTabs[j] = hasTab;
        mEllipsisStarts[j] = 0;
        mEllipsisCounts[j] = 0;

        if (chooseHt != null) {
            fm.ascent = above;
            fm.descent = below;
            fm.top = top;
            fm.bottom = bottom;
            for (int i = 0; i < chooseHt.length; i++) {
                if (chooseHt[i] instanceof LineHeightSpan.WithDensity) {
                    ((LineHeightSpan.WithDensity) chooseHt[i]).chooseHeight(text, start, end, chooseHtv[i], v, fm, paint);
                } else {
                    chooseHt[i].chooseHeight(text, start, end, chooseHtv[i], v, fm);
                }
            }
            above = fm.ascent;
            below = fm.descent;
            top = fm.top;
            bottom = fm.bottom;
        }

        boolean firstLine = (j == 0);
        boolean currentLineIsTheLastVisibleOne = (j + 1 == mMaximumVisibleLineCount);

        if (ellipsize != null && measured != null) {
            boolean forceEllipsis = moreChars && (mLineCount + 1 == mMaximumVisibleLineCount);
            boolean doEllipsis = (((mMaximumVisibleLineCount == 1 && moreChars) || (firstLine && !moreChars))
                    && ellipsize != TextUtils.TruncateAt.MARQUEE)
                    || (!firstLine && (currentLineIsTheLastVisibleOne || !moreChars)
                    && ellipsize == TextUtils.TruncateAt.END);
            if (doEllipsis) {
                calculateEllipsis(start, end, measured, ellipsisWidth, ellipsize, j, textWidth, paint, forceEllipsis);
            }
        }

        final boolean lastLine;
        if (mEllipsized) {
            lastLine = true;
        } else {
            final boolean lastCharIsNewLine = start != bufEnd && bufEnd > 0 && text.charAt(bufEnd - 1) == CHAR_NEW_LINE;
            if (end == bufEnd && !lastCharIsNewLine) lastLine = true;
            else if (start == bufEnd && lastCharIsNewLine) lastLine = true;
            else lastLine = false;
        }

        if (firstLine) {
            if (trackPad) mTopPadding = top - above;
            if (includePad) above = top;
        }
        int extra;
        if (lastLine) {
            if (trackPad) mBottomPadding = bottom - below;
            if (includePad) below = bottom;
        }
        if (needMultiply && (addLastLineLineSpacing || !lastLine)) {
            double ex = (below - above) * (spacingmult - 1) + spacingadd;
            if (ex >= 0) extra = (int) (ex + EXTRA_ROUNDING);
            else extra = -(int) (-ex + EXTRA_ROUNDING);
        } else {
            extra = 0;
        }
        mLineTops[j] = v;
        mLineDescents[j] = below + extra;
        mLineExtras[j] = extra;
        v += (below - above) + extra;
        mLineTops[j + 1] = v;
        mLineStarts[j + 1] = end;
        mLineCount++;
        return v;
    }

    private void calculateEllipsis(int lineStart, int lineEnd, Measured measured, float avail,
            TextUtils.TruncateAt where, int line, float textWidth, TextPaint paint, boolean forceEllipsis) {
        avail -= getTotalInsets(line);
        if (textWidth <= avail && !forceEllipsis) {
            mEllipsisStarts[line] = 0;
            mEllipsisCounts[line] = 0;
            return;
        }
        float ellipsisWidth = paint.measureText(where == TextUtils.TruncateAt.END_SMALL ? "‥" : "…");
        int ellipsisStart = 0;
        int ellipsisCount = 0;
        int len = lineEnd - lineStart;
        final float[] widths = measured.widths;
        final int widthStart = measured.start;
        // Do not count a trailing newline as text to ellipsize.
        int textLen = len;
        while (textLen > 0 && measured.chars[lineStart + textLen - 1 - widthStart] == CHAR_NEW_LINE) textLen--;
        if (where == TextUtils.TruncateAt.START) {
            if (mMaximumVisibleLineCount == 1) {
                float sum = 0;
                int i;
                for (i = textLen; i > 0; i--) {
                    float w = widths[i - 1 + lineStart - widthStart];
                    if (w + sum + ellipsisWidth > avail) {
                        while (i < textLen && widths[i + lineStart - widthStart] == 0.0f) i++;
                        break;
                    }
                    sum += w;
                }
                ellipsisStart = 0;
                ellipsisCount = i;
            }
        } else if (where == TextUtils.TruncateAt.END || where == TextUtils.TruncateAt.MARQUEE
                || where == TextUtils.TruncateAt.END_SMALL) {
            float sum = 0;
            int i;
            for (i = 0; i < textLen; i++) {
                float w = widths[i + lineStart - widthStart];
                if (w + sum + ellipsisWidth > avail) break;
                sum += w;
            }
            ellipsisStart = i;
            ellipsisCount = textLen - i;
            if (forceEllipsis && ellipsisCount == 0 && textLen > 0) {
                ellipsisStart = textLen - 1;
                ellipsisCount = 1;
            }
        } else {
            if (mMaximumVisibleLineCount == 1) {
                float lsum = 0, rsum = 0;
                int left = 0, right = textLen;
                float ravail = (avail - ellipsisWidth) / 2;
                for (right = textLen; right > 0; right--) {
                    float w = widths[right - 1 + lineStart - widthStart];
                    if (w + rsum > ravail) {
                        while (right < textLen && widths[right + lineStart - widthStart] == 0.0f) right++;
                        break;
                    }
                    rsum += w;
                }
                float lavail = avail - ellipsisWidth - rsum;
                for (left = 0; left < right; left++) {
                    float w = widths[left + lineStart - widthStart];
                    if (w + lsum > lavail) break;
                    lsum += w;
                }
                ellipsisStart = left;
                ellipsisCount = right - left;
            }
        }
        mEllipsized = true;
        mEllipsisStarts[line] = ellipsisStart;
        mEllipsisCounts[line] = ellipsisCount;
    }

    private float getTotalInsets(int line) {
        int totalIndent = 0;
        int[] l = getLeftIndents(), r = getRightIndents();
        if (l != null && l.length > 0) totalIndent += l[Math.min(line, l.length - 1)];
        if (r != null && r.length > 0) totalIndent += r[Math.min(line, r.length - 1)];
        return totalIndent;
    }

    private void ensureCapacity(int lines) {
        if (lines + 1 >= mLineTops.length) {
            int n = Math.max(lines + 2, mLineTops.length * 2);
            mLineStarts = java.util.Arrays.copyOf(mLineStarts, n);
            mLineTops = java.util.Arrays.copyOf(mLineTops, n);
            mLineDescents = java.util.Arrays.copyOf(mLineDescents, n);
            mLineExtras = java.util.Arrays.copyOf(mLineExtras, n);
            mLineTabs = java.util.Arrays.copyOf(mLineTabs, n);
            mEllipsisStarts = java.util.Arrays.copyOf(mEllipsisStarts, n);
            mEllipsisCounts = java.util.Arrays.copyOf(mEllipsisCounts, n);
        }
    }

    // ---- accessors ----

    @Override
    public int getLineForVertical(int vertical) {
        int high = mLineCount;
        int low = -1;
        int guess;
        while (high - low > 1) {
            guess = (high + low) >> 1;
            if (mLineTops[guess] > vertical) high = guess;
            else low = guess;
        }
        return low < 0 ? 0 : low;
    }

    @Override
    public int getLineCount() { return mLineCount; }

    @Override
    public int getLineTop(int line) { return mLineTops[line]; }

    @Override
    public int getLineExtra(int line) { return mLineExtras[line]; }

    @Override
    public int getLineDescent(int line) { return mLineDescents[line]; }

    @Override
    public int getLineStart(int line) { return mLineStarts[line]; }

    @Override
    public int getParagraphDirection(int line) { return DIR_LEFT_TO_RIGHT; }

    @Override
    public boolean getLineContainsTab(int line) { return mLineTabs[line]; }

    @Override
    public final Directions getLineDirections(int line) { return DIRS_ALL_LEFT_TO_RIGHT; }

    @Override
    public int getTopPadding() { return mTopPadding; }

    @Override
    public int getBottomPadding() { return mBottomPadding; }

    @Override
    public int getEllipsisCount(int line) { return line < mLineCount ? mEllipsisCounts[line] : 0; }

    @Override
    public int getEllipsisStart(int line) { return line < mLineCount ? mEllipsisStarts[line] : 0; }

    @Override
    public RectF computeDrawingBoundingBox() { return super.computeDrawingBoundingBox(); }

    /** Hidden AOSP API. */
    public boolean isEllipsized() { return mEllipsized; }

    /** Hidden AOSP API used by DynamicLayout. */
    int getLineStartsLength() { return mLineCount; }
}
