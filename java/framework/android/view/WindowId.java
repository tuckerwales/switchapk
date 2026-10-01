package android.view;

import android.os.Parcel;
import android.os.Parcelable;

/** Identifies a window; switchapk does not hand these out (View.getWindowId returns null). */
public class WindowId implements Parcelable {
    WindowId() {}

    public boolean isFocused() { return true; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {}

    public static final Parcelable.Creator<WindowId> CREATOR = new Parcelable.Creator<WindowId>() {
        public WindowId createFromParcel(Parcel in) { return new WindowId(); }
        public WindowId[] newArray(int size) { return new WindowId[size]; }
    };
}
