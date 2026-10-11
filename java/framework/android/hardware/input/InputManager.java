package android.hardware.input;

import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import java.util.ArrayList;

/**
 * The input service (INPUT_SERVICE). The devices are the fixed set InputDevice
 * reports (virtual keyboard, touchscreen, the gamepad), so device listeners
 * register but are never called. getInputDeviceViewBehavior, verifyInputEvent and
 * getHostUsiVersion are absent (their types are not in this tree) and auto-stub.
 */
public final class InputManager {
    public static final String ACTION_QUERY_KEYBOARD_LAYOUTS = "android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS";
    public static final String META_DATA_KEYBOARD_LAYOUTS = "android.hardware.input.metadata.KEYBOARD_LAYOUTS";

    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);
        void onInputDeviceRemoved(int deviceId);
        void onInputDeviceChanged(int deviceId);
    }

    private static final InputManager sInstance = new InputManager();
    private final ArrayList<InputDeviceListener> mListeners = new ArrayList<InputDeviceListener>();

    InputManager() {}

    /** framework-internal (AOSP getInstance, hidden). */
    public static InputManager getInstance() { return sInstance; }

    public InputDevice getInputDevice(int id) { return InputDevice.getDevice(id); }

    public int[] getInputDeviceIds() { return InputDevice.getDeviceIds(); }

    public void registerInputDeviceListener(InputDeviceListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) mListeners.add(listener);
        }
    }

    public void unregisterInputDeviceListener(InputDeviceListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    public float getMaximumObscuringOpacityForTouch() { return 0.8f; }

    public boolean isStylusPointerIconEnabled() { return false; }
}
