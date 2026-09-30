package android.os;

public final class Messenger implements Parcelable {
    private final Handler mTarget;

    public Messenger(Handler target) { mTarget = target; }
    public Messenger(IBinder target) { mTarget = null; }

    public void send(Message message) throws RemoteException {
        if (mTarget == null) throw new DeadObjectException();
        mTarget.sendMessage(message);
    }

    public IBinder getBinder() { return null; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<Messenger> CREATOR = new Parcelable.Creator<Messenger>() {
        public Messenger createFromParcel(Parcel in) { return (Messenger) in.readValue(null); }
        public Messenger[] newArray(int size) { return new Messenger[size]; }
    };
}
