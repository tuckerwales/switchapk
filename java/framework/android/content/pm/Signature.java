package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

public class Signature implements Parcelable {
    private final byte[] mSignature;

    public Signature(byte[] signature) { mSignature = signature.clone(); }

    public Signature(String text) {
        final int N = text.length() / 2;
        mSignature = new byte[N];
        for (int i = 0; i < N; i++) mSignature[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
    }

    public char[] toChars() {
        char[] text = new char[mSignature.length * 2];
        final String hex = "0123456789abcdef";
        for (int i = 0; i < mSignature.length; i++) {
            text[i * 2] = hex.charAt((mSignature[i] >> 4) & 0xf);
            text[i * 2 + 1] = hex.charAt(mSignature[i] & 0xf);
        }
        return text;
    }

    public String toCharsString() { return new String(toChars()); }
    public byte[] toByteArray() { return mSignature.clone(); }
    public boolean equals(Object obj) { return obj instanceof Signature && Arrays.equals(mSignature, ((Signature) obj).mSignature); }
    public int hashCode() { return Arrays.hashCode(mSignature); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int parcelableFlags) { dest.writeByteArray(mSignature); }
    public static final Parcelable.Creator<Signature> CREATOR = new Parcelable.Creator<Signature>() {
        public Signature createFromParcel(Parcel source) { return new Signature(source.createByteArray()); }
        public Signature[] newArray(int size) { return new Signature[size]; }
    };
}
