package android.text.method;

import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

/** Inserts the character for a key (AOSP QwertyKeyListener, no autocorrect). */
public class QwertyKeyListener extends BaseKeyListener {
    private final TextKeyListener.Capitalize mCap;
    private final boolean mAutoText;

    public QwertyKeyListener(TextKeyListener.Capitalize cap, boolean autoText) {
        mCap = cap == null ? TextKeyListener.Capitalize.NONE : cap;
        mAutoText = autoText;
    }

    public static QwertyKeyListener getInstance(boolean autoText, TextKeyListener.Capitalize cap) {
        return new QwertyKeyListener(cap, autoText);
    }

    public static QwertyKeyListener getInstanceForFullKeyboard() {
        return getInstance(false, TextKeyListener.Capitalize.NONE);
    }

    public int getInputType() {
        return new TextKeyListener(mCap, mAutoText).getInputType();
    }

    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        if (super.onKeyDown(view, content, keyCode, event)) return true;
        int u = event.getUnicodeChar(getMetaState(content, event));
        if (u == 0 || (u & KeyCharacterMap.COMBINING_ACCENT) != 0) return false;
        char c = (char) u;
        int start = Selection.getSelectionStart(content);
        int end = Selection.getSelectionEnd(content);
        if (start < 0 || end < 0) {
            start = end = content.length();
        }
        int off = Math.min(start, end);
        if (TextKeyListener.shouldCap(mCap, content, off) && Character.isLowerCase(c)) {
            c = Character.toUpperCase(c);
        }
        content.replace(off, Math.max(start, end), String.valueOf(c));
        adjustMetaAfterKeypress(content);
        return true;
    }

    /** Autocorrect undo is not implemented. */
    public static void markAsReplaced(Spannable text, int start, int end, String original) {}
}
