package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.TextUtils;

/** Paragraph style that indents lines and may draw in the margin (AOSP LeadingMarginSpan). */
public interface LeadingMarginSpan extends ParagraphStyle {
    int getLeadingMargin(boolean first);

    void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text,
            int start, int end, boolean first, Layout layout);

    interface LeadingMarginSpan2 extends LeadingMarginSpan, WrapTogetherSpan {
        int getLeadingMarginLineCount();
    }

    class Standard implements LeadingMarginSpan, ParcelableSpan {
        private final int mFirst, mRest;

        public Standard(int first, int rest) {
            mFirst = first;
            mRest = rest;
        }

        public Standard(int every) { this(every, every); }

        public Standard(Parcel src) {
            mFirst = src.readInt();
            mRest = src.readInt();
        }

        public int getSpanTypeId() { return getSpanTypeIdInternal(); }

        /** Hidden AOSP API. */
        public int getSpanTypeIdInternal() { return TextUtils.LEADING_MARGIN_SPAN; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

        /** Hidden AOSP API. */
        public void writeToParcelInternal(Parcel dest, int flags) {
            dest.writeInt(mFirst);
            dest.writeInt(mRest);
        }

        public int getLeadingMargin(boolean first) { return first ? mFirst : mRest; }

        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                CharSequence text, int start, int end, boolean first, Layout layout) {}
    }
}
