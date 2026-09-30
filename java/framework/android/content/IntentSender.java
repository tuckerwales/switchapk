package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public class IntentSender implements Parcelable {
    public static class SendIntentException extends android.util.AndroidException {
        public SendIntentException() {}
        public SendIntentException(String name) { super(name); }
        public SendIntentException(Exception cause) { super(cause); }
    }

    public interface OnFinished {
        void onSendFinished(IntentSender IntentSender, Intent intent, int resultCode, String resultData, android.os.Bundle resultExtras);
    }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }
    public String getCreatorPackage() { return null; }

    public static final Parcelable.Creator<IntentSender> CREATOR = new Parcelable.Creator<IntentSender>() {
        public IntentSender createFromParcel(Parcel in) { return (IntentSender) in.readValue(null); }
        public IntentSender[] newArray(int size) { return new IntentSender[size]; }
    };
}
