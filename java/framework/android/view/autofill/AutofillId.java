package android.view.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

/** Identifies a view (and optionally a virtual child) for autofill. */
public final class AutofillId implements Parcelable {
    private static final int NO_VIRTUAL = -1;

    private final int mViewId;
    private final int mVirtualId;

    AutofillId(int viewId, int virtualId) {
        mViewId = viewId;
        mVirtualId = virtualId;
    }

    /** framework-internal (hidden in AOSP). */
    public AutofillId(int id) { this(id, NO_VIRTUAL); }

    /** framework-internal (hidden in AOSP). */
    public AutofillId(AutofillId hostId, int virtualChildId) { this(hostId.mViewId, virtualChildId); }

    public static AutofillId create(View host, int virtualId) {
        return new AutofillId(host.getAutofillId(), virtualId);
    }

    /** framework-internal (hidden in AOSP). */
    public int getViewId() { return mViewId; }

    /** framework-internal (hidden in AOSP). */
    public boolean isVirtualInt() { return mVirtualId != NO_VIRTUAL; }

    /** framework-internal (hidden in AOSP). */
    public int getVirtualChildIntId() { return mVirtualId; }

    @Override
    public int hashCode() { return 31 * mViewId + mVirtualId; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AutofillId)) return false;
        AutofillId other = (AutofillId) obj;
        return mViewId == other.mViewId && mVirtualId == other.mVirtualId;
    }

    @Override
    public String toString() {
        return mVirtualId == NO_VIRTUAL ? Integer.toString(mViewId) : mViewId + ":" + mVirtualId;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mViewId);
        out.writeInt(mVirtualId);
    }

    public static final Parcelable.Creator<AutofillId> CREATOR = new Parcelable.Creator<AutofillId>() {
        public AutofillId createFromParcel(Parcel in) {
            int viewId = in.readInt();
            return new AutofillId(viewId, in.readInt());
        }
        public AutofillId[] newArray(int size) { return new AutofillId[size]; }
    };
}
