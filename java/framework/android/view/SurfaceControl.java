package android.view;

import android.os.Parcel;
import android.os.Parcelable;

/** Handle to a compositor surface. switchapk composites in-process; this is an opaque token. */
public final class SurfaceControl implements Parcelable {
    SurfaceControl() {}

    public boolean isValid() { return true; }

    public void release() {}

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {}

    public static final Parcelable.Creator<SurfaceControl> CREATOR = new Parcelable.Creator<SurfaceControl>() {
        public SurfaceControl createFromParcel(Parcel in) { return new SurfaceControl(); }
        public SurfaceControl[] newArray(int size) { return new SurfaceControl[size]; }
    };
}
