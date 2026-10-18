package android.view.contentcapture;

import android.app.ActivityThread;
import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/** Container class representing a request to share data with the content capture service. Ported from AOSP. */
public final class DataShareRequest implements Parcelable {
    private final String mPackageName;
    private final LocusId mLocusId;
    private final String mMimeType;

    public DataShareRequest(LocusId locusId, String mimeType) {
        if (mimeType == null) throw new NullPointerException("mimeType");
        mPackageName = ActivityThread.currentPackageName();
        mLocusId = locusId;
        mMimeType = mimeType;
    }

    private DataShareRequest(String packageName, LocusId locusId, String mimeType) {
        mPackageName = packageName;
        mLocusId = locusId;
        mMimeType = mimeType;
    }

    public String getPackageName() { return mPackageName; }

    public LocusId getLocusId() { return mLocusId; }

    public String getMimeType() { return mMimeType; }

    @Override
    public String toString() {
        return "DataShareRequest { packageName = " + mPackageName + ", locusId = " + mLocusId
                + ", mimeType = " + mMimeType + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataShareRequest that = (DataShareRequest) o;
        return Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mLocusId, that.mLocusId)
                && Objects.equals(mMimeType, that.mMimeType);
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + Objects.hashCode(mPackageName);
        hash = 31 * hash + Objects.hashCode(mLocusId);
        hash = 31 * hash + Objects.hashCode(mMimeType);
        return hash;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeParcelable(mLocusId, flags);
        dest.writeString(mMimeType);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Parcelable.Creator<DataShareRequest> CREATOR = new Parcelable.Creator<DataShareRequest>() {
        @Override
        public DataShareRequest createFromParcel(Parcel in) {
            String packageName = in.readString();
            LocusId locusId = in.readParcelable(null);
            String mimeType = in.readString();
            return new DataShareRequest(packageName, locusId, mimeType);
        }

        @Override
        public DataShareRequest[] newArray(int size) { return new DataShareRequest[size]; }
    };
}
