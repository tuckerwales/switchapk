package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Scales text horizontally. */
public class ScaleXSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final float mProportion;

    public ScaleXSpan(float proportion) {
        mProportion = proportion;
    }

    public ScaleXSpan(Parcel src) {
        mProportion = src.readFloat();
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.SCALE_X_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeFloat(mProportion);
    }

    public float getScaleX() { return mProportion; }

    @Override
    public void updateDrawState(TextPaint ds) { ds.setTextScaleX(ds.getTextScaleX() * mProportion); }

    @Override
    public void updateMeasureState(TextPaint ds) { ds.setTextScaleX(ds.getTextScaleX() * mProportion); }

    @Override
    public String toString() { return "ScaleXSpan{scaleX=" + mProportion + "}"; }
}
