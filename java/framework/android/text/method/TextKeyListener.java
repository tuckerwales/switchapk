package android.text.method;

import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

/** Hardware keys for normal text fields (AOSP TextKeyListener). */
public class TextKeyListener extends BaseKeyListener implements android.text.SpanWatcher {
    public enum Capitalize { NONE, SENTENCES, WORDS, CHARACTERS }

    private static TextKeyListener sInstance;

    private final Capitalize mCap;
    private final boolean mAutoText;

    public TextKeyListener(Capitalize cap, boolean autoText) {
        mCap = cap == null ? Capitalize.NONE : cap;
        mAutoText = autoText;
    }

    public static TextKeyListener getInstance() { return getInstance(false, Capitalize.NONE); }

    public static TextKeyListener getInstance(boolean autoText, Capitalize cap) {
        if (!autoText && cap == Capitalize.NONE) {
            if (sInstance == null) sInstance = new TextKeyListener(Capitalize.NONE, false);
            return sInstance;
        }
        return new TextKeyListener(cap, autoText);
    }

    public static boolean shouldCap(Capitalize cap, CharSequence cs, int off) {
        if (cap == null || cap == Capitalize.NONE || cs == null) return false;
        int mode = 0;
        if (cap == Capitalize.CHARACTERS) mode = TextUtils.CAP_MODE_CHARACTERS;
        else if (cap == Capitalize.WORDS) mode = TextUtils.CAP_MODE_WORDS;
        else if (cap == Capitalize.SENTENCES) mode = TextUtils.CAP_MODE_SENTENCES;
        return (TextUtils.getCapsMode(cs, off, mode) & mode) != 0;
    }

    public int getInputType() {
        int type = InputType.TYPE_CLASS_TEXT;
        if (mCap == Capitalize.CHARACTERS) type |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
        else if (mCap == Capitalize.WORDS) type |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        else if (mCap == Capitalize.SENTENCES) type |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        if (mAutoText) type |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        return type;
    }

    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        return QwertyKeyListener.getInstance(mAutoText, mCap).onKeyDown(view, content, keyCode, event);
    }

    public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
        return super.onKeyUp(view, content, keyCode, event);
    }

    public boolean onKeyOther(View view, Editable content, KeyEvent event) {
        return super.onKeyOther(view, content, event);
    }

    /** No autocorrect buffer yet, so there is nothing to revert. */
    public static void clear(Editable e) {}

    public void onSpanAdded(Spannable text, Object what, int start, int end) {}

    public void onSpanRemoved(Spannable text, Object what, int start, int end) {}

    public void onSpanChanged(Spannable text, Object what, int ostart, int oend, int nstart, int nend) {}

    public void release() {}
}
