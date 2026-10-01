package android.view;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Parcel;
import android.os.Parcelable;

/** Drag and drop event. switchapk never starts a system drag, so these are only created by apps. */
public class DragEvent implements Parcelable {
    public static final int ACTION_DRAG_ENDED = 4;
    public static final int ACTION_DRAG_ENTERED = 5;
    public static final int ACTION_DRAG_EXITED = 6;
    public static final int ACTION_DRAG_LOCATION = 2;
    public static final int ACTION_DRAG_STARTED = 1;
    public static final int ACTION_DROP = 3;

    int mAction;
    float mX, mY;
    ClipDescription mClipDescription;
    ClipData mClipData;
    Object mLocalState;
    boolean mDragResult;

    private DragEvent() {}

    /** framework-internal (hidden in AOSP). */
    public static DragEvent obtain(int action, float x, float y, Object localState, ClipDescription description,
            ClipData data, boolean result) {
        DragEvent ev = new DragEvent();
        ev.mAction = action;
        ev.mX = x;
        ev.mY = y;
        ev.mLocalState = localState;
        ev.mClipDescription = description;
        ev.mClipData = data;
        ev.mDragResult = result;
        return ev;
    }

    public int getAction() { return mAction; }
    public float getX() { return mX; }
    public float getY() { return mY; }
    public ClipData getClipData() { return mClipData; }
    public ClipDescription getClipDescription() { return mClipDescription; }
    public Object getLocalState() { return mLocalState; }
    public boolean getResult() { return mDragResult; }

    @Override
    public String toString() { return "DragEvent{action=" + mAction + " @ (" + mX + ", " + mY + ")}"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAction);
        dest.writeFloat(mX);
        dest.writeFloat(mY);
    }

    public static final Parcelable.Creator<DragEvent> CREATOR = new Parcelable.Creator<DragEvent>() {
        public DragEvent createFromParcel(Parcel in) {
            return obtain(in.readInt(), in.readFloat(), in.readFloat(), null, null, null, false);
        }

        public DragEvent[] newArray(int size) { return new DragEvent[size]; }
    };
}
