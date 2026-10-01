package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;

/** An auto correction applied by the input method. */
public final class CorrectionInfo implements Parcelable {
    private final int mOffset;
    private final CharSequence mOldText;
    private final CharSequence mNewText;

    public CorrectionInfo(int offset, CharSequence oldText, CharSequence newText) {
        mOffset = offset;
        mOldText = oldText;
        mNewText = newText;
    }

    public int getOffset() { return mOffset; }

    public CharSequence getOldText() { return mOldText; }

    public CharSequence getNewText() { return mNewText; }

    @Override
    public String toString() { return "CorrectionInfo{#" + mOffset + " \"" + mOldText + "\" -> \"" + mNewText + "\"}"; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOffset);
        dest.writeCharSequence(mOldText);
        dest.writeCharSequence(mNewText);
    }

    public static final Parcelable.Creator<CorrectionInfo> CREATOR = new Parcelable.Creator<CorrectionInfo>() {
        public CorrectionInfo createFromParcel(Parcel source) {
            return new CorrectionInfo(source.readInt(), source.readCharSequence(), source.readCharSequence());
        }

        public CorrectionInfo[] newArray(int size) { return new CorrectionInfo[size]; }
    };

    public int describeContents() { return 0; }
}
