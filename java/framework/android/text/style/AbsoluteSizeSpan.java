package android.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Sets an absolute text size in px or dip (AOSP AbsoluteSizeSpan). */
public class AbsoluteSizeSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final int mSize;
    private final boolean mDip;

    public AbsoluteSizeSpan(int size) { this(size, false); }

    public AbsoluteSizeSpan(int size, boolean dip) {
        mSize = size;
        mDip = dip;
    }

    public AbsoluteSizeSpan(Parcel src) {
        mSize = src.readInt();
        mDip = src.readInt() != 0;
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.ABSOLUTE_SIZE_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeInt(mSize);
        dest.writeInt(mDip ? 1 : 0);
    }

    public int getSize() { return mSize; }

    public boolean getDip() { return mDip; }

    @Override
    public void updateDrawState(TextPaint ds) { updateMeasureState(ds); }

    @Override
    public void updateMeasureState(TextPaint ds) {
        if (mDip) ds.setTextSize(mSize * ds.density);
        else ds.setTextSize(mSize);
    }

    @Override
    public String toString() { return "AbsoluteSizeSpan{size=" + mSize + ", isDip=" + mDip + "}"; }
}
