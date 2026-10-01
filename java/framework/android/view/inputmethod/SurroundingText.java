package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

/** Text around the cursor with the selection inside it. */
public final class SurroundingText implements Parcelable {
    private final CharSequence mText;
    private final int mSelectionStart;
    private final int mSelectionEnd;
    private final int mOffset;

    public SurroundingText(CharSequence text, int selectionStart, int selectionEnd, int offset) {
        mText = text;
        mSelectionStart = selectionStart;
        mSelectionEnd = selectionEnd;
        mOffset = offset;
    }

    public CharSequence getText() { return mText; }

    public int getSelectionStart() { return mSelectionStart; }

    public int getSelectionEnd() { return mSelectionEnd; }

    public int getOffset() { return mOffset; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeCharSequence(mText);
        dest.writeInt(mSelectionStart);
        dest.writeInt(mSelectionEnd);
        dest.writeInt(mOffset);
    }

    public int describeContents() { return 0; }

    public static final Parcelable.Creator<SurroundingText> CREATOR = new Parcelable.Creator<SurroundingText>() {
        public SurroundingText createFromParcel(Parcel in) {
            return new SurroundingText(in.readCharSequence(), in.readInt(), in.readInt(), in.readInt());
        }

        public SurroundingText[] newArray(int size) { return new SurroundingText[size]; }
    };
}
