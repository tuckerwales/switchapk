package android.text;

/** framework-internal. Immutable text with a span store (base of SpannableString and SpannedString). */
abstract class SpannableStringInternal {
    private final String mText;
    final SpanStore mStore = new SpanStore();

    SpannableStringInternal(CharSequence source, int start, int end, boolean ignoreNoCopySpan) {
        if (start == 0 && end == source.length()) mText = source.toString();
        else mText = source.toString().substring(start, end);
        if (source instanceof Spanned) {
            Spanned sp = (Spanned) source;
            Object[] spans = sp.getSpans(start, end, Object.class);
            for (Object what : spans) {
                if (ignoreNoCopySpan && what instanceof NoCopySpan) continue;
                int st = sp.getSpanStart(what);
                int en = sp.getSpanEnd(what);
                int fl = sp.getSpanFlags(what);
                if (st < start) st = start;
                if (en > end) en = end;
                setSpan(what, st - start, en - start, fl, false);
            }
        }
    }

    public final int length() { return mText.length(); }

    public final char charAt(int i) { return mText.charAt(i); }

    @Override
    public final String toString() { return mText; }

    public final void getChars(int start, int end, char[] dest, int off) { mText.getChars(start, end, dest, off); }

    void setSpan(Object what, int start, int end, int flags) { setSpan(what, start, end, flags, true); }

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

    private void setSpan(Object what, int start, int end, int flags, boolean enforceParagraph) {
        checkRange("setSpan", start, end);
        int i = mStore.indexOf(what);
        if (i >= 0) {
            int ostart = mStore.starts[i];
            int oend = mStore.ends[i];
            mStore.starts[i] = start;
            mStore.ends[i] = end;
            mStore.flags[i] = flags;
            sendSpanChanged(what, ostart, oend, start, end);
            return;
        }
        mStore.add(what, start, end, flags);
        if ((flags & Spanned.SPAN_INTERMEDIATE) == 0) sendSpanAdded(what, start, end);
    }

    void removeSpan(Object what) {
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

    public int nextSpanTransition(int start, int limit, Class kind) { return mStore.nextSpanTransition(start, limit, kind); }

    private SpanWatcher[] watchers(int start, int end) {
        if (!(this instanceof Spannable)) return new SpanWatcher[0];
        return getSpans(start, end, SpanWatcher.class);
    }

    private void sendSpanAdded(Object what, int start, int end) {
        for (SpanWatcher w : watchers(start, end)) w.onSpanAdded((Spannable) this, what, start, end);
    }

    private void sendSpanRemoved(Object what, int start, int end) {
        for (SpanWatcher w : watchers(start, end)) w.onSpanRemoved((Spannable) this, what, start, end);
    }

    private void sendSpanChanged(Object what, int s, int e, int st, int en) {
        for (SpanWatcher w : watchers(Math.min(s, st), Math.max(e, en))) {
            w.onSpanChanged((Spannable) this, what, s, e, st, en);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Spanned && toString().equals(o.toString())) {
            Spanned other = (Spanned) o;
            Object[] otherSpans = other.getSpans(0, other.length(), Object.class);
            Object[] spans = getSpans(0, length(), Object.class);
            if (spans.length != otherSpans.length) return false;
            for (int i = 0; i < spans.length; i++) {
                Object thisSpan = spans[i];
                Object otherSpan = otherSpans[i];
                if (thisSpan == this) {
                    if (other != otherSpan || getSpanStart(thisSpan) != other.getSpanStart(otherSpan)
                            || getSpanEnd(thisSpan) != other.getSpanEnd(otherSpan)
                            || getSpanFlags(thisSpan) != other.getSpanFlags(otherSpan)) {
                        return false;
                    }
                } else if (!thisSpan.equals(otherSpan) || getSpanStart(thisSpan) != other.getSpanStart(otherSpan)
                        || getSpanEnd(thisSpan) != other.getSpanEnd(otherSpan)
                        || getSpanFlags(thisSpan) != other.getSpanFlags(otherSpan)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = toString().hashCode();
        hash = hash * 31 + mStore.count;
        for (int i = 0; i < mStore.count; i++) {
            Object span = mStore.spans[i];
            if (span != this) hash = hash * 31 + span.hashCode();
            hash = hash * 31 + mStore.starts[i];
            hash = hash * 31 + mStore.ends[i];
            hash = hash * 31 + mStore.flags[i];
        }
        return hash;
    }
}
