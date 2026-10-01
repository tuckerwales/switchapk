package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

/** Snapshot of editor text for an input method. */
public class ExtractedText implements Parcelable {
    public static final int FLAG_SINGLE_LINE = 0x0001;
    public static final int FLAG_SELECTING = 0x0002;

    public CharSequence text;
    public int startOffset;
    public int partialStartOffset;
    public int partialEndOffset;
    public int selectionStart;
    public int selectionEnd;
    public int flags;
    public CharSequence hint;

    public ExtractedText() {}

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeCharSequence(text);
        dest.writeInt(startOffset);
        dest.writeInt(partialStartOffset);
        dest.writeInt(partialEndOffset);
        dest.writeInt(selectionStart);
        dest.writeInt(selectionEnd);
        dest.writeInt(this.flags);
        dest.writeCharSequence(hint);
    }

    public static final Parcelable.Creator<ExtractedText> CREATOR = new Parcelable.Creator<ExtractedText>() {
        public ExtractedText createFromParcel(Parcel source) {
            ExtractedText res = new ExtractedText();
            res.text = source.readCharSequence();
            res.startOffset = source.readInt();
            res.partialStartOffset = source.readInt();
            res.partialEndOffset = source.readInt();
            res.selectionStart = source.readInt();
            res.selectionEnd = source.readInt();
            res.flags = source.readInt();
            res.hint = source.readCharSequence();
            return res;
        }

        public ExtractedText[] newArray(int size) { return new ExtractedText[size]; }
    };

    public int describeContents() { return 0; }
}
