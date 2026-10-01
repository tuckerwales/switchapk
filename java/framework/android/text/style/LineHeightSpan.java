package android.text.style;

import android.graphics.Paint;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;

/** Paragraph style that adjusts line heights (AOSP LineHeightSpan). */
public interface LineHeightSpan extends ParagraphStyle, WrapTogetherSpan {
    void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm);

    interface WithDensity extends LineHeightSpan {
        void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                Paint.FontMetricsInt fm, TextPaint paint);
    }

    class Standard implements LineHeightSpan, ParcelableSpan {
        private final int mHeight;

        public Standard(int height) {
            if (height <= 0) throw new IllegalArgumentException("Height:" + height + "must be positive");
            mHeight = height;
        }

        public Standard(Parcel src) { mHeight = src.readInt(); }

        public int getHeight() { return mHeight; }

        public int getSpanTypeId() { return getSpanTypeIdInternal(); }

        /** Hidden AOSP API. */
        public int getSpanTypeIdInternal() { return TextUtils.LINE_HEIGHT_SPAN; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

        /** Hidden AOSP API. */
        public void writeToParcelInternal(Parcel dest, int flags) { dest.writeInt(mHeight); }

        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                Paint.FontMetricsInt fm) {
            final int originHeight = fm.descent - fm.ascent;
            if (originHeight <= 0) return;
            final float ratio = mHeight * 1.0f / originHeight;
            fm.descent = Math.round(fm.descent * ratio);
            fm.ascent = fm.descent - mHeight;
        }
    }
}
