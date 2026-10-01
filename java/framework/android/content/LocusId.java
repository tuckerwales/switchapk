package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public final class LocusId implements Parcelable {
    private final String mId;

    public LocusId(String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id cannot be empty");
        mId = id;
    }

    public String getId() { return mId; }

    @Override
    public int hashCode() { return mId.hashCode(); }

    @Override
    public boolean equals(Object obj) { return obj instanceof LocusId && mId.equals(((LocusId) obj).mId); }

    @Override
    public String toString() { return "LocusId[" + mId.length() + "_chars]"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel parcel, int flags) { parcel.writeString(mId); }

    public static final Parcelable.Creator<LocusId> CREATOR = new Parcelable.Creator<LocusId>() {
        public LocusId createFromParcel(Parcel source) { return new LocusId(source.readString()); }
        public LocusId[] newArray(int size) { return new LocusId[size]; }
    };
}
