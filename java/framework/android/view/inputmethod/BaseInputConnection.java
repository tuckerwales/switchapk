package android.view.inputmethod;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.NoCopySpan;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/**
 * Default {@link InputConnection} that edits an {@link Editable} (AOSP
 * BaseInputConnection). Methods that need types this tree does not have yet
 * (ExtractedText, SurroundingText, CompletionInfo) are left unimplemented.
 */
public class BaseInputConnection implements InputConnection {
    private static final Object COMPOSING = new Composing();

    private static final class Composing implements NoCopySpan {}

    private final View mTargetView;
    private final boolean mDummyMode;
    private Editable mEditable;

    public BaseInputConnection(View targetView, boolean fullEditor) {
        mTargetView = targetView;
        mDummyMode = !fullEditor;
    }

    public static final void removeComposingSpans(Spannable text) {
        text.removeSpan(COMPOSING);
        Object[] spans = text.getSpans(0, text.length(), Object.class);
        for (int i = 0; i < spans.length; i++) {
            if ((text.getSpanFlags(spans[i]) & Spanned.SPAN_COMPOSING) != 0) text.removeSpan(spans[i]);
        }
    }

    public static void setComposingSpans(Spannable text) {
        Object[] spans = text.getSpans(0, text.length(), Object.class);
        for (int i = 0; i < spans.length; i++) {
            if (spans[i] == COMPOSING) continue;
            int flags = text.getSpanFlags(spans[i]);
            if ((flags & Spanned.SPAN_COMPOSING) == 0) {
                text.setSpan(spans[i], text.getSpanStart(spans[i]), text.getSpanEnd(spans[i]),
                        flags | Spanned.SPAN_COMPOSING);
            }
        }
    }

    public static int getComposingSpanStart(Spannable text) { return text.getSpanStart(COMPOSING); }

    public static int getComposingSpanEnd(Spannable text) { return text.getSpanEnd(COMPOSING); }

    /** The buffer being edited. Full editors override this. */
    public Editable getEditable() {
        if (mDummyMode) {
            if (mEditable == null) mEditable = Editable.Factory.getInstance().newEditable("");
            return mEditable;
        }
        return null;
    }

    public boolean beginBatchEdit() { return false; }

    public boolean endBatchEdit() { return false; }

    public void closeConnection() { finishComposingText(); }

    public boolean clearMetaKeyStates(int states) {
        Editable content = getEditable();
        if (content == null) return false;
        MetaKeyKeyListener.clearMetaKeyState(content, states);
        return true;
    }

    public boolean commitText(CharSequence text, int newCursorPosition) {
        replaceText(text, newCursorPosition, false);
        return true;
    }

    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        replaceText(text, newCursorPosition, true);
        return true;
    }

    public boolean setComposingRegion(int start, int end) {
        Editable content = getEditable();
        if (content == null) return false;
        int len = content.length();
        if (start < 0) start = 0;
        if (end < 0) end = 0;
        if (start > len) start = len;
        if (end > len) end = len;
        if (end < start) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        content.removeSpan(COMPOSING);
        if (start != end) {
            content.setSpan(COMPOSING, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
        }
        return true;
    }

    public boolean finishComposingText() {
        Editable content = getEditable();
        if (content != null) removeComposingSpans(content);
        return true;
    }

    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        Editable content = getEditable();
        if (content == null) return false;
        if (beforeLength < 0) beforeLength = 0;
        if (afterLength < 0) afterLength = 0;
        int start = Selection.getSelectionStart(content);
        int end = Selection.getSelectionEnd(content);
        if (start < 0 || end < 0) return false;
        int a = Math.min(start, end);
        int b = Math.max(start, end);
        int cs = getComposingSpanStart(content);
        int ce = getComposingSpanEnd(content);
        if (cs >= 0 && ce >= 0) {
            a = Math.min(cs, ce);
            b = Math.max(cs, ce);
        }
        int delStart = Math.max(0, a - beforeLength);
        int delEnd = Math.min(content.length(), b + afterLength);
        if (b < delEnd) content.delete(b, delEnd);
        if (delStart < a) content.delete(delStart, a);
        return true;
    }

    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return deleteSurroundingText(beforeLength, afterLength);
    }

    public int getCursorCapsMode(int reqModes) {
        Editable content = getEditable();
        if (content == null) return 0;
        int end = Selection.getSelectionEnd(content);
        if (end < 0) end = 0;
        return TextUtils.getCapsMode(content, end, reqModes);
    }

    public CharSequence getTextBeforeCursor(int n, int flags) {
        Editable content = getEditable();
        if (content == null || n <= 0) return "";
        int a = selMin(content);
        int start = Math.max(0, a - n);
        return copy(content, start, a, flags);
    }

    public CharSequence getTextAfterCursor(int n, int flags) {
        Editable content = getEditable();
        if (content == null || n <= 0) return "";
        int b = selMax(content);
        int end = Math.min(content.length(), b + n);
        return copy(content, b, end, flags);
    }

    public CharSequence getSelectedText(int flags) {
        Editable content = getEditable();
        if (content == null) return null;
        int a = selMin(content);
        int b = selMax(content);
        if (a == b) return null;
        return copy(content, a, b, flags);
    }

    public boolean performEditorAction(int editorAction) {
        if (mTargetView instanceof TextView) {
            ((TextView) mTargetView).onEditorAction(editorAction);
            return true;
        }
        return false;
    }

    public boolean performContextMenuAction(int id) {
        if (mTargetView instanceof TextView) return ((TextView) mTargetView).onTextContextMenuItem(id);
        return false;
    }

    public boolean performPrivateCommand(String action, Bundle data) { return false; }

    public boolean requestCursorUpdates(int cursorUpdateMode) { return false; }

    public Handler getHandler() { return null; }

    public boolean setSelection(int start, int end) {
        Editable content = getEditable();
        if (content == null) return false;
        int len = content.length();
        if (start < 0 || end < 0 || start > len || end > len) return false;
        Selection.setSelection(content, start, end);
        return true;
    }

    public boolean sendKeyEvent(KeyEvent event) {
        if (mTargetView == null || event == null) return false;
        return mTargetView.dispatchKeyEvent(event);
    }

    public boolean reportFullscreenMode(boolean enabled) { return true; }

    private void replaceText(CharSequence text, int newCursorPosition, boolean composing) {
        Editable content = getEditable();
        if (content == null) return;
        if (text == null) text = "";
        beginBatchEdit();
        int a = getComposingSpanStart(content);
        int b = getComposingSpanEnd(content);
        if (a < 0 || b < 0) {
            a = Selection.getSelectionStart(content);
            b = Selection.getSelectionEnd(content);
        }
        if (a < 0) a = content.length();
        if (b < 0) b = content.length();
        if (b < a) {
            int tmp = a;
            a = b;
            b = tmp;
        }
        int oldLen = content.length();
        CharSequence insert = text;
        if (composing && text instanceof Spannable) {
            Spannable sp = new SpannableStringBuilder(text);
            setComposingSpans(sp);
            insert = sp;
        }
        content.replace(a, b, insert);
        int inserted = content.length() - (oldLen - (b - a));
        if (inserted < 0) inserted = 0;
        if (composing) {
            removeComposingSpans(content);
            if (inserted > 0) {
                int end = Math.min(content.length(), a + inserted);
                content.setSpan(COMPOSING, a, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
            }
        } else {
            removeComposingSpans(content);
        }
        int cursor = newCursorPosition > 0 ? a + inserted + newCursorPosition - 1 : a + newCursorPosition;
        if (cursor < 0) cursor = 0;
        if (cursor > content.length()) cursor = content.length();
        Selection.setSelection(content, cursor);
        endBatchEdit();
    }

    private static int selMin(Editable content) {
        int start = Selection.getSelectionStart(content);
        int end = Selection.getSelectionEnd(content);
        if (start < 0) start = 0;
        if (end < 0) end = 0;
        return Math.min(start, end);
    }

    private static int selMax(Editable content) {
        int start = Selection.getSelectionStart(content);
        int end = Selection.getSelectionEnd(content);
        if (start < 0) start = 0;
        if (end < 0) end = 0;
        return Math.max(start, end);
    }

    private static CharSequence copy(Editable content, int start, int end, int flags) {
        if (end < start) end = start;
        if ((flags & GET_TEXT_WITH_STYLES) != 0) return content.subSequence(start, end);
        return content.subSequence(start, end).toString();
    }
}
