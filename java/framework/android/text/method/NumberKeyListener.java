package android.text.method;

import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.View;

/** Accepts only a set of characters (AOSP NumberKeyListener). */
public abstract class NumberKeyListener extends BaseKeyListener implements InputFilter {
    public NumberKeyListener() {}

    protected abstract char[] getAcceptedChars();

    protected int lookup(KeyEvent event, Spannable content) {
        int u = event.getUnicodeChar(getMetaState(content, event));
        if (u == 0) return 0;
        return ok(getAcceptedChars(), (char) u) ? u : 0;
    }

    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        char[] accept = getAcceptedChars();
        StringBuilder out = null;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (!ok(accept, c)) {
                if (out == null) {
                    out = new StringBuilder();
                    out.append(source, start, i);
                }
            } else if (out != null) {
                out.append(c);
            }
        }
        if (out == null) return null;
        if (out.length() == 0) return "";
        return out;
    }

    protected static boolean ok(char[] accept, char c) {
        for (int i = 0; i < accept.length; i++) if (accept[i] == c) return true;
        return false;
    }

    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        int u = lookup(event, content);
        if (u != 0) {
            int start = Selection.getSelectionStart(content);
            int end = Selection.getSelectionEnd(content);
            if (start < 0 || end < 0) start = end = content.length();
            content.replace(Math.min(start, end), Math.max(start, end), String.valueOf((char) u));
            adjustMetaAfterKeypress(content);
            return true;
        }
        return super.onKeyDown(view, content, keyCode, event);
    }
}
