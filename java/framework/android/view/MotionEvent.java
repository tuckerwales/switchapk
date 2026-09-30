package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public class MotionEvent implements Parcelable {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_MASK = 0xff;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int ACTION_POINTER_ID_MASK = 0xff00;
    public static final int ACTION_POINTER_ID_SHIFT = 8;
    public static final int INVALID_POINTER_ID = -1;
    public static final int TOOL_TYPE_UNKNOWN = 0;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_STYLUS = 2;
    public static final int TOOL_TYPE_MOUSE = 3;
    public static final int TOOL_TYPE_ERASER = 4;
    public static final int FLAG_WINDOW_IS_OBSCURED = 1;
    public static final int FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 2;
    public static final int FLAG_CANCELED = 32;

    private long mDownTime;
    private long mEventTime;
    private int mAction;
    private float mX;
    private float mY;
    private int mMetaState;
    private int mPointerId;

    private MotionEvent() {}

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        return obtain(downTime, eventTime, action, x, y, metaState, 0);
    }

    /** framework-internal */
    static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState,
            int pointerId) {
        MotionEvent ev = new MotionEvent();
        ev.mDownTime = downTime;
        ev.mEventTime = eventTime;
        ev.mAction = action;
        ev.mX = x;
        ev.mY = y;
        ev.mMetaState = metaState;
        ev.mPointerId = pointerId;
        return ev;
    }

    public static MotionEvent obtain(MotionEvent other) {
        return obtain(other.mDownTime, other.mEventTime, other.mAction, other.mX, other.mY, other.mMetaState,
                other.mPointerId);
    }

    public void recycle() {}

    public int getAction() { return mAction; }
    public int getActionMasked() { return mAction & ACTION_MASK; }
    public int getActionIndex() { return (mAction & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT; }
    public long getDownTime() { return mDownTime; }
    public long getEventTime() { return mEventTime; }
    public float getX() { return mX; }
    public float getY() { return mY; }
    public float getRawX() { return mX; }
    public float getRawY() { return mY; }
    public float getX(int pointerIndex) { return mX; }
    public float getY(int pointerIndex) { return mY; }
    public int getPointerCount() { return 1; }
    public int getPointerId(int pointerIndex) { return mPointerId; }
    public int getMetaState() { return mMetaState; }
    public int getToolType(int pointerIndex) { return TOOL_TYPE_FINGER; }
    public void setAction(int action) { mAction = action; }
    public void offsetLocation(float dx, float dy) { mX += dx; mY += dy; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mDownTime);
        dest.writeLong(mEventTime);
        dest.writeInt(mAction);
        dest.writeFloat(mX);
        dest.writeFloat(mY);
        dest.writeInt(mMetaState);
        dest.writeInt(mPointerId);
    }

    public static final Creator<MotionEvent> CREATOR = new Creator<MotionEvent>() {
        public MotionEvent createFromParcel(Parcel in) {
            return obtain(in.readLong(), in.readLong(), in.readInt(), in.readFloat(), in.readFloat(), in.readInt(),
                    in.readInt());
        }
        public MotionEvent[] newArray(int size) { return new MotionEvent[size]; }
    };
}
