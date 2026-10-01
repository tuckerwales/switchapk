package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.IdentityHashMap;

/**
 * Mutable text with markup (AOSP SpannableStringBuilder semantics without the
 * interval tree): spans move with edits according to their MARK/POINT flags,
 * empty SPAN_EXCLUSIVE_EXCLUSIVE spans are removed when their text is deleted,
 * TextWatchers and SpanWatchers are notified, and InputFilters apply to
 * replacements.
 */
public class SpannableStringBuilder implements CharSequence, GetChars, Spannable, Editable, Appendable {
    private static final InputFilter[] NO_FILTERS = new InputFilter[0];
    private static final int START_MASK = 0xF0;
    private static final int END_MASK = 0x0F;
    private static final int START_SHIFT = 4;
    private static final int POINT = 2;
    private static final int PARAGRAPH = 3;

    private char[] mText;
    private int mLength;
    private final SpanStore mStore = new SpanStore();
    private InputFilter[] mFilters = NO_FILTERS;
    private int mTextWatcherDepth;

    public SpannableStringBuilder() { this(""); }

    public SpannableStringBuilder(CharSequence text) { this(text, 0, text.length()); }

    public SpannableStringBuilder(CharSequence text, int start, int end) {
        int srclen = end - start;
        if (srclen < 0) throw new StringIndexOutOfBoundsException();
        mText = new char[Math.max(16, srclen + 16)];
        TextUtils.getChars(text, start, end, mText, 0);
        mLength = srclen;
        if (text instanceof Spanned) {
            Spanned sp = (Spanned) text;
            Object[] spans = sp.getSpans(start, end, Object.class);
            for (int i = 0; i < spans.length; i++) {
                if (spans[i] instanceof NoCopySpan) continue;
                int st = sp.getSpanStart(spans[i]) - start;
                int en = sp.getSpanEnd(spans[i]) - start;
                int fl = sp.getSpanFlags(spans[i]);
                if (st < 0) st = 0;
                if (st > end - start) st = end - start;
                if (en < 0) en = 0;
                if (en > end - start) en = end - start;
                setSpan(false, spans[i], st, en, fl, false);
            }
        }
    }

    public static SpannableStringBuilder valueOf(CharSequence source) {
        if (source instanceof SpannableStringBuilder) return (SpannableStringBuilder) source;
        return new SpannableStringBuilder(source);
    }

    public char charAt(int where) {
        if (where < 0 || where >= mLength) {
            throw new IndexOutOfBoundsException("charAt: " + where + " out of 0 ... " + mLength);
        }
        return mText[where];
    }

    public int length() { return mLength; }

    private void resizeFor(int size) {
        if (size <= mText.length) return;
        char[] n = new char[Math.max(size, mText.length * 2)];
        System.arraycopy(mText, 0, n, 0, mLength);
        mText = n;
    }

    public SpannableStringBuilder insert(int where, CharSequence tb, int start, int end) {
        return replace(where, where, tb, start, end);
    }

    public SpannableStringBuilder insert(int where, CharSequence tb) { return replace(where, where, tb, 0, tb.length()); }

    public SpannableStringBuilder delete(int start, int end) {
        SpannableStringBuilder ret = replace(start, end, "", 0, 0);
        if (mStore.count == 0 && mText.length > 64 && mLength < mText.length / 4) {
            char[] n = new char[Math.max(16, mLength * 2)];
            System.arraycopy(mText, 0, n, 0, mLength);
            mText = n;
        }
        return ret;
    }

    public void clear() { replace(0, length(), "", 0, 0); }

    public void clearSpans() {
        for (int i = mStore.count - 1; i >= 0; i--) {
            Object what = mStore.spans[i];
            int ostart = mStore.starts[i];
            int oend = mStore.ends[i];
            mStore.removeAt(i);
            sendSpanRemoved(what, ostart, oend);
        }
    }

    public SpannableStringBuilder append(CharSequence text) {
        int length = length();
        return replace(length, length, text, 0, text.length());
    }

    public SpannableStringBuilder append(CharSequence text, Object what, int flags) {
        int start = length();
        append(text);
        setSpan(what, start, length(), flags);
        return this;
    }

    public SpannableStringBuilder append(CharSequence text, int start, int end) {
        int length = length();
        return replace(length, length, text, start, end);
    }

    public SpannableStringBuilder append(char text) { return append(String.valueOf(text)); }

    public SpannableStringBuilder replace(int start, int end, CharSequence tb) { return replace(start, end, tb, 0, tb.length()); }

    public SpannableStringBuilder replace(final int start, final int end, CharSequence tb, int tbstart, int tbend) {
        checkRange("replace", start, end);
        int filtercount = mFilters.length;
        for (int i = 0; i < filtercount; i++) {
            CharSequence repl = mFilters[i].filter(tb, tbstart, tbend, this, start, end);
            if (repl != null) {
                tb = repl;
                tbstart = 0;
                tbend = repl.length();
            }
        }
        final int origLen = end - start;
        final int newLen = tbend - tbstart;
        if (origLen == 0 && newLen == 0 && !hasNonExclusiveExclusiveSpanAt(tb, tbstart)) return this;

        TextWatcher[] textWatchers = getSpans(start, start + origLen, TextWatcher.class);
        sendBeforeTextChanged(textWatchers, start, origLen, newLen);

        // selection spans on the replaced range keep their meaning
        boolean adjustSelection = origLen != 0 && newLen != 0;
        int selectionStart = 0;
        int selectionEnd = 0;
        if (adjustSelection) {
            selectionStart = Selection.getSelectionStart(this);
            selectionEnd = Selection.getSelectionEnd(this);
        }

        change(start, end, tb, tbstart, tbend);

        if (adjustSelection) {
            boolean changed = false;
            if (selectionStart > start && selectionStart < end) {
                final long diff = selectionStart - start;
                final int offset = Math.toIntExact(diff * newLen / origLen);
                selectionStart = start + offset;
                changed = true;
                setSpan(false, Selection.SELECTION_START, selectionStart, selectionStart, Spanned.SPAN_POINT_POINT, true);
            }
            if (selectionEnd > start && selectionEnd < end) {
                final long diff = selectionEnd - start;
                final int offset = Math.toIntExact(diff * newLen / origLen);
                selectionEnd = start + offset;
                changed = true;
                setSpan(false, Selection.SELECTION_END, selectionEnd, selectionEnd, Spanned.SPAN_POINT_POINT, true);
            }
        }
        sendTextChanged(textWatchers, start, origLen, newLen);
        sendAfterTextChanged(textWatchers);
        return this;
    }

    private static boolean hasNonExclusiveExclusiveSpanAt(CharSequence text, int offset) {
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;
            Object[] spans = spanned.getSpans(offset, offset, Object.class);
            for (Object span : spans) {
                if (spanned.getSpanFlags(span) != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) return true;
            }
        }
        return false;
    }

    private int movePosition(int pos, int flag, int start, int end, int newLen, boolean textIsRemoved) {
        if (pos < start) return pos;
        if (pos > end) return pos + newLen - (end - start);
        // inside or at the edges of the replaced range
        if (flag == POINT) {
            if (textIsRemoved || pos > start) return start + newLen;
            return start + newLen;
        }
        if (flag == PARAGRAPH) {
            if (pos == end) return start + newLen;
            return start;
        }
        if (textIsRemoved && pos == end) return start + newLen;
        return start;
    }

    private void change(int start, int end, CharSequence cs, int csStart, int csEnd) {
        final int replacedLength = end - start;
        final int replacementLength = csEnd - csStart;
        final int nbNewChars = replacementLength - replacedLength;
        final boolean textIsRemoved = replacedLength > 0;

        resizeFor(mLength + nbNewChars);
        System.arraycopy(mText, end, mText, start + replacementLength, mLength - end);
        TextUtils.getChars(cs, csStart, csEnd, mText, start);
        mLength += nbNewChars;

        IdentityHashMap<Object, int[]> changed = null;
        for (int i = mStore.count - 1; i >= 0; i--) {
            int ost = mStore.starts[i];
            int oen = mStore.ends[i];
            int fl = mStore.flags[i];
            int nst = movePosition(ost, (fl & START_MASK) >> START_SHIFT, start, end, replacementLength, textIsRemoved);
            int nen = movePosition(oen, fl & END_MASK, start, end, replacementLength, textIsRemoved);
            if (nst > nen) nen = nst;
            if (textIsRemoved && nst == nen && (fl & Spanned.SPAN_POINT_MARK_MASK) == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    && ost != oen) {
                Object what = mStore.spans[i];
                mStore.removeAt(i);
                sendSpanRemoved(what, ost, oen);
                continue;
            }
            if (nst != ost || nen != oen) {
                mStore.starts[i] = nst;
                mStore.ends[i] = nen;
                if (changed == null) changed = new IdentityHashMap<Object, int[]>();
                changed.put(mStore.spans[i], new int[] {ost, oen, nst, nen});
            }
        }

        if (cs instanceof Spanned) {
            Spanned sp = (Spanned) cs;
            Object[] spans = sp.getSpans(csStart, csEnd, Object.class);
            for (int i = 0; i < spans.length; i++) {
                if (spans[i] instanceof NoCopySpan) continue;
                int st = sp.getSpanStart(spans[i]);
                int en = sp.getSpanEnd(spans[i]);
                if (st < csStart) st = csStart;
                if (en > csEnd) en = csEnd;
                if (getSpanStart(spans[i]) < 0) {
                    int copySpanStart = st - csStart + start;
                    int copySpanEnd = en - csStart + start;
                    int copySpanFlags = sp.getSpanFlags(spans[i]) | Spanned.SPAN_INTERMEDIATE;
                    setSpan(false, spans[i], copySpanStart, copySpanEnd, copySpanFlags, false);
                }
            }
        }

        if (changed != null) {
            for (java.util.Map.Entry<Object, int[]> e : changed.entrySet()) {
                int[] v = e.getValue();
                sendSpanChanged(e.getKey(), v[0], v[1], v[2], v[3]);
            }
        }
    }

    public void setSpan(Object what, int start, int end, int flags) { setSpan(true, what, start, end, flags, true); }

    private void setSpan(boolean send, Object what, int start, int end, int flags, boolean enforceParagraph) {
        checkRange("setSpan", start, end);
        int flagsStart = (flags & START_MASK) >> START_SHIFT;
        if (isInvalidParagraph(start, flagsStart)) {
            if (!enforceParagraph) return;
            throw new RuntimeException("PARAGRAPH span must start at paragraph boundary (" + start + " follows "
                    + charAt(start - 1) + ")");
        }
        int flagsEnd = flags & END_MASK;
        if (isInvalidParagraph(end, flagsEnd)) {
            if (!enforceParagraph) return;
            throw new RuntimeException("PARAGRAPH span must end at paragraph boundary (" + end + " follows "
                    + charAt(end - 1) + ")");
        }
        if (flagsStart == POINT && flagsEnd == 1 && start == end) {
            if (send) android.util.Log.e("SpannableStringBuilder", "SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length");
            return;
        }
        int i = mStore.indexOf(what);
        if (i >= 0) {
            int ostart = mStore.starts[i];
            int oend = mStore.ends[i];
            mStore.starts[i] = start;
            mStore.ends[i] = end;
            mStore.flags[i] = flags;
            if (send) sendSpanChanged(what, ostart, oend, start, end);
            return;
        }
        mStore.add(what, start, end, flags);
        if (send && (flags & Spanned.SPAN_INTERMEDIATE) == 0) sendSpanAdded(what, start, end);
    }

    private boolean isInvalidParagraph(int index, int flag) {
        return flag == PARAGRAPH && index != 0 && index != length() && charAt(index - 1) != '\n';
    }

    public void removeSpan(Object what) { removeSpan(what, 0); }

    public void removeSpan(Object what, int flags) {
        int i = mStore.indexOf(what);
        if (i < 0) return;
        int ostart = mStore.starts[i];
        int oend = mStore.ends[i];
        mStore.removeAt(i);
        sendSpanRemoved(what, ostart, oend);
    }

    public int getSpanStart(Object what) {
        int i = mStore.indexOf(what);
        return i >= 0 ? mStore.starts[i] : -1;
    }

    public int getSpanEnd(Object what) {
        int i = mStore.indexOf(what);
        return i >= 0 ? mStore.ends[i] : -1;
    }

    public int getSpanFlags(Object what) {
        int i = mStore.indexOf(what);
        return i >= 0 ? mStore.flags[i] : 0;
    }

    public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
        return mStore.getSpans(queryStart, queryEnd, kind);
    }

    /** framework-internal (hidden in AOSP). */
    public <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind, boolean sortByInsertionOrder) {
        return mStore.getSpans(queryStart, queryEnd, kind);
    }

    public int nextSpanTransition(int start, int limit, Class kind) { return mStore.nextSpanTransition(start, limit, kind); }

    public CharSequence subSequence(int start, int end) { return new SpannableStringBuilder(this, start, end); }

    public void getChars(int start, int end, char[] dest, int destoff) {
        checkRange("getChars", start, end);
        System.arraycopy(mText, start, dest, destoff, end - start);
    }

    @Override
    public String toString() { return new String(mText, 0, mLength); }

    /** framework-internal (hidden in AOSP). */
    public String substring(int start, int end) { return new String(mText, start, end - start); }

    /** framework-internal (hidden in AOSP). */
    public int getTextWatcherDepth() { return mTextWatcherDepth; }

    private void sendBeforeTextChanged(TextWatcher[] watchers, int start, int before, int after) {
        mTextWatcherDepth++;
        for (TextWatcher w : watchers) w.beforeTextChanged(this, start, before, after);
        mTextWatcherDepth--;
    }

    private void sendTextChanged(TextWatcher[] watchers, int start, int before, int after) {
        mTextWatcherDepth++;
        for (TextWatcher w : watchers) w.onTextChanged(this, start, before, after);
        mTextWatcherDepth--;
    }

    private void sendAfterTextChanged(TextWatcher[] watchers) {
        mTextWatcherDepth++;
        for (TextWatcher w : watchers) w.afterTextChanged(this);
        mTextWatcherDepth--;
    }

    private void sendSpanAdded(Object what, int start, int end) {
        SpanWatcher[] recip = getSpans(start, end, SpanWatcher.class);
        for (SpanWatcher w : recip) w.onSpanAdded(this, what, start, end);
    }

    private void sendSpanRemoved(Object what, int start, int end) {
        SpanWatcher[] recip = getSpans(start, end, SpanWatcher.class);
        for (SpanWatcher w : recip) w.onSpanRemoved(this, what, start, end);
    }

    private void sendSpanChanged(Object what, int oldStart, int oldEnd, int start, int end) {
        SpanWatcher[] spanWatchers = getSpans(Math.min(oldStart, start), Math.min(Math.max(oldEnd, end), length()),
                SpanWatcher.class);
        for (SpanWatcher w : spanWatchers) w.onSpanChanged(this, what, oldStart, oldEnd, start, end);
    }

    private void checkRange(final String operation, int start, int end) {
        if (end < start) {
            throw new IndexOutOfBoundsException(operation + " (" + start + " ... " + end + ") has end before start");
        }
        int len = length();
        if (start > len || end > len) {
            throw new IndexOutOfBoundsException(operation + " (" + start + " ... " + end + ") ends beyond length " + len);
        }
        if (start < 0 || end < 0) {
            throw new IndexOutOfBoundsException(operation + " (" + start + " ... " + end + ") starts before 0");
        }
    }

    /** framework-internal (hidden in AOSP). */
    public void drawText(Canvas c, int start, int end, float x, float y, Paint p) {
        c.drawText(mText, start, end - start, x, y, p);
    }

    /** framework-internal (hidden in AOSP). */
    public float measureText(int start, int end, Paint p) { return p.measureText(mText, start, end - start); }

    /** framework-internal (hidden in AOSP). */
    public int getTextWidths(int start, int end, float[] widths, Paint p) {
        return p.getTextWidths(mText, start, end - start, widths);
    }

    public void setFilters(InputFilter[] filters) {
        if (filters == null) throw new IllegalArgumentException();
        mFilters = filters;
    }

    public InputFilter[] getFilters() { return mFilters; }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Spanned && toString().equals(o.toString())) {
            final Spanned other = (Spanned) o;
            final Object[] otherSpans = other.getSpans(0, other.length(), Object.class);
            final Object[] thisSpans = getSpans(0, length(), Object.class);
            if (mStore.count == otherSpans.length) {
                for (int i = 0; i < thisSpans.length; ++i) {
                    final Object thisSpan = thisSpans[i];
                    final Object otherSpan = otherSpans[i];
                    if (thisSpan == this) {
                        if (other != otherSpan) return false;
                    } else if (!thisSpan.equals(otherSpan)) {
                        return false;
                    }
                    if (getSpanStart(thisSpan) != other.getSpanStart(otherSpan)
                            || getSpanEnd(thisSpan) != other.getSpanEnd(otherSpan)
                            || getSpanFlags(thisSpan) != other.getSpanFlags(otherSpan)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = toString().hashCode();
        hash = hash * 31 + mStore.count;
        for (int i = 0; i < mStore.count; ++i) {
            Object span = mStore.spans[i];
            if (span != this) hash = hash * 31 + span.hashCode();
            hash = hash * 31 + mStore.starts[i];
            hash = hash * 31 + mStore.ends[i];
            hash = hash * 31 + mStore.flags[i];
        }
        return hash;
    }
}
