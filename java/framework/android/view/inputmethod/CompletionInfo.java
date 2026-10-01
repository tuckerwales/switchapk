package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

/** A completion offered by an editor to the input method. */
public final class CompletionInfo implements Parcelable {
    private final long mId;
    private final int mPosition;
    private final CharSequence mText;
    private final CharSequence mLabel;

    public CompletionInfo(long id, int index, CharSequence text) { this(id, index, text, null); }

    public CompletionInfo(long id, int index, CharSequence text, CharSequence label) {
        mId = id;
        mPosition = index;
        mText = text;
        mLabel = label;
    }

    public long getId() { return mId; }

    public int getPosition() { return mPosition; }

    public CharSequence getText() { return mText; }

    public CharSequence getLabel() { return mLabel; }

    @Override
    public String toString() { return "CompletionInfo{#" + mPosition + " \"" + mText + "\" id=" + mId + " label=" + mLabel + "}"; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeInt(mPosition);
        dest.writeCharSequence(mText);
        dest.writeCharSequence(mLabel);
    }

    public static final Parcelable.Creator<CompletionInfo> CREATOR = new Parcelable.Creator<CompletionInfo>() {
        public CompletionInfo createFromParcel(Parcel source) {
            return new CompletionInfo(source.readLong(), source.readInt(), source.readCharSequence(), source.readCharSequence());
        }

        public CompletionInfo[] newArray(int size) { return new CompletionInfo[size]; }
    };

    public int describeContents() { return 0; }
}
