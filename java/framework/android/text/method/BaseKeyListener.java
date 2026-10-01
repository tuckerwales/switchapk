package android.text.method;

import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

/** Deletes and inserts characters (AOSP BaseKeyListener). */
public abstract class BaseKeyListener extends MetaKeyKeyListener implements KeyListener {
    public BaseKeyListener() {}

    public boolean backspace(View view, Editable content, int keyCode, KeyEvent event) {
        return delete(content, true);
    }

    public boolean forwardDelete(View view, Editable content, int keyCode, KeyEvent event) {
        return delete(content, false);
    }

    private static boolean delete(Editable content, boolean before) {
        int start = Selection.getSelectionStart(content);
        int end = Selection.getSelectionEnd(content);
        if (start < 0 || end < 0) return false;
        int a = Math.min(start, end);
        int b = Math.max(start, end);
        if (a != b) {
            content.delete(a, b);
            return true;
        }
        if (before) {
            if (a == 0) return false;
            content.delete(TextUtils.getOffsetBefore(content, a), a);
        } else {
            if (a == content.length()) return false;
            content.delete(a, TextUtils.getOffsetAfter(content, a));
        }
        return true;
    }

    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL) return backspace(view, content, keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) return forwardDelete(view, content, keyCode, event);
        return super.onKeyDown(view, content, keyCode, event);
    }

    public boolean onKeyOther(View view, Editable content, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_MULTIPLE || event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN) {
            return false;
        }
        String chars = event.getCharacters();
        if (chars == null || chars.length() == 0) return false;
        int start = Selection.getSelectionStart(content);
        int end = Selection.getSelectionEnd(content);
        if (start < 0 || end < 0) {
            start = end = content.length();
        }
        content.replace(Math.min(start, end), Math.max(start, end), chars);
        return true;
    }
}
