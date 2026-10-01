package com.example.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

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
}
