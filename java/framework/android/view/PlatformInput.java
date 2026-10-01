package android.view;

import android.view.inputmethod.InputMethodManager;

/**
 * framework-internal. Drains one platform wake into touch, key, joystick, text and lifecycle events.
 * Kinds match platform.h: 0 none, 1 touch, 2 key, 3 joystick, 4 quit, 5 focus, 6 resize, 7 sensor, 8 text.
 * Touch events arrive per pointer and are merged here into multi-pointer MotionEvents.
 */
public final class PlatformInput {
    public static final int NONE = 0;
    public static final int TOUCH = 1;
    public static final int KEY = 2;
    public static final int JOYSTICK = 3;
    public static final int QUIT = 4;
    public static final int FOCUS = 5;
    public static final int RESIZE = 6;
    public static final int SENSOR = 7;
    public static final int TEXT = 8;

    public interface Sink {
        void onQuit();
        void onFocus(boolean gained);
        void onResize(int width, int height, int dpi);
    }

    /** framework-internal. Receives sensor samples (WS15). */
    public interface SensorSink {
        void onSensor(int type, float x, float y, float z, long timeNs);
    }

    private static SensorSink sSensorSink;

    public static void setSensorSink(SensorSink sink) { sSensorSink = sink; }

    private static final int MAX_POINTERS = 16;
    private static final int[] sIds = new int[MAX_POINTERS];
    private static final float[] sX = new float[MAX_POINTERS];
    private static final float[] sY = new float[MAX_POINTERS];
    private static int sCount;
    private static long sDownTime;

    private PlatformInput() {}

    public static void drain(Sink sink) {
        int[] iv = new int[4];
        float[] fv = new float[8];
        long[] tv = new long[1];
        for (;;) {
            int kind = nNextEvent(iv, fv, tv);
            if (kind == NONE) return;
            long ms = tv[0] / 1000000L;
            switch (kind) {
                case TOUCH:
                    onTouch(iv[0], iv[1], fv[0], fv[1], ms);
                    break;
                case KEY:
                    onKey(iv[0], iv[1], iv[2], iv[3], ms);
                    break;
                case JOYSTICK:
                    onJoystick(fv, ms);
                    break;
                case QUIT:
                    if (sink != null) sink.onQuit();
                    return;
                case FOCUS:
                    WindowManagerGlobal.getInstance().setPlatformFocus(iv[0] != 0);
                    if (sink != null) sink.onFocus(iv[0] != 0);
                    break;
                case RESIZE:
                    if (sink != null) sink.onResize(iv[0], iv[1], iv[2]);
                    break;
                case SENSOR:
                    if (sSensorSink != null) sSensorSink.onSensor(iv[0], fv[0], fv[1], fv[2], tv[0]);
                    break;
                case TEXT:
                    InputMethodManager.deliverTextResult(iv[0], nTakeText());
                    break;
                default:
                    break;
            }
        }
    }

    private static int indexOf(int id) {
        for (int i = 0; i < sCount; i++) if (sIds[i] == id) return i;
        return -1;
    }

    private static void onTouch(int action, int id, float x, float y, long time) {
        int index = indexOf(id);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (index < 0) {
                    if (sCount == MAX_POINTERS) return;
                    index = sCount++;
                    sIds[index] = id;
                }
                sX[index] = x;
                sY[index] = y;
                if (sCount == 1) {
                    sDownTime = time;
                    send(MotionEvent.ACTION_DOWN, time);
                } else {
                    send(MotionEvent.ACTION_POINTER_DOWN | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT), time);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (index < 0) return;
                sX[index] = x;
                sY[index] = y;
                send(MotionEvent.ACTION_MOVE, time);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (index < 0) return;
                sX[index] = x;
                sY[index] = y;
                if (sCount == 1) send(MotionEvent.ACTION_UP, time);
                else send(MotionEvent.ACTION_POINTER_UP | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT), time);
                for (int i = index; i < sCount - 1; i++) {
                    sIds[i] = sIds[i + 1];
                    sX[i] = sX[i + 1];
                    sY[i] = sY[i + 1];
                }
                sCount--;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (sCount > 0) send(MotionEvent.ACTION_CANCEL, time);
                sCount = 0;
                break;
            default:
                break;
        }
    }

    private static void send(int action, long time) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[sCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[sCount];
        for (int i = 0; i < sCount; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = sIds[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = sX[i];
            coords[i].y = sY[i];
            coords[i].pressure = 1f;
            coords[i].size = 0.1f;
        }
        MotionEvent ev = MotionEvent.obtain(sDownTime, time, action, sCount, props, coords, 0, 0, 1f, 1f,
                InputDevice.ID_TOUCHSCREEN, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        WindowManagerGlobal.getInstance().dispatchTouch(ev);
    }

    private static final long[] sKeyDownTime = new long[KeyEvent.getMaxKeyCode() + 1];

    private static void onKey(int up, int keyCode, int meta, int repeat, long time) {
        int action = up != 0 ? KeyEvent.ACTION_UP : KeyEvent.ACTION_DOWN;
        long downTime = time;
        if (keyCode >= 0 && keyCode < sKeyDownTime.length) {
            if (action == KeyEvent.ACTION_DOWN && repeat == 0) sKeyDownTime[keyCode] = time;
            downTime = sKeyDownTime[keyCode] != 0 ? sKeyDownTime[keyCode] : time;
        }
        int source;
        int device;
        if (KeyEvent.isGamepadButton(keyCode)) {
            source = InputDevice.SOURCE_GAMEPAD;
            device = InputDevice.ID_GAMEPAD;
        } else if (keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_CENTER) {
            source = InputDevice.SOURCE_DPAD;
            device = InputDevice.ID_GAMEPAD;
        } else {
            source = InputDevice.SOURCE_KEYBOARD;
            device = KeyCharacterMap.VIRTUAL_KEYBOARD;
        }
        KeyEvent ev = new KeyEvent(downTime, time, action, keyCode, repeat, meta, device, 0, 0, source);
        WindowManagerGlobal.getInstance().dispatchKey(ev);
    }

    private static final int[] JOYSTICK_AXES = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
        MotionEvent.AXIS_RZ, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_HAT_X,
        MotionEvent.AXIS_HAT_Y};

    private static void onJoystick(float[] fv, long time) {
        MotionEvent.PointerProperties[] props = {new MotionEvent.PointerProperties()};
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
        for (int i = 0; i < JOYSTICK_AXES.length; i++) coords[0].setAxisValue(JOYSTICK_AXES[i], fv[i]);
        // AXIS_BRAKE/GAS mirror the triggers, as Android's gamepad mapping does
        coords[0].setAxisValue(MotionEvent.AXIS_BRAKE, fv[4]);
        coords[0].setAxisValue(MotionEvent.AXIS_GAS, fv[5]);
        MotionEvent ev = MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, 1, props, coords, 0, 0, 1f, 1f,
                InputDevice.ID_GAMEPAD, 0, InputDevice.SOURCE_JOYSTICK, 0);
        WindowManagerGlobal.getInstance().dispatchGenericMotion(ev);
    }

    private static native int nNextEvent(int[] ints, float[] floats, long[] timeNs);
    private static native String nTakeText();
}
