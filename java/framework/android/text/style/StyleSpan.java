package android.text.style;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Applies a Typeface style (bold, italic) to the spanned run (AOSP StyleSpan). */
public class StyleSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final int mStyle;
    private final int mFontWeightAdjustment;

    public StyleSpan(int style) { this(style, Integer.MAX_VALUE); }

    public StyleSpan(int style, int fontWeightAdjustment) {
        mStyle = style;
        mFontWeightAdjustment = fontWeightAdjustment;
    }

    public StyleSpan(Parcel src) {
        mStyle = src.readInt();
        mFontWeightAdjustment = src.readInt();
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.STYLE_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeInt(mStyle);
        dest.writeInt(mFontWeightAdjustment);
    }

    public int getStyle() { return mStyle; }

    public int getFontWeightAdjustment() { return mFontWeightAdjustment; }

    @Override
    public void updateDrawState(TextPaint ds) { apply(ds, mStyle); }

    @Override
    public void updateMeasureState(TextPaint paint) { apply(paint, mStyle); }

    @Override
    public String toString() { return "StyleSpan{style=" + mStyle + ", fontWeightAdjustment=" + mFontWeightAdjustment + "}"; }

    private static void apply(Paint paint, int style) {
        Typeface old = paint.getTypeface();
        int oldStyle = old == null ? 0 : old.getStyle();
        int want = oldStyle | style;
        Typeface tf = old == null ? Typeface.defaultFromStyle(want) : Typeface.create(old, want);
        int fake = want & ~tf.getStyle();
        if ((fake & Typeface.BOLD) != 0) paint.setFakeBoldText(true);
        if ((fake & Typeface.ITALIC) != 0) paint.setTextSkewX(-0.25f);
        paint.setTypeface(tf);
    }
}
