package android.text.method;

import android.graphics.Rect;
import android.text.GetChars;
import android.text.TextUtils;
import android.view.View;

/**
 * Replaces individual characters (newlines, for example) while leaving every
 * other character in place. Text with none of the original characters is
 * returned unchanged, so spans survive.
 */
public abstract class ReplacementTransformationMethod implements TransformationMethod {
    protected abstract char[] getOriginal();

    protected abstract char[] getReplacement();

    public CharSequence getTransformation(CharSequence source, View v) {
        char[] original = getOriginal();
        int n = source.length();
        boolean found = false;
        for (int i = 0; i < n && !found; i++) {
            char c = source.charAt(i);
            for (int j = 0; j < original.length; j++) {
                if (c == original[j]) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) return source;
        return new ReplacementCharSequence(source, original, getReplacement());
    }

    public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect) {}

    private static class ReplacementCharSequence implements CharSequence, GetChars {
        private final CharSequence mSource;
        private final char[] mOriginal;
        private final char[] mReplacement;

        ReplacementCharSequence(CharSequence source, char[] original, char[] replacement) {
            mSource = source;
            mOriginal = original;
            mReplacement = replacement;
        }

        public int length() { return mSource.length(); }

        public char charAt(int i) {
            char c = mSource.charAt(i);
            for (int j = 0; j < mOriginal.length; j++) {
                if (c == mOriginal[j]) return mReplacement[j];
            }
            return c;
        }

        public CharSequence subSequence(int start, int end) {
            char[] buf = new char[end - start];
            getChars(start, end, buf, 0);
            return new String(buf);
        }

        public String toString() {
            char[] buf = new char[length()];
            getChars(0, length(), buf, 0);
            return new String(buf);
        }

        public void getChars(int start, int end, char[] dest, int off) {
            TextUtils.getChars(mSource, start, end, dest, off);
            int n = end - start;
            for (int i = 0; i < n; i++) {
                char c = dest[off + i];
                for (int j = 0; j < mOriginal.length; j++) {
                    if (c == mOriginal[j]) {
                        dest[off + i] = mReplacement[j];
                        break;
                    }
                }
            }
        }
    }
}
