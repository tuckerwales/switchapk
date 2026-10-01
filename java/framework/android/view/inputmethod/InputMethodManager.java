package android.view.inputmethod;

import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.TextView;

/**
 * Soft keyboard bridge. {@link #showSoftInput} asks the platform for text.
 * The result is committed into the served editor with {@link InputConnection#commitText}.
 */
public final class InputMethodManager {
    public static final int RESULT_UNCHANGED_SHOWN = 0;
    public static final int RESULT_UNCHANGED_HIDDEN = 1;
    public static final int RESULT_SHOWN = 2;
    public static final int RESULT_HIDDEN = 3;

    private static InputMethodManager sInstance;
    private static int sLastRequestId;
    private static String sLastText;
    private static View sServedView;

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
        if (!(sServedView instanceof TextView) || text == null) return;
        TextView editor = (TextView) sServedView;
        EditorInfo info = new EditorInfo();
        InputConnection ic = editor.onCreateInputConnection(info);
        if (ic == null) return;
        // The platform keyboard (Switch swkbd) edits the whole field: replace it.
        CharSequence current = editor.getText();
        ic.beginBatchEdit();
        ic.setSelection(0, current != null ? current.length() : 0);
        ic.commitText(text, 1);
        ic.endBatchEdit();
    }

    public boolean isActive(View view) { return view != null && view == sServedView; }

    public boolean isActive() { return sServedView != null; }

    public boolean isAcceptingText() {
        return sServedView instanceof TextView && ((TextView) sServedView).onCheckIsTextEditor();
    }

    public boolean showSoftInput(View view, int flags) { return showSoftInput(view, flags, null); }

    public boolean showSoftInput(View view, int flags, ResultReceiver resultReceiver) {
        sServedView = view;
        String initial = "";
        String hint = "";
        int type = 0;
        if (view instanceof TextView) {
            TextView editor = (TextView) view;
            CharSequence text = editor.getText();
            CharSequence hintText = editor.getHint();
            if (text != null) initial = text.toString();
            if (hintText != null) hint = hintText.toString();
            type = editor.getInputType();
        }
        nRequestText(++sLastRequestId, initial, hint, type, 0);
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
