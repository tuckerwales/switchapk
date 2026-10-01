package android.text;

import android.graphics.Paint;
import android.graphics.text.LineBreakConfig;
import android.text.style.UpdateLayout;
import android.text.style.WrapTogetherSpan;
import java.lang.ref.WeakReference;

/**
 * A layout that follows edits to its Spannable text (AOSP DynamicLayout API).
 * Unlike AOSP, which reflows only the changed paragraph block, this keeps a
 * StaticLayout of the display text and rebuilds it whole on every change.
 */
public class DynamicLayout extends Layout {
    private static final int PRIORITY = 128;

    public static final class Builder {
        private CharSequence mBase;
        private CharSequence mDisplay;
        private TextPaint mPaint;
        private int mWidth;
        private Alignment mAlignment = Alignment.ALIGN_NORMAL;
        private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private float mSpacingMult = DEFAULT_LINESPACING_MULTIPLIER;
        private float mSpacingAdd = DEFAULT_LINESPACING_ADDITION;
        private boolean mIncludePad = true;
        private boolean mFallbackLineSpacing;
        private int mBreakStrategy;
        private int mHyphenationFrequency;
        private int mJustificationMode;
        private TextUtils.TruncateAt mEllipsize;
        private int mEllipsizedWidth;
        private LineBreakConfig mLineBreakConfig = LineBreakConfig.NONE;
        private boolean mUseBoundsForWidth;
        private boolean mShiftDrawingOffsetForStartOverhang;
        private Paint.FontMetrics mMinimumFontMetrics;

        Builder() {}

        public static Builder obtain(CharSequence base, TextPaint paint, int width) {
            Builder b = new Builder();
            b.mBase = base;
            b.mDisplay = base;
            b.mPaint = paint;
            b.mWidth = width;
            b.mEllipsizedWidth = width;
            return b;
        }

        public Builder setDisplayText(CharSequence display) { mDisplay = display; return this; }

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

        public Builder setBreakStrategy(int breakStrategy) { mBreakStrategy = breakStrategy; return this; }

        public Builder setHyphenationFrequency(int hyphenationFrequency) {
            mHyphenationFrequency = hyphenationFrequency;
            return this;
        }

        public Builder setJustificationMode(int justificationMode) { mJustificationMode = justificationMode; return this; }

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

        public DynamicLayout build() { return new DynamicLayout(this); }
    }

    private final CharSequence mBase;
    private final CharSequence mDisplay;
    private final Builder mParams;
    private ChangeWatcher mWatcher;
    private StaticLayout mLines;

    @Deprecated
    public DynamicLayout(CharSequence base, TextPaint paint, int width, Alignment align, float spacingmult,
            float spacingadd, boolean includepad) {
        this(base, base, paint, width, align, spacingmult, spacingadd, includepad);
    }

    @Deprecated
    public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint, int width, Alignment align,
            float spacingmult, float spacingadd, boolean includepad) {
        this(base, display, paint, width, align, spacingmult, spacingadd, includepad, null, 0);
    }

    @Deprecated
    public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint, int width, Alignment align,
            float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize,
            int ellipsizedWidth) {
        this(Builder.obtain(base, paint, width).setDisplayText(display).setAlignment(align)
                .setLineSpacing(spacingadd, spacingmult).setIncludePad(includepad).setEllipsize(ellipsize)
                .setEllipsizedWidth(ellipsizedWidth));
    }

    private DynamicLayout(Builder b) {
        super(b.mDisplay, b.mPaint, b.mWidth, b.mAlignment, b.mTextDir, b.mSpacingMult, b.mSpacingAdd);
        setLayoutParams(b.mIncludePad, b.mFallbackLineSpacing, b.mEllipsizedWidth, b.mEllipsize, Integer.MAX_VALUE,
                b.mBreakStrategy, b.mHyphenationFrequency, null, null, b.mJustificationMode, b.mLineBreakConfig,
                b.mUseBoundsForWidth, b.mShiftDrawingOffsetForStartOverhang, b.mMinimumFontMetrics);
        mBase = b.mBase;
        mDisplay = b.mDisplay;
        mParams = b;
        reflowAll();
        if (mBase instanceof Spannable) {
            if (mWatcher == null) mWatcher = new ChangeWatcher(this);
            Spannable sp = (Spannable) mBase;
            ChangeWatcher[] spans = sp.getSpans(0, sp.length(), ChangeWatcher.class);
            for (int i = 0; i < spans.length; i++) sp.removeSpan(spans[i]);
            sp.setSpan(mWatcher, 0, mBase.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE | (PRIORITY << Spannable.SPAN_PRIORITY_SHIFT));
        }
    }

    private void reflowAll() {
        Builder p = mParams;
        CharSequence text = mDisplay;
        mLines = StaticLayout.Builder.obtain(text, 0, text.length(), getPaint(), getWidth())
                .setAlignment(p.mAlignment).setTextDirection(p.mTextDir).setLineSpacing(p.mSpacingAdd, p.mSpacingMult)
                .setIncludePad(p.mIncludePad).setUseLineSpacingFromFallbacks(p.mFallbackLineSpacing)
                .setEllipsize(p.mEllipsize).setEllipsizedWidth(p.mEllipsizedWidth).setBreakStrategy(p.mBreakStrategy)
                .setHyphenationFrequency(p.mHyphenationFrequency).setJustificationMode(p.mJustificationMode)
                .setLineBreakConfig(p.mLineBreakConfig).build();
    }

    /** Hidden AOSP API: reflows after an edit of {@code before} chars replaced by {@code after} at {@code where}. */
    public void reflow(CharSequence s, int where, int before, int after) {
        if (s != mBase) return;
        reflowAll();
    }

    @Override
    public int getLineCount() { return mLines.getLineCount(); }

    @Override
    public int getLineTop(int line) { return mLines.getLineTop(line); }

    @Override
    public int getLineDescent(int line) { return mLines.getLineDescent(line); }

    @Override
    public int getLineExtra(int line) { return mLines.getLineExtra(line); }

    @Override
    public int getLineStart(int line) { return mLines.getLineStart(line); }

    @Override
    public boolean getLineContainsTab(int line) { return mLines.getLineContainsTab(line); }

    @Override
    public int getParagraphDirection(int line) { return DIR_LEFT_TO_RIGHT; }

    @Override
    public final Directions getLineDirections(int line) { return DIRS_ALL_LEFT_TO_RIGHT; }

    @Override
    public int getTopPadding() { return mLines.getTopPadding(); }

    @Override
    public int getBottomPadding() { return mLines.getBottomPadding(); }

    @Override
    public int getEllipsizedWidth() { return mParams.mEllipsizedWidth; }

    @Override
    public int getEllipsisStart(int line) { return mParams.mEllipsize == null ? 0 : mLines.getEllipsisStart(line); }

    @Override
    public int getEllipsisCount(int line) { return mParams.mEllipsize == null ? 0 : mLines.getEllipsisCount(line); }

    @Override
    public LineBreakConfig getLineBreakConfig() { return mParams.mLineBreakConfig; }

    private static class ChangeWatcher implements TextWatcher, SpanWatcher {
        private final WeakReference<DynamicLayout> mLayout;

        ChangeWatcher(DynamicLayout layout) { mLayout = new WeakReference<DynamicLayout>(layout); }

        private void reflow(CharSequence s, int where, int before, int after) {
            DynamicLayout ml = mLayout.get();
            if (ml != null) {
                ml.reflow(s, where, before, after);
            } else if (s instanceof Spannable) {
                ((Spannable) s).removeSpan(this);
            }
        }

        public void beforeTextChanged(CharSequence s, int where, int before, int after) {}

        public void onTextChanged(CharSequence s, int where, int before, int after) { reflow(s, where, before, after); }

        public void afterTextChanged(Editable s) {}

        public void onSpanAdded(Spannable s, Object o, int start, int end) {
            if (o instanceof UpdateLayout || o instanceof WrapTogetherSpan) reflow(s, start, end - start, end - start);
        }

        public void onSpanRemoved(Spannable s, Object o, int start, int end) {
            if (o instanceof UpdateLayout || o instanceof WrapTogetherSpan) reflow(s, start, end - start, end - start);
        }

        public void onSpanChanged(Spannable s, Object o, int start, int end, int nstart, int nend) {
            if (o instanceof UpdateLayout || o instanceof WrapTogetherSpan) {
                if (start > s.length()) start = s.length();
                reflow(s, start, end - start, end - start);
                reflow(s, nstart, nend - nstart, nend - nstart);
            }
        }
    }
}
