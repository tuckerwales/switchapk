package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Vibrator;
import java.util.ArrayList;
import java.util.List;

/**
 * Input devices known to switchapk: the virtual keyboard (id -1), the touch
 * screen (id 1) and the game controller (Joy-Cons or Pro Controller, id 2).
 */
public final class InputDevice implements Parcelable {
    public static final int KEYBOARD_TYPE_ALPHABETIC = 2;
    public static final int KEYBOARD_TYPE_NONE = 0;
    public static final int KEYBOARD_TYPE_NON_ALPHABETIC = 1;
    public static final int MOTION_RANGE_ORIENTATION = 8;
    public static final int MOTION_RANGE_PRESSURE = 2;
    public static final int MOTION_RANGE_SIZE = 3;
    public static final int MOTION_RANGE_TOOL_MAJOR = 6;
    public static final int MOTION_RANGE_TOOL_MINOR = 7;
    public static final int MOTION_RANGE_TOUCH_MAJOR = 4;
    public static final int MOTION_RANGE_TOUCH_MINOR = 5;
    public static final int MOTION_RANGE_X = 0;
    public static final int MOTION_RANGE_Y = 1;
    public static final int SOURCE_ANY = -256;
    public static final int SOURCE_BLUETOOTH_STYLUS = 49154;
    public static final int SOURCE_CLASS_BUTTON = 1;
    public static final int SOURCE_CLASS_JOYSTICK = 16;
    public static final int SOURCE_CLASS_MASK = 255;
    public static final int SOURCE_CLASS_NONE = 0;
    public static final int SOURCE_CLASS_POINTER = 2;
    public static final int SOURCE_CLASS_POSITION = 8;
    public static final int SOURCE_CLASS_TRACKBALL = 4;
    public static final int SOURCE_DPAD = 513;
    public static final int SOURCE_GAMEPAD = 1025;
    public static final int SOURCE_HDMI = 33554433;
    public static final int SOURCE_JOYSTICK = 16777232;
    public static final int SOURCE_KEYBOARD = 257;
    public static final int SOURCE_MOUSE = 8194;
    public static final int SOURCE_MOUSE_RELATIVE = 131076;
    public static final int SOURCE_ROTARY_ENCODER = 4194304;
    public static final int SOURCE_SENSOR = 67108864;
    public static final int SOURCE_STYLUS = 16386;
    public static final int SOURCE_TOUCHPAD = 1048584;
    public static final int SOURCE_TOUCHSCREEN = 4098;
    public static final int SOURCE_TOUCH_NAVIGATION = 2097152;
    public static final int SOURCE_TRACKBALL = 65540;
    public static final int SOURCE_UNKNOWN = 0;

    /** framework-internal. Device ids used by PlatformInput. */
    public static final int ID_TOUCHSCREEN = 1;
    /** framework-internal. */
    public static final int ID_GAMEPAD = 2;

    private final int mId;
    private final String mName;
    private final int mSources;
    private final int mKeyboardType;
    private final ArrayList<MotionRange> mMotionRanges = new ArrayList<MotionRange>();

    public static final class MotionRange {
        private final int mAxis;
        private final int mSource;
        private final float mMin;
        private final float mMax;
        private final float mFlat;
        private final float mFuzz;
        private final float mResolution;

        MotionRange(int axis, int source, float min, float max, float flat, float fuzz, float resolution) {
            mAxis = axis;
            mSource = source;
            mMin = min;
            mMax = max;
            mFlat = flat;
            mFuzz = fuzz;
            mResolution = resolution;
        }

        public int getAxis() { return mAxis; }
        public int getSource() { return mSource; }
        public boolean isFromSource(int source) { return (getSource() & source) == source; }
        public float getMin() { return mMin; }
        public float getMax() { return mMax; }
        public float getRange() { return mMax - mMin; }
        public float getFlat() { return mFlat; }
        public float getFuzz() { return mFuzz; }
        public float getResolution() { return mResolution; }
    }

    private InputDevice(int id, String name, int sources, int keyboardType) {
        mId = id;
        mName = name;
        mSources = sources;
        mKeyboardType = keyboardType;
    }

    private static InputDevice sKeyboard;
    private static InputDevice sTouch;
    private static InputDevice sGamepad;

    private static synchronized void init() {
        if (sKeyboard != null) return;
        sKeyboard = new InputDevice(KeyCharacterMap.VIRTUAL_KEYBOARD, "Virtual", SOURCE_KEYBOARD,
                KEYBOARD_TYPE_ALPHABETIC);
        sTouch = new InputDevice(ID_TOUCHSCREEN, "touchscreen", SOURCE_TOUCHSCREEN, KEYBOARD_TYPE_NONE);
        Display d = Display.defaultDisplay();
        sTouch.addRange(MotionEvent.AXIS_X, SOURCE_TOUCHSCREEN, 0, Math.max(1, d.getWidth() - 1), 0, 0, 0);
        sTouch.addRange(MotionEvent.AXIS_Y, SOURCE_TOUCHSCREEN, 0, Math.max(1, d.getHeight() - 1), 0, 0, 0);
        sTouch.addRange(MotionEvent.AXIS_PRESSURE, SOURCE_TOUCHSCREEN, 0, 1, 0, 0, 0);
        sTouch.addRange(MotionEvent.AXIS_SIZE, SOURCE_TOUCHSCREEN, 0, 1, 0, 0, 0);
        sGamepad = new InputDevice(ID_GAMEPAD, "Nintendo Switch Controller",
                SOURCE_GAMEPAD | SOURCE_DPAD | SOURCE_JOYSTICK, KEYBOARD_TYPE_NON_ALPHABETIC);
        int[] sticks = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ};
        for (int axis : sticks) sGamepad.addRange(axis, SOURCE_JOYSTICK, -1f, 1f, 0.1f, 0.01f, 0);
        sGamepad.addRange(MotionEvent.AXIS_LTRIGGER, SOURCE_JOYSTICK, 0f, 1f, 0, 0, 0);
        sGamepad.addRange(MotionEvent.AXIS_RTRIGGER, SOURCE_JOYSTICK, 0f, 1f, 0, 0, 0);
        sGamepad.addRange(MotionEvent.AXIS_HAT_X, SOURCE_JOYSTICK, -1f, 1f, 0, 0, 0);
        sGamepad.addRange(MotionEvent.AXIS_HAT_Y, SOURCE_JOYSTICK, -1f, 1f, 0, 0, 0);
    }

    private void addRange(int axis, int source, float min, float max, float flat, float fuzz, float res) {
        mMotionRanges.add(new MotionRange(axis, source, min, max, flat, fuzz, res));
    }

    public static InputDevice getDevice(int id) {
        init();
        if (id == sKeyboard.mId) return sKeyboard;
        if (id == ID_TOUCHSCREEN) return sTouch;
        if (id == ID_GAMEPAD) return sGamepad;
        return null;
    }

    public static int[] getDeviceIds() { return new int[] {KeyCharacterMap.VIRTUAL_KEYBOARD, ID_TOUCHSCREEN, ID_GAMEPAD}; }

    public int getId() { return mId; }
    public int getControllerNumber() { return mId == ID_GAMEPAD ? 1 : 0; }
    public int getVendorId() { return mId == ID_GAMEPAD ? 0x057e : 0; }
    public int getProductId() { return mId == ID_GAMEPAD ? 0x2009 : 0; }
    public String getDescriptor() { return "switchapk-" + mId; }
    public boolean isVirtual() { return mId < 0; }
    public boolean isExternal() { return mId == ID_GAMEPAD; }
    public String getName() { return mName; }
    public int getSources() { return mSources; }
    public boolean supportsSource(int source) { return (mSources & source) == source; }
    public int getKeyboardType() { return mKeyboardType; }
    public KeyCharacterMap getKeyCharacterMap() { return KeyCharacterMap.load(mId); }

    public boolean[] hasKeys(int... keys) {
        boolean[] ret = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            int k = keys[i];
            ret[i] = mId == ID_GAMEPAD ? KeyEvent.isGamepadButton(k) || (k >= KeyEvent.KEYCODE_DPAD_UP
                    && k <= KeyEvent.KEYCODE_DPAD_RIGHT) : mId < 0;
        }
        return ret;
    }

    public int getKeyCodeForKeyLocation(int locationKeyCode) { return locationKeyCode; }

    public MotionRange getMotionRange(int axis) {
        for (int i = 0; i < mMotionRanges.size(); i++) {
            if (mMotionRanges.get(i).mAxis == axis) return mMotionRanges.get(i);
        }
        return null;
    }

    public MotionRange getMotionRange(int axis, int source) {
        for (int i = 0; i < mMotionRanges.size(); i++) {
            MotionRange r = mMotionRanges.get(i);
            if (r.mAxis == axis && r.mSource == source) return r;
        }
        return null;
    }

    public List<MotionRange> getMotionRanges() { return mMotionRanges; }

    public Vibrator getVibrator() { return new Vibrator.SystemVibrator(); }

    public boolean isEnabled() { return true; }

    public boolean hasMicrophone() { return false; }

    public void writeToParcel(Parcel out, int flags) { out.writeInt(mId); }

    public int describeContents() { return 0; }

    @Override
    public String toString() {
        return "Input Device " + mId + ": " + mName + " sources=0x" + Integer.toHexString(mSources);
    }

    public static final Parcelable.Creator<InputDevice> CREATOR = new Parcelable.Creator<InputDevice>() {
        public InputDevice createFromParcel(Parcel in) { return getDevice(in.readInt()); }
        public InputDevice[] newArray(int size) { return new InputDevice[size]; }
    };
}
