package android.view.inputmethod;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Soft keyboard bridge. {@link #showSoftInput} asks the platform for text.
 * The result is committed into the served editor with {@link InputConnection#commitText}.
 * Editors that are not TextViews (Jetpack Compose text fields, for example) are
 * reached through {@link View#onCreateInputConnection} on the served view.
 */
public final class InputMethodManager {
    public static final int HANDWRITING_DELEGATE_FLAG_HOME_DELEGATOR_ALLOWED = 1;
    public static final int HIDE_IMPLICIT_ONLY = 1;
    public static final int HIDE_NOT_ALWAYS = 2;
    public static final int SHOW_FORCED = 2;
    public static final int SHOW_IMPLICIT = 1;
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
        if (sServedView == null || text == null) return;
        InputConnection ic = sServedView.onCreateInputConnection(new EditorInfo());
        if (ic == null) return;
        // The platform keyboard (Switch swkbd) edits the whole field: replace it.
        CharSequence current = sServedView instanceof TextView
                ? ((TextView) sServedView).getText() : currentText(ic);
        ic.beginBatchEdit();
        ic.setSelection(0, current != null ? current.length() : 0);
        ic.commitText(text, 1);
        ic.endBatchEdit();
    }

    /** The whole text of an editor known only through its InputConnection. */
    private static CharSequence currentText(InputConnection ic) {
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null) return et.text;
        CharSequence before = ic.getTextBeforeCursor(Integer.MAX_VALUE / 2, 0);
        CharSequence selected = ic.getSelectedText(0);
        CharSequence after = ic.getTextAfterCursor(Integer.MAX_VALUE / 2, 0);
        StringBuilder b = new StringBuilder();
        if (before != null) b.append(before);
        if (selected != null) b.append(selected);
        if (after != null) b.append(after);
        return b;
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
        } else if (view != null && view.onCheckIsTextEditor()) {
            EditorInfo info = new EditorInfo();
            InputConnection ic = view.onCreateInputConnection(info);
            if (ic != null) {
                initial = currentText(ic).toString();
                ic.closeConnection();
            }
            if (info.hintText != null) hint = info.hintText.toString();
            type = info.inputType;
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
    public void toggleSoftInput(int showFlags, int hideFlags) {}

    // The platform keyboard is modal and takes the whole text, so it needs none of
    // the editor state updates below.
    public void restartInput(View view) {}
    public void invalidateInput(View view) {}
    public void updateSelection(View view, int selStart, int selEnd, int candidatesStart, int candidatesEnd) {}
    public void updateCursorAnchorInfo(View view, CursorAnchorInfo cursorAnchorInfo) {}
    public void updateExtractedText(View view, int token, ExtractedText text) {}
    public void updateCursor(View view, int left, int top, int right, int bottom) {}
    public void viewClicked(View view) {}
    public void displayCompletions(View view, CompletionInfo[] completions) {}
    public void sendAppPrivateCommand(View view, String action, Bundle data) {}
    public void dispatchKeyEventFromInputMethod(View targetView, KeyEvent event) {
        if (targetView != null && event != null) targetView.dispatchKeyEvent(event);
    }
    public boolean isFullscreenMode() { return false; }
    public boolean isWatchingCursor(View view) { return false; }
    public boolean isStylusHandwritingAvailable() { return false; }
    public boolean isInputMethodSuppressingSpellChecker() { return false; }
    public void startStylusHandwriting(View view) {}
    public void showInputMethodPicker() {}

    private static native void nRequestText(int id, String initial, String hint, int inputType, int maxLen);
}
