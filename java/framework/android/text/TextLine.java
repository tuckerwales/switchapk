package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.text.style.TabStopSpan;

/**
 * Measures and draws one line of styled text (simplified port of AOSP TextLine,
 * left to right only). A line is split into runs at character style span
 * transitions, tabs and the ellipsis range; each run gets its own TextPaint.
 */
class TextLine {
    static final int TAB_INCREMENT = 20;
    private static final String ELLIPSIS = "…";

    private TextPaint mPaint;
    private CharSequence mText;
    private int mStart;
    private int mLen;
    private char[] mChars = new char[16];
    private Spanned mSpanned;
    private boolean mHasTabs;
    private TabStops mTabs;
    private int mEllipsisStart;
    private int mEllipsisEnd;
    private final TextPaint mWorkPaint = new TextPaint();
    private final Paint.FontMetricsInt mTmpFm = new Paint.FontMetricsInt();

    private static final TextLine[] sCached = new TextLine[3];

    static TextLine obtain() {
        synchronized (sCached) {
            for (int i = sCached.length; --i >= 0;) {
                if (sCached[i] != null) {
                    TextLine tl = sCached[i];
                    sCached[i] = null;
                    return tl;
                }
            }
        }
        return new TextLine();
    }

    static TextLine recycle(TextLine tl) {
        tl.mText = null;
        tl.mPaint = null;
        tl.mSpanned = null;
        tl.mTabs = null;
        synchronized (sCached) {
            for (int i = 0; i < sCached.length; i++) {
                if (sCached[i] == null) {
                    sCached[i] = tl;
                    break;
                }
            }
        }
        return null;
    }

    /** Ellipsis offsets are relative to {@code start}; pass 0, 0 for none. */
    void set(TextPaint paint, CharSequence text, int start, int limit, boolean hasTabs, TabStops tabStops,
            int ellipsisStart, int ellipsisEnd) {
        mPaint = paint;
        mText = text;
        mStart = start;
        mLen = limit - start;
        if (mChars.length < mLen) mChars = new char[Math.max(mLen, mChars.length * 2)];
        TextUtils.getChars(text, start, limit, mChars, 0);
        mSpanned = text instanceof Spanned ? (Spanned) text : null;
        mHasTabs = hasTabs;
        mTabs = tabStops;
        if (ellipsisEnd > ellipsisStart && ellipsisStart >= 0 && ellipsisEnd <= mLen) {
            mEllipsisStart = ellipsisStart;
            mEllipsisEnd = ellipsisEnd;
        } else {
            mEllipsisStart = mEllipsisEnd = 0;
        }
    }

    // ---- runs ----

    /** End (relative) of the run starting at relative offset {@code rs}. */
    private int runEnd(int rs) {
        if (isEllipsisRun(rs)) return mEllipsisEnd;
        return nextTransition(rs, mLen);
    }

    private int nextTransition(int rs, int limit) {
        if (mChars[rs] == '\t') return rs + 1;
        int re = limit;
        if (mSpanned != null) {
            int abs = mStart + rs;
            ReplacementSpan[] reps = mSpanned.getSpans(abs, abs + 1, ReplacementSpan.class);
            for (ReplacementSpan r : reps) {
                if (mSpanned.getSpanStart(r) <= abs && mSpanned.getSpanEnd(r) > abs) {
                    return Math.min(mSpanned.getSpanEnd(r) - mStart, limit);
                }
            }
            re = mSpanned.nextSpanTransition(abs, mStart + limit, CharacterStyle.class) - mStart;
            if (re <= rs) re = rs + 1;
            int repEnd = mSpanned.nextSpanTransition(abs, mStart + re, ReplacementSpan.class) - mStart;
            if (repEnd > rs && repEnd < re) re = repEnd;
        }
        if (mHasTabs) {
            for (int i = rs; i < re; i++) {
                if (mChars[i] == '\t') {
                    re = i;
                    break;
                }
            }
        }
        if (mEllipsisEnd > mEllipsisStart && rs < mEllipsisStart && re > mEllipsisStart) re = mEllipsisStart;
        return re;
    }

    /** Fills mWorkPaint for the run and returns its replacement span, if any. */
    private ReplacementSpan applyStyles(int rs, int re) {
        TextPaint wp = mWorkPaint;
        wp.set(mPaint);
        ReplacementSpan replacement = null;
        final boolean ellipsis = isEllipsisRun(rs);
        if (ellipsis) re = rs + 1;
        if (mSpanned != null) {
            int start = mStart + rs;
            int end = mStart + re;
            CharacterStyle[] spans = mSpanned.getSpans(start, end, CharacterStyle.class);
            for (CharacterStyle span : spans) {
                int ss = mSpanned.getSpanStart(span);
                int se = mSpanned.getSpanEnd(span);
                if (ss >= end || se <= start) continue;
                if (span instanceof ReplacementSpan) {
                    if (!ellipsis) replacement = (ReplacementSpan) span;
                } else {
                    span.updateDrawState(wp);
                }
            }
        }
        return replacement;
    }

    private boolean isEllipsisRun(int rs) { return mEllipsisEnd > mEllipsisStart && rs == mEllipsisStart; }

    private float runWidth(int rs, int re, float x, ReplacementSpan replacement, Paint.FontMetricsInt fmi) {
        TextPaint wp = mWorkPaint;
        if (replacement != null) {
            int ss = Math.max(mSpanned.getSpanStart(replacement), mStart + rs);
            int se = Math.min(mSpanned.getSpanEnd(replacement), mStart + re);
            if (fmi != null) {
                Paint.FontMetricsInt tmp = mTmpFm;
                wp.getFontMetricsInt(tmp);
                int w = replacement.getSize(wp, mText, ss, se, tmp);
                expand(fmi, tmp, 0);
                return w;
            }
            return replacement.getSize(wp, mText, ss, se, null);
        }
        if (fmi != null) {
            wp.getFontMetricsInt(mTmpFm);
            expand(fmi, mTmpFm, wp.baselineShift);
        }
        if (isEllipsisRun(rs)) return wp.measureText(ELLIPSIS);
        if (mChars[rs] == '\t') return nextTab(x) - x;
        return wp.measureText(mChars, rs, re - rs);
    }

    private static void expand(Paint.FontMetricsInt fmi, Paint.FontMetricsInt run, int shift) {
        int ascent = run.ascent, descent = run.descent, top = run.top, bottom = run.bottom;
        if (shift < 0) {
            ascent += shift;
            top += shift;
        } else {
            descent += shift;
            bottom += shift;
        }
        fmi.ascent = Math.min(fmi.ascent, ascent);
        fmi.descent = Math.max(fmi.descent, descent);
        fmi.top = Math.min(fmi.top, top);
        fmi.bottom = Math.max(fmi.bottom, bottom);
    }

    float nextTab(float h) {
        if (mTabs != null) return mTabs.nextTab(h);
        return TabStops.nextDefaultStop(h, TAB_INCREMENT);
    }

    // ---- public operations ----

    /** Width of the whole line; expands {@code fmi} (if non null) with every run's metrics. */
    float metrics(Paint.FontMetricsInt fmi) {
        if (fmi != null) {
            mPaint.getFontMetricsInt(fmi);
        }
        float x = 0;
        for (int rs = 0; rs < mLen;) {
            int re = runEnd(rs);
            ReplacementSpan rep = applyStyles(rs, re);
            x += runWidth(rs, re, x, rep, fmi);
            rs = re;
        }
        return x;
    }

    /** Horizontal offset of the relative position {@code offset} from the line start. */
    float measure(int offset) {
        float x = 0;
        for (int rs = 0; rs < mLen;) {
            int re = runEnd(rs);
            ReplacementSpan rep = applyStyles(rs, re);
            if (offset < re) {
                if (offset <= rs || rep != null || isEllipsisRun(rs) || mChars[rs] == '\t') return x;
                return x + mWorkPaint.measureText(mChars, rs, offset - rs);
            }
            x += runWidth(rs, re, x, rep, null);
            rs = re;
        }
        return x;
    }

    /** Relative offset closest to the horizontal position {@code h}. */
    int getOffsetForAdvance(float h) {
        if (h <= 0) return 0;
        float x = 0;
        for (int rs = 0; rs < mLen;) {
            int re = runEnd(rs);
            ReplacementSpan rep = applyStyles(rs, re);
            float w = runWidth(rs, re, x, rep, null);
            if (h < x + w) {
                if (rep != null || isEllipsisRun(rs) || mChars[rs] == '\t') return h - x < w / 2 ? rs : re;
                int n = re - rs;
                float[] widths = new float[n];
                mWorkPaint.getTextRunAdvances(mChars, rs, n, rs, n, false, widths, 0);
                float cx = x;
                for (int i = 0; i < n; i++) {
                    if (widths[i] == 0 && i > 0) continue;
                    int next = i + 1;
                    while (next < n && widths[next] == 0 && Character.isLowSurrogate(mChars[rs + next])) next++;
                    float cw = 0;
                    for (int k = i; k < next; k++) cw += widths[k];
                    if (h < cx + cw) return h - cx < cw / 2 ? rs + i : rs + next;
                    cx += cw;
                    i = next - 1;
                }
                return re;
            }
            x += w;
            rs = re;
        }
        return mLen;
    }

    void draw(Canvas c, float x, int top, int y, int bottom) {
        float h = 0;
        for (int rs = 0; rs < mLen;) {
            int re = runEnd(rs);
            ReplacementSpan rep = applyStyles(rs, re);
            TextPaint wp = mWorkPaint;
            float w = runWidth(rs, re, h, rep, null);
            if (wp.bgColor != 0) {
                int prevColor = wp.getColor();
                Paint.Style prevStyle = wp.getStyle();
                wp.setColor(wp.bgColor);
                wp.setStyle(Paint.Style.FILL);
                c.drawRect(x + h, top, x + h + w, bottom, wp);
                wp.setStyle(prevStyle);
                wp.setColor(prevColor);
            }
            if (rep != null) {
                int ss = Math.max(mSpanned.getSpanStart(rep), mStart + rs);
                int se = Math.min(mSpanned.getSpanEnd(rep), mStart + re);
                rep.draw(c, mText, ss, se, x + h, top, y, bottom, wp);
            } else if (isEllipsisRun(rs)) {
                c.drawText(ELLIPSIS, x + h, y + wp.baselineShift, wp);
            } else if (mChars[rs] != '\t') {
                int end = re;
                while (end > rs && (mChars[end - 1] == '\n' || mChars[end - 1] == '\r')) end--;
                if (end > rs) c.drawText(mChars, rs, end - rs, x + h, y + wp.baselineShift, wp);
            }
            h += w;
            rs = re;
        }
    }

    static boolean isLineEndSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == 0x1680 || (0x2000 <= ch && ch <= 0x200A && ch != 0x2007)
                || ch == 0x205F || ch == 0x3000;
    }

    /** Tab stops of a paragraph: explicit TabStopSpan offsets then a default increment. */
    static class TabStops {
        private int[] mStops;
        private int mNumStops;
        private float mIncrement;

        TabStops(float increment, Object[] spans) { reset(increment, spans); }

        void reset(float increment, Object[] spans) {
            mIncrement = increment;
            int ns = 0;
            if (spans != null) {
                int[] stops = mStops;
                for (Object o : spans) {
                    if (o instanceof TabStopSpan) {
                        if (stops == null) stops = new int[10];
                        else if (ns == stops.length) {
                            int[] nstops = new int[ns * 2];
                            System.arraycopy(stops, 0, nstops, 0, ns);
                            stops = nstops;
                        }
                        stops[ns++] = ((TabStopSpan) o).getTabStop();
                    }
                }
                if (ns > 1) java.util.Arrays.sort(stops, 0, ns);
                if (stops != mStops) mStops = stops;
            }
            mNumStops = ns;
        }

        float nextTab(float h) {
            int ns = mNumStops;
            if (ns > 0) {
                int[] stops = mStops;
                for (int i = 0; i < ns; ++i) {
                    int stop = stops[i];
                    if (stop > h) return stop;
                }
            }
            return nextDefaultStop(h, mIncrement);
        }

        static float nextDefaultStop(float h, float inc) { return ((int) ((h + inc) / inc)) * inc; }
    }
}
