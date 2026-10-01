package android.text;

import java.lang.reflect.Array;

/**
 * framework-internal. Spans of a text: parallel arrays of (span, start, end,
 * flags) in insertion order. Query semantics follow AOSP: spans that only
 * touch the query range at an edge are excluded unless one of them is empty,
 * and results are ordered by SPAN_PRIORITY (higher first), then insertion.
 */
final class SpanStore {
    Object[] spans = new Object[4];
    int[] starts = new int[4];
    int[] ends = new int[4];
    int[] flags = new int[4];
    int count;

    int indexOf(Object what) {
        for (int i = count - 1; i >= 0; i--) if (spans[i] == what) return i;
        return -1;
    }

    void add(Object what, int start, int end, int f) {
        if (count == spans.length) {
            int n = count * 2;
            Object[] s = new Object[n];
            int[] a = new int[n];
            int[] b = new int[n];
            int[] c = new int[n];
            System.arraycopy(spans, 0, s, 0, count);
            System.arraycopy(starts, 0, a, 0, count);
            System.arraycopy(ends, 0, b, 0, count);
            System.arraycopy(flags, 0, c, 0, count);
            spans = s;
            starts = a;
            ends = b;
            flags = c;
        }
        spans[count] = what;
        starts[count] = start;
        ends[count] = end;
        flags[count] = f;
        count++;
    }

    void removeAt(int i) {
        int n = count - i - 1;
        System.arraycopy(spans, i + 1, spans, i, n);
        System.arraycopy(starts, i + 1, starts, i, n);
        System.arraycopy(ends, i + 1, ends, i, n);
        System.arraycopy(flags, i + 1, flags, i, n);
        count--;
        spans[count] = null;
    }

    void clear() {
        for (int i = 0; i < count; i++) spans[i] = null;
        count = 0;
    }

    static boolean overlaps(int spanStart, int spanEnd, int queryStart, int queryEnd) {
        if (spanStart > queryEnd || spanEnd < queryStart) return false;
        if (spanStart != spanEnd && queryStart != queryEnd) {
            if (spanStart == queryEnd) return false;
            if (spanEnd == queryStart) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    <T> T[] getSpans(int queryStart, int queryEnd, Class<T> kind) {
        if (kind == null) kind = (Class<T>) Object.class;
        int n = 0;
        int[] idx = null;
        for (int i = 0; i < count; i++) {
            if (!overlaps(starts[i], ends[i], queryStart, queryEnd)) continue;
            if (kind != Object.class && !kind.isInstance(spans[i])) continue;
            if (idx == null) idx = new int[count];
            idx[n++] = i;
        }
        T[] ret = (T[]) Array.newInstance(kind, n);
        if (n == 0) return ret;
        // stable sort by priority (descending)
        for (int i = 1; i < n; i++) {
            int v = idx[i];
            int pv = flags[v] & Spanned.SPAN_PRIORITY;
            int j = i - 1;
            while (j >= 0 && (flags[idx[j]] & Spanned.SPAN_PRIORITY) < pv) {
                idx[j + 1] = idx[j];
                j--;
            }
            idx[j + 1] = v;
        }
        for (int i = 0; i < n; i++) ret[i] = (T) spans[idx[i]];
        return ret;
    }

    int nextSpanTransition(int start, int limit, Class kind) {
        if (kind == null) kind = Object.class;
        for (int i = 0; i < count; i++) {
            int st = starts[i];
            int en = ends[i];
            if (st > start && st < limit && kind.isInstance(spans[i])) limit = st;
            if (en > start && en < limit && kind.isInstance(spans[i])) limit = en;
        }
        return limit;
    }
}
