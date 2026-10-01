package android.text.style;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Changes the font family or typeface of the spanned run (AOSP TypefaceSpan). */
public class TypefaceSpan extends MetricAffectingSpan implements ParcelableSpan {
    private final String mFamily;
    private final Typeface mTypeface;

    public TypefaceSpan(String family) { this(family, null); }

    public TypefaceSpan(Typeface typeface) { this(null, typeface); }

    public TypefaceSpan(Parcel src) {
        mFamily = src.readString();
        mTypeface = null;
    }

    private TypefaceSpan(String family, Typeface typeface) {
        mFamily = family;
        mTypeface = typeface;
    }

    public int getSpanTypeId() { return getSpanTypeIdInternal(); }

    /** Hidden AOSP API. */
    public int getSpanTypeIdInternal() { return TextUtils.TYPEFACE_SPAN; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

    /** Hidden AOSP API. */
    public void writeToParcelInternal(Parcel dest, int flags) { dest.writeString(mFamily); }

    public String getFamily() { return mFamily; }

    public Typeface getTypeface() { return mTypeface; }

    @Override
    public void updateDrawState(TextPaint ds) { updateTypeface(ds); }

    @Override
    public void updateMeasureState(TextPaint paint) { updateTypeface(paint); }

    private void updateTypeface(Paint paint) {
        if (mTypeface != null) {
            paint.setTypeface(mTypeface);
        } else if (mFamily != null) {
            Typeface old = paint.getTypeface();
            int style = old == null ? Typeface.NORMAL : old.getStyle();
            Typeface styled = Typeface.create(mFamily, style);
            int fake = style & ~styled.getStyle();
            if ((fake & Typeface.BOLD) != 0) paint.setFakeBoldText(true);
            if ((fake & Typeface.ITALIC) != 0) paint.setTextSkewX(-0.25f);
            paint.setTypeface(styled);
        }
    }

    @Override
    public String toString() { return "TypefaceSpan{family='" + mFamily + "', typeface=" + mTypeface + "}"; }
}
