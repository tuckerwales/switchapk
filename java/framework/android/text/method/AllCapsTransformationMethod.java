package android.text.method;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.GetChars;
import android.text.TextUtils;
import android.view.View;
import java.util.Locale;

/** Displays the source in upper case (AOSP AllCapsTransformationMethod). */
public class AllCapsTransformationMethod implements TransformationMethod {
    private final Locale mLocale;

    public AllCapsTransformationMethod(Context context) {
        Configuration c = context.getResources().getConfiguration();
        Locale locale = c.getLocales().get(0);
        mLocale = locale != null ? locale : Locale.getDefault();
    }

    public CharSequence getTransformation(CharSequence source, View view) {
        boolean lower = false;
        for (int i = 0; i < source.length(); i++) {
            if (Character.isLowerCase(source.charAt(i))) {
                lower = true;
                break;
            }
        }
        if (!lower) return source;
        return new AllCapsCharSequence(source, mLocale);
    }

    public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect) {}

    private static class AllCapsCharSequence implements CharSequence, GetChars {
        private final CharSequence mSource;
        private final Locale mLocale;
        private String mCachedSource;
        private String mUpper;

        AllCapsCharSequence(CharSequence source, Locale locale) {
            mSource = source;
            mLocale = locale;
        }

        private String upper() {
            String now = mSource.toString();
            if (!now.equals(mCachedSource)) {
                mCachedSource = now;
                mUpper = now.toUpperCase(mLocale);
            }
            return mUpper;
        }

        public int length() { return mSource.length(); }

        public char charAt(int i) { return upper().charAt(i); }

        public CharSequence subSequence(int start, int end) { return upper().substring(start, end); }

        public String toString() { return upper(); }

        public void getChars(int start, int end, char[] dest, int off) {
            TextUtils.getChars(upper(), start, end, dest, off);
        }
    }
}
