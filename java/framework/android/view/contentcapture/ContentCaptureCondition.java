package android.view.contentcapture;

import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;

/** Defines a condition for when content capture should be allowed. Ported from AOSP. */
public final class ContentCaptureCondition implements Parcelable {
    public static final int FLAG_IS_REGEX = 0x2;

    private final LocusId mLocusId;
    private final int mFlags;

    public ContentCaptureCondition(LocusId locusId, int flags) {
        if (locusId == null) throw new NullPointerException("locusId");
        mLocusId = locusId;
        mFlags = flags;
    }

    public LocusId getLocusId() { return mLocusId; }

    public int getFlags() { return mFlags; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + mFlags;
        result = prime * result + mLocusId.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ContentCaptureCondition other = (ContentCaptureCondition) obj;
        return mFlags == other.mFlags && mLocusId.equals(other.mLocusId);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder(mLocusId.toString()); // LocusId is PII safe
        if (mFlags != 0) {
            // Same output as AOSP's DebugUtils.flagsToString(ContentCaptureCondition.class, "FLAG_", mFlags).
            string.append(" (");
            int rest = mFlags;
            if ((rest & FLAG_IS_REGEX) != 0) {
                string.append("IS_REGEX");
                rest &= ~FLAG_IS_REGEX;
                if (rest != 0) string.append('|');
            }
            if (rest != 0) string.append("0x").append(Integer.toHexString(rest));
            string.append(')');
        }
        return string.toString();
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mLocusId, flags);
        parcel.writeInt(mFlags);
    }

    public static final Parcelable.Creator<ContentCaptureCondition> CREATOR =
            new Parcelable.Creator<ContentCaptureCondition>() {
        @Override
        public ContentCaptureCondition createFromParcel(Parcel parcel) {
            LocusId locusId = parcel.readParcelable(null);
            return new ContentCaptureCondition(locusId, parcel.readInt());
        }

        @Override
        public ContentCaptureCondition[] newArray(int size) { return new ContentCaptureCondition[size]; }
    };
}
