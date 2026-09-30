package android.view.inputmethod;

import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;

/** Soft keyboard bridge. Editing a focused text view is TODO(WS2). */
public final class InputMethodManager {
    public static final int RESULT_UNCHANGED_SHOWN = 0;
    public static final int RESULT_UNCHANGED_HIDDEN = 1;
    public static final int RESULT_SHOWN = 2;
    public static final int RESULT_HIDDEN = 3;

    private static InputMethodManager sInstance;
    private static int sLastRequestId;
    private static String sLastText;

    private InputMethodManager() {}

    /** framework-internal. There is no public getInstance on this class. */
    public static InputMethodManager systemInstance() {
        if (sInstance == null) sInstance = new InputMethodManager();
        return sInstance;
    }

    /** framework-internal. Called when the platform posts the text result. */
    public static void deliverTextResult(int requestId, String text) {
        sLastRequestId = requestId;
        sLastText = text;
    }

    public boolean isActive(View view) { return false; }
    public boolean isActive() { return false; }
    public boolean isAcceptingText() { return false; }

    public boolean showSoftInput(View view, int flags) { return showSoftInput(view, flags, null); }

    public boolean showSoftInput(View view, int flags, ResultReceiver resultReceiver) {
        nRequestText(++sLastRequestId, "", "", 0, 0);
        return true;
    }

    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) { return false; }

    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags, ResultReceiver resultReceiver) {
        return false;
    }

    public void toggleSoftInputFromWindow(IBinder windowToken, int showFlags, int hideFlags) {}
    public void hideSoftInputFromInputMethod(IBinder token, int flags) {}
    public void showSoftInputFromInputMethod(IBinder token, int flags) {}

    private static native void nRequestText(int id, String initial, String hint, int inputType, int maxLen);
}
