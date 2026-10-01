package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Lowers the baseline of the spanned run. */
public class SubscriptSpan extends MetricAffectingSpan implements ParcelableSpan {
    public SubscriptSpan() {}

    public SubscriptSpan(Parcel src) {}

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.SUBSCRIPT_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {}

    @Override
    public void updateDrawState(TextPaint textPaint) { textPaint.baselineShift -= (int) (textPaint.ascent() / 2); }

    @Override
    public void updateMeasureState(TextPaint textPaint) { textPaint.baselineShift -= (int) (textPaint.ascent() / 2); }

    @Override
    public String toString() { return "SubscriptSpan{}"; }
}
