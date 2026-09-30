package android.os;

public class ResultReceiver implements Parcelable {
    final Handler mHandler;

    public ResultReceiver(Handler handler) { mHandler = handler; }

    public void send(final int resultCode, final Bundle resultData) {
        if (mHandler != null) {
            mHandler.post(new Runnable() {
                public void run() { onReceiveResult(resultCode, resultData); }
            });
        } else {
            onReceiveResult(resultCode, resultData);
        }
    }

    protected void onReceiveResult(int resultCode, Bundle resultData) {}
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<ResultReceiver> CREATOR = new Parcelable.Creator<ResultReceiver>() {
        public ResultReceiver createFromParcel(Parcel in) { return (ResultReceiver) in.readValue(null); }
        public ResultReceiver[] newArray(int size) { return new ResultReceiver[size]; }
    };
}
