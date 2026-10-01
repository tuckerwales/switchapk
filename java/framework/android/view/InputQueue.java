package android.view;

/** Input queue handed to NativeActivity (TODO(WS9)). */
public final class InputQueue {
    /** framework-internal (hidden in AOSP). */
    public InputQueue() {}

    public interface Callback {
        void onInputQueueCreated(InputQueue queue);
        void onInputQueueDestroyed(InputQueue queue);
    }
}
