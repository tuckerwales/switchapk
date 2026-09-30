package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public class KeyEvent implements Parcelable {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MULTIPLE = 2;

    public static final int FLAG_WOKE_HERE = 1;
    public static final int FLAG_SOFT_KEYBOARD = 2;
    public static final int FLAG_KEEP_TOUCH_MODE = 4;
    public static final int FLAG_FROM_SYSTEM = 8;
    public static final int FLAG_EDITOR_ACTION = 16;
    public static final int FLAG_CANCELED = 32;
    public static final int FLAG_VIRTUAL_HARD_KEY = 64;
    public static final int FLAG_LONG_PRESS = 128;
    public static final int FLAG_CANCELED_LONG_PRESS = 256;
    public static final int FLAG_TRACKING = 512;
    public static final int FLAG_FALLBACK = 1024;

    public static final int KEYCODE_HOME = 3;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_TAB = 61;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_BUTTON_C = 98;
    public static final int KEYCODE_BUTTON_X = 99;
    public static final int KEYCODE_BUTTON_Y = 100;
    public static final int KEYCODE_BUTTON_Z = 101;
    public static final int KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_R1 = 103;
    public static final int KEYCODE_BUTTON_L2 = 104;
    public static final int KEYCODE_BUTTON_R2 = 105;
    public static final int KEYCODE_BUTTON_THUMBL = 106;
    public static final int KEYCODE_BUTTON_THUMBR = 107;
    public static final int KEYCODE_BUTTON_START = 108;
    public static final int KEYCODE_BUTTON_SELECT = 109;
    public static final int KEYCODE_BUTTON_MODE = 110;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_UNKNOWN = 0;

    private long mDownTime;
    private long mEventTime;
    private int mAction;
    private int mKeyCode;
    private int mRepeat;
    private int mMetaState;
    private int mDeviceId;
    private int mScanCode;
    private int mFlags;
    private int mSource;

    public KeyEvent(int action, int code) { this(0, 0, action, code, 0); }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat) {
        this(downTime, eventTime, action, code, repeat, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState) {
        this(downTime, eventTime, action, code, repeat, metaState, 0, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId,
            int scancode) {
        this(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId,
            int scancode, int flags) {
        this(downTime, eventTime, action, code, repeat, metaState, deviceId, scancode, flags, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId,
            int scancode, int flags, int source) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeat = repeat;
        mMetaState = metaState;
        mDeviceId = deviceId;
        mScanCode = scancode;
        mFlags = flags;
        mSource = source;
    }

    public KeyEvent(long time, String characters, int deviceId, int displayId) {
        this(time, time, ACTION_MULTIPLE, KEYCODE_UNKNOWN, 0);
    }

    public KeyEvent(KeyEvent origEvent) {
        this(origEvent.mDownTime, origEvent.mEventTime, origEvent.mAction, origEvent.mKeyCode, origEvent.mRepeat,
                origEvent.mMetaState, origEvent.mDeviceId, origEvent.mScanCode, origEvent.mFlags, origEvent.mSource);
    }

    public KeyEvent(KeyEvent origEvent, long eventTime, int newRepeat) {
        this(origEvent);
        mEventTime = eventTime;
        mRepeat = newRepeat;
    }

    private KeyEvent(Parcel in) {
        mDownTime = in.readLong();
        mEventTime = in.readLong();
        mAction = in.readInt();
        mKeyCode = in.readInt();
        mRepeat = in.readInt();
        mMetaState = in.readInt();
        mDeviceId = in.readInt();
        mScanCode = in.readInt();
        mFlags = in.readInt();
        mSource = in.readInt();
    }

    public final int getAction() { return mAction; }
    public final int getKeyCode() { return mKeyCode; }
    public final int getRepeatCount() { return mRepeat; }
    public final int getMetaState() { return mMetaState; }
    public final long getDownTime() { return mDownTime; }
    public final long getEventTime() { return mEventTime; }
    public int getDeviceId() { return mDeviceId; }
    public int getSource() { return mSource; }
    public int getFlags() { return mFlags; }
    public int getScanCode() { return mScanCode; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mDownTime);
        dest.writeLong(mEventTime);
        dest.writeInt(mAction);
        dest.writeInt(mKeyCode);
        dest.writeInt(mRepeat);
        dest.writeInt(mMetaState);
        dest.writeInt(mDeviceId);
        dest.writeInt(mScanCode);
        dest.writeInt(mFlags);
        dest.writeInt(mSource);
    }

    public static final Creator<KeyEvent> CREATOR = new Creator<KeyEvent>() {
        public KeyEvent createFromParcel(Parcel source) { return new KeyEvent(source); }
        public KeyEvent[] newArray(int size) { return new KeyEvent[size]; }
    };

}
