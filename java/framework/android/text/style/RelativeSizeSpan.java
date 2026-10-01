package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Scales the text size by a proportion. */
public class RelativeSizeSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final float mProportion;

    public RelativeSizeSpan(float proportion) {
        mProportion = proportion;
    }

    public RelativeSizeSpan(Parcel src) {
        mProportion = src.readFloat();
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.RELATIVE_SIZE_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeFloat(mProportion);
    }

    public float getSizeChange() { return mProportion; }

    @Override
    public void updateDrawState(TextPaint ds) { updateMeasureState(ds); }

    @Override
    public void updateMeasureState(TextPaint ds) { ds.setTextSize(ds.getTextSize() * mProportion); }

    @Override
    public String toString() { return "RelativeSizeSpan{proportion=" + mProportion + "}"; }
}
