package android.text;

import android.graphics.Paint;

/** Paint plus the per-run state spans write into while text is measured or drawn (AOSP TextPaint). */
public class TextPaint extends Paint {
    public int bgColor;
    public int baselineShift;
    public int linkColor;
    public int[] drawableState;
    public float density = 1.0f;
    public int underlineColor = 0;
    public float underlineThickness;

    public TextPaint() { super(); }

    public TextPaint(int flags) { super(flags); }

    public TextPaint(Paint p) { super(p); }

    public void set(TextPaint tp) {
        super.set(tp);
        bgColor = tp.bgColor;
        baselineShift = tp.baselineShift;
        linkColor = tp.linkColor;
        drawableState = tp.drawableState;
        density = tp.density;
        underlineColor = tp.underlineColor;
        underlineThickness = tp.underlineThickness;
    }

    /** Hidden AOSP API: whether the two paints measure text identically. */
    public boolean hasEqualAttributes(TextPaint other) {
        return bgColor == other.bgColor && baselineShift == other.baselineShift && linkColor == other.linkColor
                && drawableState == other.drawableState && density == other.density
                && underlineColor == other.underlineColor && underlineThickness == other.underlineThickness
                && equalsForTextMeasurement(other);
    }

    /** Hidden AOSP API used by spans that set a custom underline. */
    public void setUnderlineText(int color, float thickness) {
        underlineColor = color;
        underlineThickness = thickness;
    }

    @Override
    public float getUnderlineThickness() {
        if (underlineColor != 0) return underlineThickness;
        return super.getUnderlineThickness();
    }
}
