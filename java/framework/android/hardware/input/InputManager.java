package android.hardware.input;

import android.os.Handler;
import android.view.InputDevice;
import java.util.ArrayList;

/**
 * The console's input devices: the touch screen, the controllers as one gamepad, and the virtual
 * keyboard. They are fixed for the life of the app, so listeners are kept but never called.
 * TODO(WS15): report controllers attaching and detaching once the platform layer tells us.
 */
public final class InputManager {
    public static final String ACTION_QUERY_KEYBOARD_LAYOUTS = "android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS";
    public static final String META_DATA_KEYBOARD_LAYOUTS = "android.hardware.input.metadata.KEYBOARD_LAYOUTS";

    private static final InputManager sInstance = new InputManager();
    private final ArrayList<InputDeviceListener> mListeners = new ArrayList<InputDeviceListener>();

    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);

        void onInputDeviceRemoved(int deviceId);

        void onInputDeviceChanged(int deviceId);
    }

    InputManager() {
    }

    /** @hide */
    public static InputManager getInstance() {
        return sInstance;
    }

    public InputDevice getInputDevice(int id) {
        return InputDevice.getDevice(id);
    }

    public int[] getInputDeviceIds() {
        return InputDevice.getDeviceIds();
    }

    public void registerInputDeviceListener(InputDeviceListener listener, Handler handler) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void unregisterInputDeviceListener(InputDeviceListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    public float getMaximumObscuringOpacityForTouch() {
        return 0.8f;
    }

    public boolean isStylusPointerIconEnabled() {
        return false;
    }
}
