package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

/** Describes what text an input method wants extracted. */
public class ExtractedTextRequest implements Parcelable {
    public int token;
    public int flags;
    public int hintMaxLines;
    public int hintMaxChars;

    public ExtractedTextRequest() {}

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(token);
        dest.writeInt(this.flags);
        dest.writeInt(hintMaxLines);
        dest.writeInt(hintMaxChars);
    }

    public static final Parcelable.Creator<ExtractedTextRequest> CREATOR = new Parcelable.Creator<ExtractedTextRequest>() {
        public ExtractedTextRequest createFromParcel(Parcel source) {
            ExtractedTextRequest res = new ExtractedTextRequest();
            res.token = source.readInt();
            res.flags = source.readInt();
            res.hintMaxLines = source.readInt();
            res.hintMaxChars = source.readInt();
            return res;
        }

        public ExtractedTextRequest[] newArray(int size) { return new ExtractedTextRequest[size]; }
    };

    public int describeContents() { return 0; }
}
