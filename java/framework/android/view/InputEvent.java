package android.view;

import android.os.Parcel;
import android.os.Parcelable;

/** Common base class for input events. */
public abstract class InputEvent implements Parcelable {
    InputEvent() {}

    public abstract int getDeviceId();

    public final InputDevice getDevice() { return InputDevice.getDevice(getDeviceId()); }

    public abstract int getSource();

    /** framework-internal (hidden in AOSP). */
    public abstract void setSource(int source);

    public boolean isFromSource(int source) { return (getSource() & source) == source; }

    public abstract long getEventTime();

    /** framework-internal (hidden in AOSP). Event time in nanoseconds. */
    public long getEventTimeNanos() { return getEventTime() * 1000000L; }

    /** framework-internal (hidden in AOSP). */
    public void recycle() {}

    public int describeContents() { return 0; }

    public static final Parcelable.Creator<InputEvent> CREATOR = new Parcelable.Creator<InputEvent>() {
        public InputEvent createFromParcel(Parcel in) {
            int token = in.readInt();
            if (token == 2) return KeyEvent.CREATOR.createFromParcel(in);
            return MotionEvent.CREATOR.createFromParcel(in);
        }

        public InputEvent[] newArray(int size) { return new InputEvent[size]; }
    };
}
