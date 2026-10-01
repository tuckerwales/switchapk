package com.example.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;

/** Draws StaticLayouts directly: plain wrapping, centered, spans, ellipsis. */
public class LayoutsView extends View {
    private final TextPaint mPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

    public LayoutsView(Context context) {
        super(context);
        mPaint.setTextSize(24);
        mPaint.setColor(Color.BLACK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        String text = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs.";
        // 1: plain wrap in a 300px column at (20, 20).
        StaticLayout plain = StaticLayout.Builder.obtain(text, 0, text.length(), mPaint, 300).build();
        canvas.save();
        canvas.translate(20, 20);
        plain.draw(canvas);
        canvas.restore();
        // 2: centered at (400, 20).
        StaticLayout center = StaticLayout.Builder.obtain(text, 0, text.length(), mPaint, 300)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        canvas.save();
        canvas.translate(400, 20);
        center.draw(canvas);
        canvas.restore();
        // 3: spans at (20, 260).
        SpannableStringBuilder sb = new SpannableStringBuilder();
        append(sb, "Red ", new ForegroundColorSpan(0xFFFF0000));
        append(sb, "bold ", new StyleSpan(Typeface.BOLD));
        append(sb, "under ", new UnderlineSpan());
        append(sb, "strike ", new StrikethroughSpan());
        append(sb, "big ", new RelativeSizeSpan(2f));
        append(sb, "bg", new BackgroundColorSpan(0xFF00FF00));
        sb.append("\n");
        int q = sb.length();
        sb.append("Quoted paragraph text\n");
        sb.setSpan(new QuoteSpan(0xFF0000FF), q, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int bl = sb.length();
        sb.append("Bulleted item");
        sb.setSpan(new BulletSpan(10, 0xFFFF00FF), bl, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        append(sb, " small", new AbsoluteSizeSpan(14));
        StaticLayout spans = StaticLayout.Builder.obtain(sb, 0, sb.length(), mPaint, 700).build();
        canvas.save();
        canvas.translate(20, 260);
        spans.draw(canvas);
        canvas.restore();
        // 4: ellipsized two lines at (800, 20).
        StaticLayout el = StaticLayout.Builder.obtain(text, 0, text.length(), mPaint, 300).setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END).build();
        canvas.save();
        canvas.translate(800, 20);
        el.draw(canvas);
        canvas.restore();
    }

    private static void append(SpannableStringBuilder sb, String s, Object span) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
