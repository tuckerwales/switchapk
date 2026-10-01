package android.view.inputmethod;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

/**
 * Channel between an input method and an editor. Methods whose parameter types
 * are not implemented yet (CompletionInfo, ExtractedText, ...) are TODO(WS2).
 */
public interface InputConnection {
    int CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS = 8;
    int CURSOR_UPDATE_FILTER_EDITOR_BOUNDS = 4;
    int CURSOR_UPDATE_FILTER_INSERTION_MARKER = 16;
    int CURSOR_UPDATE_FILTER_TEXT_APPEARANCE = 64;
    int CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS = 32;
    int CURSOR_UPDATE_IMMEDIATE = 1;
    int CURSOR_UPDATE_MONITOR = 2;
    int GET_EXTRACTED_TEXT_MONITOR = 1;
    int GET_TEXT_WITH_STYLES = 1;
    int HANDWRITING_GESTURE_RESULT_CANCELLED = 4;
    int HANDWRITING_GESTURE_RESULT_FAILED = 3;
    int HANDWRITING_GESTURE_RESULT_FALLBACK = 5;
    int HANDWRITING_GESTURE_RESULT_SUCCESS = 1;
    int HANDWRITING_GESTURE_RESULT_UNKNOWN = 0;
    int HANDWRITING_GESTURE_RESULT_UNSUPPORTED = 2;
    int INPUT_CONTENT_GRANT_READ_URI_PERMISSION = 1;

    CharSequence getTextBeforeCursor(int n, int flags);
    CharSequence getTextAfterCursor(int n, int flags);
    CharSequence getSelectedText(int flags);
    int getCursorCapsMode(int reqModes);
    boolean deleteSurroundingText(int beforeLength, int afterLength);
    boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength);
    boolean setComposingText(CharSequence text, int newCursorPosition);
    boolean setComposingRegion(int start, int end);
    boolean finishComposingText();
    boolean commitText(CharSequence text, int newCursorPosition);
    boolean setSelection(int start, int end);
    boolean performEditorAction(int editorAction);
    boolean performContextMenuAction(int id);
    boolean beginBatchEdit();
    boolean endBatchEdit();
    boolean sendKeyEvent(KeyEvent event);
    boolean clearMetaKeyStates(int states);
    boolean reportFullscreenMode(boolean enabled);
    default boolean performSpellCheck() { return false; }
    boolean performPrivateCommand(String action, Bundle data);
    boolean requestCursorUpdates(int cursorUpdateMode);
    default boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) { return false; }
    Handler getHandler();
    void closeConnection();
    default boolean setImeConsumesInput(boolean imeConsumesInput) { return false; }
}
