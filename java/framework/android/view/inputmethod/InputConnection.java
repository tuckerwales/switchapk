package android.view.inputmethod;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

/**
 * Channel between an input method and an editor. The handwriting gesture and
 * text bounds methods are omitted (their parameter types do not exist here).
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

    default SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        CharSequence textBeforeCursor = getTextBeforeCursor(beforeLength, flags);
        if (textBeforeCursor == null) return null;
        CharSequence textAfterCursor = getTextAfterCursor(afterLength, flags);
        if (textAfterCursor == null) return null;
        CharSequence selectedText = getSelectedText(flags);
        if (selectedText == null) selectedText = "";
        CharSequence surroundingText = android.text.TextUtils.concat(textBeforeCursor, selectedText, textAfterCursor);
        return new SurroundingText(surroundingText, textBeforeCursor.length(),
                textBeforeCursor.length() + selectedText.length(), -1);
    }

    int getCursorCapsMode(int reqModes);
    ExtractedText getExtractedText(ExtractedTextRequest request, int flags);
    boolean deleteSurroundingText(int beforeLength, int afterLength);
    boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength);
    boolean setComposingText(CharSequence text, int newCursorPosition);
    default boolean setComposingText(CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        return setComposingText(text, newCursorPosition);
    }
    boolean setComposingRegion(int start, int end);
    default boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
        return setComposingRegion(start, end);
    }
    boolean finishComposingText();
    boolean commitText(CharSequence text, int newCursorPosition);
    default boolean commitText(CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        return commitText(text, newCursorPosition);
    }
    boolean commitCompletion(CompletionInfo text);
    boolean commitCorrection(CorrectionInfo correctionInfo);
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
    boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts);
    default boolean setImeConsumesInput(boolean imeConsumesInput) { return false; }
    default TextSnapshot takeSnapshot() { return null; }
    default boolean replaceText(int start, int end, CharSequence text, int newCursorPosition,
            TextAttribute textAttribute) {
        beginBatchEdit();
        finishComposingText();
        setSelection(start, end);
        commitText(text, newCursorPosition, textAttribute);
        endBatchEdit();
        return true;
    }
}
