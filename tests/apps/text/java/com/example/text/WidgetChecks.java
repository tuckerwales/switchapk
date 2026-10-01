package com.example.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.TextKeyListener;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Calendar;
import java.util.TimeZone;

/** TextView behaviour that the layout screenshot cannot prove on its own. */
final class WidgetChecks {
    private WidgetChecks() {}

    static void run(Context ctx) {
        wrap(ctx);
        ellipsis(ctx);
        gravity(ctx);
        spans(ctx);
        watcher(ctx);
        password(ctx);
        singleLine(ctx);
        lengthFilter(ctx);
        hint(ctx);
        compound(ctx);
        editor(ctx);
        markup(ctx);
        links(ctx);
        meta();
    }

    private static TextView tv(Context ctx) {
        TextView v = new TextView(ctx);
        v.setAllCaps(false);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20);
        v.setTextColor(Color.BLACK);
        return v;
    }

    private static void measure(TextView v, int width, int heightMode) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, heightMode));
    }

    private static String words(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("word ");
        return sb.toString();
    }

    private static void wrap(Context ctx) {
        TextView v = tv(ctx);
        v.setText(words(30));
        measure(v, 200, View.MeasureSpec.UNSPECIFIED);
        Layout l = v.getLayout();
        boolean widths = l != null;
        if (l != null) {
            for (int i = 0; i < l.getLineCount(); i++) {
                if (l.getLineWidth(i) > l.getWidth() + 1f) widths = false;
            }
        }
        SelfTest.check("tvWrap", l != null && l.getLineCount() >= 3 && widths && l.getWidth() <= 200, l);
    }

    private static void ellipsis(Context ctx) {
        TextView v = tv(ctx);
        v.setMaxLines(2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setText(words(40));
        measure(v, 200, View.MeasureSpec.UNSPECIFIED);
        Layout l = v.getLayout();
        int count = l != null && l.getLineCount() > 1 ? l.getEllipsisCount(1) : -1;
        SelfTest.check("tvEllipsis", l != null && l.getLineCount() == 2 && count > 0, count);
    }

    private static void gravity(Context ctx) {
        TextView v = tv(ctx);
        v.setText("Centered");
        v.setGravity(Gravity.CENTER);
        measure(v, 400, View.MeasureSpec.EXACTLY);
        Layout l = v.getLayout();
        SelfTest.check("tvGravityCenter", l != null && l.getParagraphAlignment(0) == Layout.Alignment.ALIGN_CENTER, l);
        v.setGravity(Gravity.END);
        l = v.getLayout();
        SelfTest.check("tvGravityEnd", l != null && l.getParagraphAlignment(0) == Layout.Alignment.ALIGN_OPPOSITE, l);
        v.setGravity(Gravity.START);
        l = v.getLayout();
        SelfTest.check("tvGravityStart", l != null && l.getParagraphAlignment(0) == Layout.Alignment.ALIGN_NORMAL, l);
    }

    private static void spans(Context ctx) {
        TextView v = tv(ctx);
        SpannableString s = new SpannableString("Red bold");
        s.setSpan(new ForegroundColorSpan(Color.RED), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(s);
        boolean ok = v.getText() instanceof Spanned
                && ((Spanned) v.getText()).getSpans(0, 8, ForegroundColorSpan.class).length == 1;
        SelfTest.check("tvSpanKept", ok, v.getText());
    }

    private static void watcher(Context ctx) {
        TextView v = tv(ctx);
        v.setText("hi", TextView.BufferType.EDITABLE);
        final int[] n = new int[1];
        v.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            public void afterTextChanged(Editable s) { n[0]++; }
        });
        v.append("!");
        SelfTest.check("tvWatcher", n[0] == 1 && "hi!".equals(v.getText().toString()), n[0] + " " + v.getText());
    }

    private static void password(Context ctx) {
        TextView v = tv(ctx);
        v.setText("secret");
        v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        measure(v, 400, View.MeasureSpec.UNSPECIFIED);
        Layout l = v.getLayout();
        boolean dots = l != null && l.getText().length() == 6 && l.getText().charAt(0) == '\u2022';
        SelfTest.check("tvPassword", "secret".equals(v.getText().toString()) && dots, l != null ? l.getText() : null);
    }

    private static void singleLine(Context ctx) {
        TextView v = tv(ctx);
        v.setSingleLine(true);
        v.setText("a\nb");
        measure(v, 400, View.MeasureSpec.UNSPECIFIED);
        Layout l = v.getLayout();
        String shown = l != null ? l.getText().toString() : "";
        SelfTest.check("tvSingleLine", l != null && l.getLineCount() == 1 && "a b".equals(shown), shown);
    }

    private static void lengthFilter(Context ctx) {
        TextView v = tv(ctx);
        v.setFilters(new InputFilter[] { new InputFilter.LengthFilter(3) });
        v.setText("abcdef", TextView.BufferType.EDITABLE);
        SelfTest.check("tvMaxLength", "abc".equals(v.getText().toString()), v.getText());
    }

    private static void hint(Context ctx) {
        TextView v = tv(ctx);
        v.setText("");
        v.setHint("Hint");
        measure(v, 200, View.MeasureSpec.UNSPECIFIED);
        SelfTest.check("tvHint", v.getMeasuredHeight() > 0 && v.getLayout() != null, v.getMeasuredHeight());
    }

    private static void compound(Context ctx) {
        TextView v = tv(ctx);
        Drawable box = new Drawable() {
            public void draw(Canvas canvas) {}

            public void setAlpha(int alpha) {}

            public void setColorFilter(ColorFilter colorFilter) {}

            public int getOpacity() { return PixelFormat.TRANSLUCENT; }

            public int getIntrinsicWidth() { return 20; }

            public int getIntrinsicHeight() { return 10; }
        };
        int before = v.getCompoundPaddingLeft();
        v.setCompoundDrawables(box, null, null, null);
        v.setCompoundDrawablePadding(4);
        SelfTest.check("tvCompound", v.getCompoundPaddingLeft() == before + 24, v.getCompoundPaddingLeft());
    }

    private static void editor(Context ctx) {
        EditText e = new EditText(ctx);
        e.setAllCaps(false);
        e.setKeyListener(TextKeyListener.getInstance());
        SelfTest.check("editEditable", e.getText() != null && e.getFreezesText(), e.getText());
        e.onKeyDown(KeyEvent.KEYCODE_A, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A));
        SelfTest.check("editKeyA", "a".equals(e.getText().toString()), e.getText());
        e.onKeyDown(KeyEvent.KEYCODE_A, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A));
        e.onKeyDown(KeyEvent.KEYCODE_DEL, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        SelfTest.check("editKeyDel", "a".equals(e.getText().toString()), e.getText());
        EditorInfo info = new EditorInfo();
        InputConnection ic = e.onCreateInputConnection(info);
        boolean committed = ic != null && ic.commitText("hi", 1);
        SelfTest.check("editCommit", committed && "ahi".equals(e.getText().toString()), e.getText());
    }

    private static void markup(Context ctx) {
        Spanned bold = Html.fromHtml("<b>bold</b>");
        StyleSpan[] styles = bold.getSpans(0, bold.length(), StyleSpan.class);
        SelfTest.check("htmlBold", "bold".equals(bold.toString()) && styles.length == 1
                && styles[0].getStyle() == Typeface.BOLD, bold);
        SelfTest.check("htmlBr", "a\nb".equals(Html.fromHtml("a<br>b").toString()), Html.fromHtml("a<br>b"));
        SelfTest.check("htmlP", "a\n\nb\n\n".equals(Html.fromHtml("<p>a</p><p>b</p>").toString()),
                Html.fromHtml("<p>a</p><p>b</p>"));
        Spanned compact = Html.fromHtml("<p>a</p><p>b</p>", Html.FROM_HTML_MODE_COMPACT);
        SelfTest.check("htmlCompact", "a\nb\n".equals(compact.toString()), compact);
        Spanned link = Html.fromHtml("<a href=\"https://e.x/a\">z</a>");
        URLSpan[] urls = link.getSpans(0, link.length(), URLSpan.class);
        SelfTest.check("htmlLink", "z".equals(link.toString()) && urls.length == 1
                && "https://e.x/a".equals(urls[0].getURL()), link);
        Spanned colored = Html.fromHtml("<font color=\"#010203\">c</font>");
        ForegroundColorSpan[] cols = colored.getSpans(0, colored.length(), ForegroundColorSpan.class);
        SelfTest.check("htmlColor", cols.length == 1 && (cols[0].getForegroundColor() & 0xFFFFFF) == 0x010203, colored);
        Spanned css = Html.fromHtml("<span style=\"color:green\">g</span>", Html.FROM_HTML_OPTION_USE_CSS_COLORS);
        ForegroundColorSpan[] greens = css.getSpans(0, css.length(), ForegroundColorSpan.class);
        SelfTest.check("htmlCss", greens.length == 1 && greens[0].getForegroundColor() == 0xFF008000, css);
        Spanned list = Html.fromHtml("<ul><li>item</li></ul>");
        SelfTest.check("htmlLi", list.toString().startsWith("item")
                && list.getSpans(0, list.length(), BulletSpan.class).length == 1, list);
        SelfTest.check("htmlEsc", "a &lt;b&gt; &amp;".equals(Html.escapeHtml("a <b> &")), Html.escapeHtml("a <b> &"));
        SpannableStringBuilder built = new SpannableStringBuilder("bold");
        built.setSpan(new StyleSpan(Typeface.BOLD), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        String html = Html.toHtml(built);
        SelfTest.check("htmlTo", html.contains("<b>") && html.contains("bold") && html.contains("</b>"), html);

        SpannableString web = new SpannableString("see http://example.com/a today");
        boolean added = Linkify.addLinks(web, Linkify.WEB_URLS);
        URLSpan[] found = web.getSpans(0, web.length(), URLSpan.class);
        SelfTest.check("linkWeb", added && found.length == 1 && "http://example.com/a".equals(found[0].getURL()),
                added && found.length == 1 ? found[0].getURL() : "none");
        SpannableString mail = new SpannableString("mail a@b.co please");
        boolean mailed = Linkify.addLinks(mail, Linkify.EMAIL_ADDRESSES);
        URLSpan[] mspans = mail.getSpans(0, mail.length(), URLSpan.class);
        SelfTest.check("linkMail", mailed && mspans.length == 1 && "mailto:a@b.co".equals(mspans[0].getURL()),
                mailed && mspans.length == 1 ? mspans[0].getURL() : "none");
        SpannableString phone = new SpannableString("call 555-010-1234 now");
        boolean ph = Linkify.addLinks(phone, Linkify.PHONE_NUMBERS);
        URLSpan[] ps = phone.getSpans(0, phone.length(), URLSpan.class);
        SelfTest.check("linkPhone", ph && ps.length == 1 && "tel:5550101234".equals(ps[0].getURL()),
                ph && ps.length == 1 ? ps[0].getURL() : "none");
        TextView linked = tv(ctx);
        linked.setAutoLinkMask(Linkify.WEB_URLS);
        linked.setText("go http://example.com/z");
        URLSpan[] auto = linked.getUrls();
        SelfTest.check("autoLink", auto.length == 1 && linked.getMovementMethod() instanceof LinkMovementMethod,
                auto.length);

        SelfTest.check("elapsed", "01:05".equals(DateUtils.formatElapsedTime(65)), DateUtils.formatElapsedTime(65));
        String elapsedHour = DateUtils.formatElapsedTime(3661);
        SelfTest.check("elapsedH", "1:01:01".equals(elapsedHour), elapsedHour);
        SelfTest.check("isToday", DateUtils.isToday(System.currentTimeMillis()), 1);
        String rel = DateUtils.getRelativeTimeSpanString(System.currentTimeMillis() - 5 * DateUtils.MINUTE_IN_MILLIS,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
        SelfTest.check("relMin", "5 minutes ago".equals(rel), rel);
        String when = DateUtils.formatDateTime(null, 0L, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY
                | DateUtils.FORMAT_UTC);
        SelfTest.check("dateFmt", "Thu, Jan 1, 1970".equals(when), when);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(0L);
        String stamp = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", cal).toString();
        SelfTest.check("dfmt", "1970-01-01 00:00".equals(stamp), stamp);
        SelfTest.check("fileSize", "1.02 kB".equals(android.text.format.Formatter.formatFileSize(ctx, 1024)),
                android.text.format.Formatter.formatFileSize(ctx, 1024));
        SelfTest.check("ip", "1.2.3.4".equals(android.text.format.Formatter.formatIpAddress(0x01020304)),
                android.text.format.Formatter.formatIpAddress(0x01020304));
    }

    private static void links(Context ctx) {
        TextView v = tv(ctx);
        SpannableString s = new SpannableString("one two three");
        final int[] clicked = new int[1];
        s.setSpan(new android.text.style.ClickableSpan() {
            public void onClick(View w) { clicked[0]++; }
        }, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new URLSpan("http://example.com"), 8, 13, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(s);
        v.setMovementMethod(LinkMovementMethod.getInstance());
        measure(v, 400, View.MeasureSpec.EXACTLY);
        CharSequence t = v.getText();
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        boolean moved = v.getMovementMethod().onKeyDown(v, (android.text.Spannable) t, KeyEvent.KEYCODE_DPAD_DOWN, down);
        boolean first = android.text.Selection.getSelectionStart(t) == 0 && android.text.Selection.getSelectionEnd(t) == 3;
        KeyEvent center = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
        v.getMovementMethod().onKeyDown(v, (android.text.Spannable) t, KeyEvent.KEYCODE_DPAD_CENTER, center);
        SelfTest.check("linkDpadSelect", moved && first, android.text.Selection.getSelectionStart(t));
        SelfTest.check("linkDpadClick", clicked[0] == 1, clicked[0]);
        v.getMovementMethod().onKeyDown(v, (android.text.Spannable) t, KeyEvent.KEYCODE_DPAD_DOWN, down);
        SelfTest.check("linkDpadNext", android.text.Selection.getSelectionStart(t) == 8, android.text.Selection.getSelectionStart(t));
    }

    private static void meta() {
        SpannableStringBuilder b = new SpannableStringBuilder("x");
        android.text.method.MetaKeyKeyListener.resetMetaState(b);
        android.text.method.TextKeyListener l = TextKeyListener.getInstance();
        l.onKeyDown(null, b, KeyEvent.KEYCODE_ALT_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT));
        int alt = android.text.method.MetaKeyKeyListener.getMetaState(b, android.text.method.MetaKeyKeyListener.META_ALT_ON);
        SelfTest.check("metaAltPressed", alt == 1, alt);
    }
}
