package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Strikes through the spanned run. */
public class StrikethroughSpan extends CharacterStyle implements UpdateAppearance, ParcelableSpan {
    public StrikethroughSpan() {}

    public StrikethroughSpan(Parcel src) {}

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.STRIKETHROUGH_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {}

    @Override
    public void updateDrawState(TextPaint ds) { ds.setStrikeThruText(true); }

    @Override
    public String toString() { return "StrikethroughSpan{}"; }
}
