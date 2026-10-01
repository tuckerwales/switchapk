package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.lang.ref.WeakReference;

/** Replaces text with a Drawable aligned to the bottom, baseline or center (AOSP DynamicDrawableSpan). */
public abstract class DynamicDrawableSpan extends ReplacementSpan {
    public static final int ALIGN_BOTTOM = 0;
    public static final int ALIGN_BASELINE = 1;
    public static final int ALIGN_CENTER = 2;

    protected final int mVerticalAlignment;
    private WeakReference<Drawable> mDrawableRef;

    public DynamicDrawableSpan() { mVerticalAlignment = ALIGN_BOTTOM; }

    protected DynamicDrawableSpan(int verticalAlignment) { mVerticalAlignment = verticalAlignment; }

    public int getVerticalAlignment() { return mVerticalAlignment; }

    public abstract Drawable getDrawable();

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        Drawable d = getCachedDrawable();
        Rect rect = d.getBounds();
        if (fm != null) {
            fm.ascent = -rect.bottom;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return rect.right;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
            Paint paint) {
        Drawable b = getCachedDrawable();
        canvas.save();
        int transY = bottom - b.getBounds().bottom;
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= paint.getFontMetricsInt().descent;
        } else if (mVerticalAlignment == ALIGN_CENTER) {
            transY = top + (bottom - top) / 2 - b.getBounds().height() / 2;
        }
        canvas.translate(x, transY);
        b.draw(canvas);
        canvas.restore();
    }

    private Drawable getCachedDrawable() {
        WeakReference<Drawable> wr = mDrawableRef;
        Drawable d = null;
        if (wr != null) d = wr.get();
        if (d == null) {
            d = getDrawable();
            mDrawableRef = new WeakReference<Drawable>(d);
        }
        return d;
    }

    @Override
    public String toString() { return "DynamicDrawableSpan{verticalAlignment=" + getVerticalAlignment() + ", drawable=" + getDrawable() + "}"; }
}
