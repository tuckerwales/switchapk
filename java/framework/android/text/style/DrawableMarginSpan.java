package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;

/** Draws a drawable in the leading margin of a paragraph (AOSP DrawableMarginSpan). */
public class DrawableMarginSpan implements LeadingMarginSpan, LineHeightSpan {
    private static final int STANDARD_PAD_WIDTH = 0;
    private final Drawable mDrawable;
    private final int mPad;

    public DrawableMarginSpan(Drawable drawable) { this(drawable, STANDARD_PAD_WIDTH); }

    public DrawableMarginSpan(Drawable drawable, int pad) {
        mDrawable = drawable;
        mPad = pad;
    }

    public int getLeadingMargin(boolean first) { return mDrawable.getIntrinsicWidth() + mPad; }

    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
            CharSequence text, int start, int end, boolean first, Layout layout) {
        int st = ((Spanned) text).getSpanStart(this);
        int ix = x;
        int itop = layout.getLineTop(layout.getLineForOffset(st));
        int dw = mDrawable.getIntrinsicWidth();
        int dh = mDrawable.getIntrinsicHeight();
        mDrawable.setBounds(ix, itop, ix + dw, itop + dh);
        mDrawable.draw(c);
    }

    public void chooseHeight(CharSequence text, int start, int end, int istartv, int v, Paint.FontMetricsInt fm) {
        if (end == ((Spanned) text).getSpanEnd(this)) {
            int ht = mDrawable.getIntrinsicHeight();
            int need = ht - (v + fm.descent - fm.ascent - istartv);
            if (need > 0) fm.descent += need;
            need = ht - (v + fm.bottom - fm.top - istartv);
            if (need > 0) fm.bottom += need;
        }
    }

    public Drawable getDrawable() { return mDrawable; }

    public int getPadding() { return mPad; }

    @Override
    public String toString() { return "DrawableMarginSpan{drawable=" + mDrawable + ", padding=" + mPad + "}"; }
}
