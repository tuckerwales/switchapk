package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Sets the text color of the spanned run. */
public class ForegroundColorSpan extends CharacterStyle implements UpdateAppearance, ParcelableSpan {
    private final int mColor;

    public ForegroundColorSpan(int color) {
        mColor = color;
    }

    public ForegroundColorSpan(Parcel src) {
        mColor = src.readInt();
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.FOREGROUND_COLOR_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeInt(mColor);
    }

    public int getForegroundColor() { return mColor; }

    @Override
    public void updateDrawState(TextPaint textPaint) { textPaint.setColor(mColor); }

    @Override
    public String toString() { return "ForegroundColorSpan{color=#" + String.format("%08X", mColor) + "}"; }
}
