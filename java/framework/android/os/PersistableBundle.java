package android.os;

public final class PersistableBundle extends BaseBundle implements Cloneable, Parcelable {
    public static final PersistableBundle EMPTY = new PersistableBundle();

    public PersistableBundle() { super(); }
    public PersistableBundle(int capacity) { super(capacity); }
    public PersistableBundle(PersistableBundle b) { super(b); }

    public void putPersistableBundle(String key, PersistableBundle value) { mMap.put(key, value); }
    public PersistableBundle getPersistableBundle(String key) { Object o = mMap.get(key); return o instanceof PersistableBundle ? (PersistableBundle) o : null; }

    @Override
    public Object clone() { return new PersistableBundle(this); }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writePersistableBundle(this); }

    public static final Parcelable.Creator<PersistableBundle> CREATOR = new Parcelable.Creator<PersistableBundle>() {
        public PersistableBundle createFromParcel(Parcel in) { return in.readPersistableBundle(); }
        public PersistableBundle[] newArray(int size) { return new PersistableBundle[size]; }
    };

    @Override
    public String toString() { return "PersistableBundle[" + mMap.toString() + "]"; }
}
