package android.view.contentcapture;

import android.app.ActivityThread;
import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Class used by apps to request the content capture service to remove data. Ported from AOSP. */
public final class DataRemovalRequest implements Parcelable {
    public static final int FLAG_IS_PREFIX = 0x1;

    private final String mPackageName;
    private final boolean mForEverything;
    private ArrayList<LocusIdRequest> mLocusIdRequests;

    private DataRemovalRequest(Builder builder) {
        mPackageName = ActivityThread.currentPackageName();
        mForEverything = builder.mForEverything;
        if (builder.mLocusIds != null) {
            int size = builder.mLocusIds.size();
            mLocusIdRequests = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                mLocusIdRequests.add(new LocusIdRequest(builder.mLocusIds.get(i), builder.mFlags.get(i)));
            }
        }
    }

    private DataRemovalRequest(Parcel parcel) {
        mPackageName = parcel.readString();
        mForEverything = parcel.readBoolean();
        if (!mForEverything) {
            int size = parcel.readInt();
            mLocusIdRequests = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                LocusId id = parcel.readParcelable(null);
                mLocusIdRequests.add(new LocusIdRequest(id, parcel.readInt()));
            }
        }
    }

    public String getPackageName() { return mPackageName; }

    public boolean isForEverything() { return mForEverything; }

    public List<LocusIdRequest> getLocusIdRequests() {
        return mLocusIdRequests == null ? null : Collections.unmodifiableList(mLocusIdRequests);
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mPackageName);
        parcel.writeBoolean(mForEverything);
        if (!mForEverything) {
            int size = mLocusIdRequests.size();
            parcel.writeInt(size);
            for (int i = 0; i < size; i++) {
                LocusIdRequest request = mLocusIdRequests.get(i);
                parcel.writeParcelable(request.getLocusId(), flags);
                parcel.writeInt(request.getFlags());
            }
        }
    }

    public static final Parcelable.Creator<DataRemovalRequest> CREATOR =
            new Parcelable.Creator<DataRemovalRequest>() {
        @Override
        public DataRemovalRequest createFromParcel(Parcel parcel) { return new DataRemovalRequest(parcel); }

        @Override
        public DataRemovalRequest[] newArray(int size) { return new DataRemovalRequest[size]; }
    };

    /** Builder for {@link DataRemovalRequest}. */
    public static final class Builder {
        private boolean mDestroyed;
        private boolean mForEverything;
        private ArrayList<LocusId> mLocusIds;
        private ArrayList<Integer> mFlags;

        public Builder() {}

        public Builder forEverything() {
            throwIfDestroyed();
            if (mLocusIds != null) throw new IllegalStateException("Already added LocusIds");
            mForEverything = true;
            return this;
        }

        public Builder addLocusId(LocusId locusId, int flags) {
            throwIfDestroyed();
            if (mForEverything) throw new IllegalStateException("Already is for everything");
            if (locusId == null) throw new NullPointerException("locusId");
            if (mLocusIds == null) {
                mLocusIds = new ArrayList<>();
                mFlags = new ArrayList<>();
            }
            mLocusIds.add(locusId);
            mFlags.add(flags);
            return this;
        }

        public DataRemovalRequest build() {
            throwIfDestroyed();
            if (!mForEverything && mLocusIds == null) {
                throw new IllegalStateException("must call either #forEverything() or add one #addLocusId()");
            }
            mDestroyed = true;
            return new DataRemovalRequest(this);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already destroyed!");
        }
    }

    /** A request to remove a {@link LocusId}. */
    public final class LocusIdRequest {
        private final LocusId mLocusId;
        private final int mFlags;

        private LocusIdRequest(LocusId locusId, int flags) {
            mLocusId = locusId;
            mFlags = flags;
        }

        public LocusId getLocusId() { return mLocusId; }

        public int getFlags() { return mFlags; }
    }
}
