package android.text;

/** Cursor and selection stored as spans on a Spannable (AOSP Selection). */
public class Selection {
    private static final class START implements NoCopySpan {}

    private static final class END implements NoCopySpan {}

    private static final class MEMORY implements NoCopySpan {}

    public static final Object SELECTION_START = new START();
    public static final Object SELECTION_END = new END();
    private static final Object SELECTION_MEMORY = new MEMORY();

    private Selection() {}

    public static final int getSelectionStart(CharSequence text) {
        if (text instanceof Spanned) return ((Spanned) text).getSpanStart(SELECTION_START);
        return -1;
    }

    public static final int getSelectionEnd(CharSequence text) {
        if (text instanceof Spanned) return ((Spanned) text).getSpanStart(SELECTION_END);
        return -1;
    }

    public static void setSelection(Spannable text, int start, int stop) {
        int ostart = getSelectionStart(text);
        int oend = getSelectionEnd(text);
        if (ostart != start || oend != stop) {
            text.setSpan(SELECTION_START, start, start, Spanned.SPAN_POINT_POINT | Spanned.SPAN_INTERMEDIATE);
            text.setSpan(SELECTION_END, stop, stop, Spanned.SPAN_POINT_POINT);
        }
    }

    public static final void setSelection(Spannable text, int index) { setSelection(text, index, index); }

    public static final void selectAll(Spannable text) { setSelection(text, 0, text.length()); }

    public static final void extendSelection(Spannable text, int index) {
        extendSelection(text, index, -1);
    }

    /** framework-internal (hidden in AOSP). */
    public static final void extendSelection(Spannable text, int index, int memory) {
        if (text.getSpanStart(SELECTION_END) != index) {
            text.setSpan(SELECTION_END, index, index, Spanned.SPAN_POINT_POINT);
        }
    }

    public static final void removeSelection(Spannable text) {
        text.removeSpan(SELECTION_START);
        text.removeSpan(SELECTION_END);
        text.removeSpan(SELECTION_MEMORY);
    }

    private static int chooseHorizontal(Layout layout, int direction, int off1, int off2) {
        int line1 = layout.getLineForOffset(off1);
        int line2 = layout.getLineForOffset(off2);
        if (line1 == line2) {
            float h1 = layout.getPrimaryHorizontal(off1);
            float h2 = layout.getPrimaryHorizontal(off2);
            if (direction < 0) return h1 < h2 ? off1 : off2;
            return h1 > h2 ? off1 : off2;
        }
        return direction < 0 ? Math.min(off1, off2) : Math.max(off1, off2);
    }

    public static boolean moveUp(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);
        if (start != end) {
            int min = Math.min(start, end);
            int max = Math.max(start, end);
            setSelection(text, min);
            return !(min == 0 && max == text.length());
        }
        int line = layout.getLineForOffset(end);
        if (line > 0) {
            float h = layout.getPrimaryHorizontal(end);
            int move = layout.getOffsetForHorizontal(line - 1, h);
            setSelection(text, move);
            return true;
        } else if (end != 0) {
            setSelection(text, 0);
            return true;
        }
        return false;
    }

    public static boolean moveDown(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);
        if (start != end) {
            int min = Math.min(start, end);
            int max = Math.max(start, end);
            setSelection(text, max);
            return !(min == 0 && max == text.length());
        }
        int line = layout.getLineForOffset(end);
        if (line < layout.getLineCount() - 1) {
            float h = layout.getPrimaryHorizontal(end);
            int move = layout.getOffsetForHorizontal(line + 1, h);
            setSelection(text, move);
            return true;
        } else if (end != text.length()) {
            setSelection(text, text.length());
            return true;
        }
        return false;
    }

    public static boolean moveLeft(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);
        if (start != end) {
            setSelection(text, chooseHorizontal(layout, -1, start, end));
            return true;
        }
        int to = TextUtils.getOffsetBefore(text, end);
        if (to != end) {
            setSelection(text, to);
            return true;
        }
        return false;
    }

    public static boolean moveRight(Spannable text, Layout layout) {
        int start = getSelectionStart(text);
        int end = getSelectionEnd(text);
        if (start != end) {
            setSelection(text, chooseHorizontal(layout, 1, start, end));
            return true;
        }
        int to = TextUtils.getOffsetAfter(text, end);
        if (to != end) {
            setSelection(text, to);
            return true;
        }
        return false;
    }

    public static boolean extendUp(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int line = layout.getLineForOffset(end);
        if (line > 0) {
            extendSelection(text, layout.getOffsetForHorizontal(line - 1, layout.getPrimaryHorizontal(end)));
            return true;
        } else if (end != 0) {
            extendSelection(text, 0);
            return true;
        }
        return true;
    }

    public static boolean extendDown(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int line = layout.getLineForOffset(end);
        if (line < layout.getLineCount() - 1) {
            extendSelection(text, layout.getOffsetForHorizontal(line + 1, layout.getPrimaryHorizontal(end)));
            return true;
        } else if (end != text.length()) {
            extendSelection(text, text.length());
            return true;
        }
        return true;
    }

    public static boolean extendLeft(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int to = TextUtils.getOffsetBefore(text, end);
        if (to != end) extendSelection(text, to);
        return true;
    }

    public static boolean extendRight(Spannable text, Layout layout) {
        int end = getSelectionEnd(text);
        int to = TextUtils.getOffsetAfter(text, end);
        if (to != end) extendSelection(text, to);
        return true;
    }

    public static boolean extendToLeftEdge(Spannable text, Layout layout) {
        extendSelection(text, findEdge(text, layout, -1));
        return true;
    }

    public static boolean extendToRightEdge(Spannable text, Layout layout) {
        extendSelection(text, findEdge(text, layout, 1));
        return true;
    }

    public static boolean moveToLeftEdge(Spannable text, Layout layout) {
        setSelection(text, findEdge(text, layout, -1));
        return true;
    }

    public static boolean moveToRightEdge(Spannable text, Layout layout) {
        setSelection(text, findEdge(text, layout, 1));
        return true;
    }

    private static int findEdge(Spannable text, Layout layout, int dir) {
        int pt = getSelectionEnd(text);
        int line = layout.getLineForOffset(pt);
        if (dir < 0) return layout.getLineStart(line);
        int end = layout.getLineEnd(line);
        if (line == layout.getLineCount() - 1) return end;
        return end - 1;
    }

    /** framework-internal (hidden in AOSP). */
    public static boolean moveToPreceding(Spannable text, Object iter, boolean extendSelection) { return false; }

    /** framework-internal (hidden in AOSP). */
    public static boolean moveToFollowing(Spannable text, Object iter, boolean extendSelection) { return false; }
}
