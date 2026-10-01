package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextUtils;

/** Paragraph style that draws behind each line (AOSP LineBackgroundSpan). */
public interface LineBackgroundSpan extends ParagraphStyle {
    void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline, int bottom,
            CharSequence text, int start, int end, int lineNumber);

    class Standard implements LineBackgroundSpan, ParcelableSpan {
        private final int mColor;

        public Standard(int color) { mColor = color; }

        public Standard(Parcel src) { mColor = src.readInt(); }

        public int getSpanTypeId() { return getSpanTypeIdInternal(); }

        /** Hidden AOSP API. */
        public int getSpanTypeIdInternal() { return TextUtils.LINE_BACKGROUND_SPAN; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

        /** Hidden AOSP API. */
        public void writeToParcelInternal(Parcel dest, int flags) { dest.writeInt(mColor); }

        public final int getColor() { return mColor; }

        public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline,
                int bottom, CharSequence text, int start, int end, int lineNumber) {
            final int originColor = paint.getColor();
            paint.setColor(mColor);
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(originColor);
        }
    }
}
