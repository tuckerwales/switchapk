package com.example.text;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

/** Logic checks for android.text; each logs "TEXTCHECK ok name" or "TEXTCHECK FAIL name detail". */
final class SelfTest {
    static final String TAG = "TextTest";
    static int failures;

    static void check(String name, boolean ok, Object detail) {
        if (ok) {
            Log.i(TAG, "TEXTCHECK ok " + name);
        } else {
            failures++;
            Log.e(TAG, "TEXTCHECK FAIL " + name + " " + detail);
        }
    }

    static void run() {
        spans();
        builder();
        utils();
        layouts();
        Log.i(TAG, "TEXTCHECK done failures=" + failures);
    }

    private static void spans() {
        SpannableString s = new SpannableString("hello world");
        ForegroundColorSpan red = new ForegroundColorSpan(Color.RED);
        StyleSpan bold = new StyleSpan(Typeface.BOLD);
        s.setSpan(red, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(bold, 3, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        check("getSpans", s.getSpans(0, 11, Object.class).length == 2, s.getSpans(0, 11, Object.class).length);
        check("getSpansRange", s.getSpans(6, 7, ForegroundColorSpan.class).length == 0, "");
        check("spanStart", s.getSpanStart(bold) == 3 && s.getSpanEnd(bold) == 8, s.getSpanStart(bold));
        check("nextTransition", s.nextSpanTransition(0, 11, Object.class) == 3, s.nextSpanTransition(0, 11, Object.class));
        CharSequence sub = s.subSequence(2, 6);
        check("subSequenceSpans", sub instanceof Spanned && ((Spanned) sub).getSpans(0, 4, Object.class).length == 2, sub);
    }

    private static void builder() {
        SpannableStringBuilder b = new SpannableStringBuilder("abcdef");
        UnderlineSpan u = new UnderlineSpan();
        b.setSpan(u, 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.insert(0, "XY");
        check("insertShiftsSpan", b.getSpanStart(u) == 4 && b.getSpanEnd(u) == 6, b.getSpanStart(u) + "," + b.getSpanEnd(u));
        b.replace(4, 6, "");
        check("emptyExclusiveRemoved", b.getSpanStart(u) == -1, b.getSpanStart(u));
        Object mark = new Object();
        b.setSpan(mark, 2, 2, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        b.insert(2, "zz");
        check("inclusiveGrows", b.getSpanStart(mark) == 2 && b.getSpanEnd(mark) == 4, b.getSpanStart(mark) + "," + b.getSpanEnd(mark));
        check("toString", "XYzzabef".equals(b.toString()), b.toString());
        final int[] calls = new int[3];
        b.setSpan(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { calls[0]++; }
            public void onTextChanged(CharSequence s, int st, int bf, int c) { calls[1]++; }
            public void afterTextChanged(Editable s) { calls[2]++; }
        }, 0, b.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        b.append("!");
        check("watchers", calls[0] == 1 && calls[1] == 1 && calls[2] == 1, calls[0] + "," + calls[1] + "," + calls[2]);
        Selection.setSelection(b, 3);
        check("selection", Selection.getSelectionStart(b) == 3 && Selection.getSelectionEnd(b) == 3, Selection.getSelectionStart(b));
        b.insert(1, "Q");
        check("selectionMoves", Selection.getSelectionStart(b) == 4, Selection.getSelectionStart(b));
        b.delete(0, b.length());
        check("clear", b.length() == 0 && Selection.getSelectionStart(b) == 0, b.length());
    }

    private static void utils() {
        check("isEmpty", TextUtils.isEmpty(null) && TextUtils.isEmpty("") && !TextUtils.isEmpty("a"), "");
        check("split", TextUtils.split("a,b,,c", ",").length == 4, TextUtils.split("a,b,,c", ",").length);
        check("htmlEncode", "&lt;a&gt; &amp; &quot;".equals(TextUtils.htmlEncode("<a> & \"")), TextUtils.htmlEncode("<a> & \""));
        check("isDigitsOnly", TextUtils.isDigitsOnly("0123") && !TextUtils.isDigitsOnly("12a"), "");
        check("capsMode", TextUtils.getCapsMode("Hi. ", 4, TextUtils.CAP_MODE_SENTENCES) == TextUtils.CAP_MODE_SENTENCES, "");
        CharSequence c = TextUtils.concat("a", new SpannableString("b"), "c");
        check("concat", "abc".equals(c.toString()) && c instanceof Spanned, c);
        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        p.setTextSize(20);
        CharSequence e = TextUtils.ellipsize("The quick brown fox jumps over the lazy dog", p, 120, TextUtils.TruncateAt.END);
        check("ellipsizeEnd", e.toString().endsWith("…") && p.measureText(e.toString()) <= 120, e);
        CharSequence m = TextUtils.expandTemplate("^1 and ^2", "x", "y");
        check("expandTemplate", "x and y".equals(m.toString()), m);
    }

    private static void layouts() {
        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        p.setTextSize(20);
        String text = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs.";
        StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), p, 200).build();
        boolean widthsOk = true;
        for (int i = 0; i < sl.getLineCount(); i++) if (sl.getLineMax(i) > 200) widthsOk = false;
        check("staticWraps", sl.getLineCount() >= 3 && widthsOk, sl.getLineCount());
        check("lineStartsAtWord", sl.getLineStart(1) > 0 && text.charAt(sl.getLineStart(1) - 1) == ' ', sl.getLineStart(1));
        check("lineForOffset", sl.getLineForOffset(text.length()) == sl.getLineCount() - 1, sl.getLineForOffset(text.length()));
        check("height", sl.getHeight() == sl.getLineTop(sl.getLineCount()) && sl.getHeight() > 0, sl.getHeight());
        float x = sl.getPrimaryHorizontal(4);
        check("primaryHorizontal", Math.abs(x - p.measureText("The ")) < 0.5f, x);
        check("offsetForHorizontal", sl.getOffsetForHorizontal(0, x + 1) == 4, sl.getOffsetForHorizontal(0, x + 1));
        StaticLayout two = StaticLayout.Builder.obtain("a\nb\n", 0, 4, p, 200).build();
        check("newlines", two.getLineCount() == 3 && two.getLineStart(1) == 2 && two.getLineStart(2) == 4, two.getLineCount());
        StaticLayout el = StaticLayout.Builder.obtain(text, 0, text.length(), p, 200).setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END).build();
        check("maxLinesEllipsis", el.getLineCount() == 2 && el.getEllipsisCount(1) > 0, el.getLineCount() + "," + el.getEllipsisCount(1));
        BoringLayout.Metrics bm = BoringLayout.isBoring("single line", p);
        check("isBoring", bm != null && bm.width > 0 && BoringLayout.isBoring("two\nlines", p) == null, bm);
        BoringLayout bl = BoringLayout.make("single line", p, 300, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, bm, true);
        check("boringLayout", bl.getLineCount() == 1 && bl.getLineMax(0) == bm.width, bl.getLineMax(0));
        SpannableStringBuilder ed = new SpannableStringBuilder("short");
        DynamicLayout dl = new DynamicLayout(ed, p, 200, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, true);
        check("dynamicInitial", dl.getLineCount() == 1, dl.getLineCount());
        ed.append(" text that keeps going and going until it wraps");
        check("dynamicReflow", dl.getLineCount() > 1, dl.getLineCount());
        float desired = Layout.getDesiredWidth("abc", p);
        check("desiredWidth", Math.abs(desired - p.measureText("abc")) < 0.5f, desired);
    }
}
