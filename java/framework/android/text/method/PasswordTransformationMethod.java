package android.text.method;

import android.graphics.Rect;
import android.text.Editable;
import android.text.GetChars;
import android.text.TextWatcher;
import android.view.View;

/** Draws password text as dots (AOSP PasswordTransformationMethod). */
public class PasswordTransformationMethod implements TransformationMethod, TextWatcher {
    private static final char DOT = '\u2022';

    private static PasswordTransformationMethod sInstance;

    public PasswordTransformationMethod() {}

    public static PasswordTransformationMethod getInstance() {
        if (sInstance == null) sInstance = new PasswordTransformationMethod();
        return sInstance;
    }

    public CharSequence getTransformation(CharSequence source, View view) {
        return new PasswordCharSequence(source);
    }

    public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect) {}

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    public void afterTextChanged(Editable s) {}

    private static class PasswordCharSequence implements CharSequence, GetChars {
        private final CharSequence mSource;

        PasswordCharSequence(CharSequence source) { mSource = source; }

        public int length() { return mSource.length(); }

        public char charAt(int i) { return DOT; }

        public CharSequence subSequence(int start, int end) {
            char[] buf = new char[end - start];
            getChars(start, end, buf, 0);
            return new String(buf);
        }

        public String toString() { return subSequence(0, length()).toString(); }

        public void getChars(int start, int end, char[] dest, int off) {
            for (int i = start; i < end; i++) dest[off + i - start] = DOT;
        }
    }
}
