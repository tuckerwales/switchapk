package android.text.style;

import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.TextUtils;

/** Paragraph style that overrides the layout alignment (AOSP AlignmentSpan). */
public interface AlignmentSpan extends ParagraphStyle {
    Layout.Alignment getAlignment();

    class Standard implements AlignmentSpan, ParcelableSpan {
        private final Layout.Alignment mAlignment;

        public Standard(Layout.Alignment align) { mAlignment = align; }

        public Standard(Parcel src) { mAlignment = Layout.Alignment.valueOf(src.readString()); }

        public int getSpanTypeId() { return getSpanTypeIdInternal(); }

        /** Hidden AOSP API. */
        public int getSpanTypeIdInternal() { return TextUtils.ALIGNMENT_SPAN; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) { writeToParcelInternal(dest, flags); }

        /** Hidden AOSP API. */
        public void writeToParcelInternal(Parcel dest, int flags) { dest.writeString(mAlignment.name()); }

        public Layout.Alignment getAlignment() { return mAlignment; }

        @Override
        public String toString() { return "AlignmentSpan.Standard{alignment=" + mAlignment + "}"; }
    }
}
