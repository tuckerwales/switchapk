package android.os;

/** Attribution of work to other apps. Everything runs as this app, so a WorkSource only carries uids along. */
public class WorkSource implements Parcelable {
    private final java.util.TreeSet<Integer> mUids = new java.util.TreeSet<Integer>();

    public WorkSource() {}

    public WorkSource(WorkSource orig) {
        if (orig != null) mUids.addAll(orig.mUids);
    }

    public void clear() { mUids.clear(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorkSource && ((WorkSource) o).mUids.equals(mUids);
    }

    @Override
    public int hashCode() { return mUids.hashCode(); }

    public boolean diff(WorkSource other) { return other == null ? !mUids.isEmpty() : !mUids.equals(other.mUids); }

    public void set(WorkSource other) {
        mUids.clear();
        if (other != null) mUids.addAll(other.mUids);
    }

    public boolean add(WorkSource other) { return other != null && mUids.addAll(other.mUids); }

    public boolean remove(WorkSource other) { return other != null && mUids.removeAll(other.mUids); }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUids.size());
        for (int uid : mUids) dest.writeInt(uid);
    }

    @Override
    public String toString() { return "WorkSource{" + mUids + "}"; }

    public static final Parcelable.Creator<WorkSource> CREATOR = new Parcelable.Creator<WorkSource>() {
        public WorkSource createFromParcel(Parcel in) {
            WorkSource ws = new WorkSource();
            int n = in.readInt();
            for (int i = 0; i < n; i++) ws.mUids.add(in.readInt());
            return ws;
        }

        public WorkSource[] newArray(int size) { return new WorkSource[size]; }
    };
}
