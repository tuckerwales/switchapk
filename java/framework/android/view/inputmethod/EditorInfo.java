package android.view.inputmethod;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;

/** Describes an editor to the input method. Minimal holder; text input is TODO(WS2). */
public class EditorInfo implements InputType, Parcelable {
    public static final int IME_ACTION_DONE = 6;
    public static final int IME_ACTION_GO = 2;
    public static final int IME_ACTION_NEXT = 5;
    public static final int IME_ACTION_NONE = 1;
    public static final int IME_ACTION_PREVIOUS = 7;
    public static final int IME_ACTION_SEARCH = 3;
    public static final int IME_ACTION_SEND = 4;
    public static final int IME_ACTION_UNSPECIFIED = 0;
    public static final int IME_FLAG_FORCE_ASCII = -2147483648;
    public static final int IME_FLAG_NAVIGATE_NEXT = 134217728;
    public static final int IME_FLAG_NAVIGATE_PREVIOUS = 67108864;
    public static final int IME_FLAG_NO_ACCESSORY_ACTION = 536870912;
    public static final int IME_FLAG_NO_ENTER_ACTION = 1073741824;
    public static final int IME_FLAG_NO_EXTRACT_UI = 268435456;
    public static final int IME_FLAG_NO_FULLSCREEN = 33554432;
    public static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 16777216;
    public static final int IME_MASK_ACTION = 255;
    public static final int IME_NULL = 0;

    public int inputType = TYPE_NULL;
    public int imeOptions = IME_NULL;
    public String privateImeOptions;
    public CharSequence actionLabel;
    public int actionId;
    public int initialSelStart = -1;
    public int initialSelEnd = -1;
    public int initialCapsMode;
    public CharSequence hintText;
    public CharSequence label;
    public String packageName;
    public int fieldId;
    public String fieldName;
    public Bundle extras;
    public LocaleList hintLocales;
    public String[] contentMimeTypes;

    public EditorInfo() {}

    public final void makeCompatible(int targetSdkVersion) {}

    public void dump(android.util.Printer pw, String prefix) {
        pw.println(prefix + "inputType=0x" + Integer.toHexString(inputType) + " imeOptions=0x"
                + Integer.toHexString(imeOptions));
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(inputType);
        dest.writeInt(imeOptions);
        dest.writeString(privateImeOptions);
        dest.writeInt(actionId);
        dest.writeInt(initialSelStart);
        dest.writeInt(initialSelEnd);
        dest.writeInt(initialCapsMode);
        dest.writeString(packageName);
        dest.writeInt(fieldId);
        dest.writeString(fieldName);
    }

    public static final Parcelable.Creator<EditorInfo> CREATOR = new Parcelable.Creator<EditorInfo>() {
        public EditorInfo createFromParcel(Parcel source) {
            EditorInfo res = new EditorInfo();
            res.inputType = source.readInt();
            res.imeOptions = source.readInt();
            res.privateImeOptions = source.readString();
            res.actionId = source.readInt();
            res.initialSelStart = source.readInt();
            res.initialSelEnd = source.readInt();
            res.initialCapsMode = source.readInt();
            res.packageName = source.readString();
            res.fieldId = source.readInt();
            res.fieldName = source.readString();
            return res;
        }

        public EditorInfo[] newArray(int size) { return new EditorInfo[size]; }
    };
}
