package android.view.contentcapture;

import android.os.Parcel;
import android.os.Parcelable;

/** Identifier for a {@link ContentCaptureSession}. Ported from AOSP. */
public final class ContentCaptureSessionId implements Parcelable {
    private final int mValue;

    ContentCaptureSessionId(int value) { mValue = value; }

    @Override
    public int hashCode() { return 31 + mValue; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return mValue == ((ContentCaptureSessionId) obj).mValue;
    }

    @Override
    public String toString() { return Integer.toString(mValue); }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) { parcel.writeInt(mValue); }

    public static final Parcelable.Creator<ContentCaptureSessionId> CREATOR =
            new Parcelable.Creator<ContentCaptureSessionId>() {
        @Override
        public ContentCaptureSessionId createFromParcel(Parcel parcel) {
            return new ContentCaptureSessionId(parcel.readInt());
        }

        @Override
        public ContentCaptureSessionId[] newArray(int size) { return new ContentCaptureSessionId[size]; }
    };
}
