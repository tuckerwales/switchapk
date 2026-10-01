package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Fills the background behind the spanned run. */
public class BackgroundColorSpan extends CharacterStyle implements UpdateAppearance, ParcelableSpan {
    private final int mColor;

    public BackgroundColorSpan(int color) {
        mColor = color;
    }

    public BackgroundColorSpan(Parcel src) {
        mColor = src.readInt();
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.BACKGROUND_COLOR_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeInt(mColor);
    }

    public int getBackgroundColor() { return mColor; }

    @Override
    public void updateDrawState(TextPaint textPaint) { textPaint.bgColor = mColor; }

    @Override
    public String toString() { return "BackgroundColorSpan{color=#" + String.format("%08X", mColor) + "}"; }
}
